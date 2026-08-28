package com.example.data.repository

import android.util.Log
import android.util.Xml
import com.example.data.model.ConfidenceLevel
import com.example.data.model.PlayerEntity
import com.example.data.model.RiskLevel
import com.example.data.model.Role
import com.example.data.remote.KaggleHistoricalStatsService
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed class ListoneImportResult {
    data class Success(val players: List<PlayerEntity>, val warningCount: Int, val sourceFormat: String) : ListoneImportResult()
    data class Error(val message: String, val rowNumber: Int? = null) : ListoneImportResult()
}

/**
 * Universal importer for Fantacalcio Listone supporting:
 * - Excel workbooks (.xlsx - Office Open XML format)
 * - CSV files (.csv - Semicolon, Comma, Tab, Pipe delimited)
 * - Pasted tabular text directly copied from Excel / Google Sheets
 * - Automatic character encoding detection & accented letter repair
 * - Dynamic column detection (R, Nome, Squadra, Qt, FVM, Titolarità)
 * - Automatic enrichment with FBref/Kaggle historical metrics on every import
 */
object ListoneImporter {
    private const val TAG = "ListoneImporter"

    fun parseStream(inputStream: InputStream): ListoneImportResult {
        return try {
            val bytes = inputStream.readBytes()
            if (bytes.isEmpty()) {
                return ListoneImportResult.Error("Il file selezionato è vuoto.")
            }

            // Check if file is ZIP/XLSX (Magic bytes: 'PK\x03\x04')
            val isZipOrXlsx = bytes.size >= 4 &&
                    bytes[0] == 0x50.toByte() &&
                    bytes[1] == 0x4B.toByte() &&
                    bytes[2] == 0x03.toByte() &&
                    bytes[3] == 0x04.toByte()

            val rows: List<List<String>>
            val sourceFormat: String

            if (isZipOrXlsx) {
                rows = parseXlsxBytes(bytes)
                sourceFormat = "Excel (.xlsx)"
            } else {
                val text = decodeAndCleanString(bytes)
                if (text.isBlank()) {
                    return ListoneImportResult.Error("Impossibile leggere il contenuto del file.")
                }
                val delimiter = detectDelimiter(text)
                rows = parseCsvRows(text, delimiter)
                sourceFormat = if (delimiter == '\t') "Tabulato Excel" else "CSV"
            }

            if (rows.isEmpty()) {
                return ListoneImportResult.Error("Nessuna riga valida trovata nel file.")
            }

            processRowsToPlayers(rows, sourceFormat)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing listone", e)
            ListoneImportResult.Error("Errore durante l'elaborazione del listone: ${e.localizedMessage ?: e.message}")
        }
    }

    fun parseText(text: String): ListoneImportResult {
        if (text.isBlank()) {
            return ListoneImportResult.Error("Il testo inserito è vuoto.")
        }
        val delimiter = detectDelimiter(text)
        val rows = parseCsvRows(text, delimiter)
        if (rows.isEmpty()) {
            return ListoneImportResult.Error("Nessuna riga valida trovata nel testo inserito.")
        }
        val format = if (delimiter == '\t') "Copia-Incolla Excel" else "Testo CSV"
        return processRowsToPlayers(rows, format)
    }

    /**
     * Parses an OpenXML (.xlsx) file using standard Android ZipInputStream and XmlPullParser.
     */
    private fun parseXlsxBytes(bytes: ByteArray): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheetBytesList = mutableListOf<ByteArray>()

        // 1. Read ZIP entries
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase(Locale.ROOT)
                if (name.contains("sharedstrings.xml")) {
                    val ssBytes = zis.readBytes()
                    sharedStrings.addAll(parseSharedStrings(ssBytes))
                } else if (name.contains("sheet") && name.endsWith(".xml") && name.contains("worksheets")) {
                    sheetBytesList.add(zis.readBytes())
                }
                entry = zis.nextEntry
            }
        }

        if (sheetBytesList.isEmpty()) {
            return emptyList()
        }

        // 2. Parse the first worksheet
        return parseSheetXml(sheetBytesList.first(), sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            var inSi = false
            var currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("si", ignoreCase = true)) {
                            inSi = true
                            currentText = StringBuilder()
                        } else if (inSi && tagName.equals("t", ignoreCase = true)) {
                            val text = parser.nextText()
                            currentText.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("si", ignoreCase = true)) {
                            inSi = false
                            list.add(currentText.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sharedStrings.xml", e)
        }
        return list
    }

    private fun parseSheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            var inRow = false
            var inCell = false
            var cellType = ""
            var cellValue = StringBuilder()
            val currentRowCells = mutableMapOf<Int, String>()
            var maxColIndex = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("row", ignoreCase = true)) {
                            inRow = true
                            currentRowCells.clear()
                            maxColIndex = 0
                        } else if (inRow && tagName.equals("c", ignoreCase = true)) {
                            inCell = true
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            val cellRef = parser.getAttributeValue(null, "r") ?: ""
                            val colIdx = columnRefToIndex(cellRef)
                            if (colIdx >= 0) {
                                maxColIndex = maxOf(maxColIndex, colIdx)
                            }
                            cellValue = StringBuilder()
                        } else if (inCell && (tagName.equals("v", ignoreCase = true) || tagName.equals("t", ignoreCase = true))) {
                            val text = parser.nextText()
                            cellValue.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        if (tagName.equals("c", ignoreCase = true)) {
                            inCell = false
                            val rawVal = cellValue.toString().trim()
                            val resolvedVal = when (cellType) {
                                "s" -> {
                                    val index = rawVal.toIntOrNull()
                                    if (index != null && index in sharedStrings.indices) sharedStrings[index] else rawVal
                                }
                                "inlineStr", "str" -> rawVal
                                else -> rawVal
                            }
                            val lastCol = if (currentRowCells.isEmpty()) 0 else currentRowCells.keys.maxOrNull() ?: 0
                            val targetIndex = if (maxColIndex > 0) maxColIndex else lastCol + 1
                            currentRowCells[targetIndex] = resolvedVal
                        } else if (tagName.equals("row", ignoreCase = true)) {
                            inRow = false
                            if (currentRowCells.isNotEmpty()) {
                                val highestCol = currentRowCells.keys.maxOrNull() ?: 0
                                val rowList = mutableListOf<String>()
                                for (c in 0..highestCol) {
                                    rowList.add(currentRowCells[c] ?: "")
                                }
                                if (rowList.any { it.isNotBlank() }) {
                                    rows.add(rowList)
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sheet XML", e)
        }
        return rows
    }

    private fun columnRefToIndex(cellRef: String): Int {
        if (cellRef.isBlank()) return -1
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase(Locale.ROOT)
        if (letters.isEmpty()) return -1
        var result = 0
        for (ch in letters) {
            result = result * 26 + (ch - 'A' + 1)
        }
        return result - 1 // 0-indexed (A -> 0, B -> 1, ...)
    }

    private fun processRowsToPlayers(rows: List<List<String>>, sourceFormat: String): ListoneImportResult {
        // Find header row in first 10 non-empty rows
        val (headerIndex, columnIndexes) = findHeaderAndColumns(rows)

        val (roleIdx, nameIdx, teamIdx, quotationIdx, fvmIdx, starterIdx, fmIdx) = if (headerIndex != -1) {
            columnIndexes
        } else {
            inferColumnsFromData(rows)
        }

        if (nameIdx == -1 && roleIdx == -1) {
            return ListoneImportResult.Error("Intestazione non riconosciuta: assicurati che siano presenti colonne per Ruolo (R) e Nome (o Calciatore).")
        }

        val startRow = if (headerIndex != -1) headerIndex + 1 else 0
        val parsedPlayers = mutableListOf<PlayerEntity>()
        var warnings = 0

        for (i in startRow until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            val rawName = if (nameIdx != -1) row.getOrNull(nameIdx)?.trim()?.replace("\"", "") ?: "" else ""
            val name = sanitizeAndFixPlayerName(rawName)
            if (name.isBlank() || isHeaderLike(name)) continue

            val rawRole = if (roleIdx != -1) row.getOrNull(roleIdx)?.trim()?.replace("\"", "") ?: "C" else "C"
            val role = Role.fromString(rawRole)

            val rawTeam = if (teamIdx != -1) row.getOrNull(teamIdx)?.trim()?.replace("\"", "") ?: "Serie A" else "Serie A"
            val team = sanitizeTeamName(rawTeam)

            val rawQt = if (quotationIdx != -1) row.getOrNull(quotationIdx) else null
            val quotation = parseNumericValue(rawQt) ?: 1

            val rawFvm = if (fvmIdx != -1) row.getOrNull(fvmIdx) else null
            val fvm = parseNumericValue(rawFvm) ?: (quotation * 2).coerceAtLeast(1)

            val rawStarter = if (starterIdx != -1) row.getOrNull(starterIdx) else null
            val starterProb = parseNumericValue(rawStarter)?.coerceIn(10, 99) ?: estimateStarterFromFvm(fvm, role)

            val rawFm = if (fmIdx != -1) row.getOrNull(fmIdx) else null
            val parsedFm = parseDecimalValue(rawFm)
            val expPts = calculateExpectedPoints(fvm, role, starterProb, parsedFm)
            val risk = if (starterProb < 65) RiskLevel.ALTO else if (starterProb < 82) RiskLevel.MEDIO else RiskLevel.BASSO

            val safeId = "list_${name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")}_${UUID.randomUUID().toString().take(4)}"

            val player = PlayerEntity(
                id = safeId,
                name = name,
                team = team,
                role = role,
                mantraRole = "",
                quotation = quotation,
                fvm = fvm,
                starterProb2026_27 = starterProb,
                expectedFantasyPoints = expPts,
                expectedMinutes = (starterProb * 0.9).toInt().coerceIn(20, 90),
                riskLevel = risk,
                confidenceLevel = if (fvm >= 50) ConfidenceLevel.ALTA else if (fvm >= 15) ConfidenceLevel.MEDIA else ConfidenceLevel.BASSA,
                isPenaltyTaker = false,
                penaltyOrder = 0,
                isFreeKickTaker = false,
                isCornerTaker = false,
                ballottaggioRival = null,
                ballottaggioShare = 100,
                stats2023_24Json = "",
                stats2024_25Json = "",
                stats2025_26Json = "",
                status = "Disponibile",
                injuryNotes = "",
                expectedReturnDate = ""
            )
            parsedPlayers.add(player)
        }

        if (parsedPlayers.isEmpty()) {
            return ListoneImportResult.Error("Nessun giocatore valido trovato nelle righe del file.")
        }

        // Automatically enrich all players with real FBref/Kaggle historical statistics
        val enrichedPlayers = KaggleHistoricalStatsService.enrichList(parsedPlayers)
        return ListoneImportResult.Success(enrichedPlayers, warnings, sourceFormat)
    }

    private fun decodeAndCleanString(bytes: ByteArray): String {
        val effectiveBytes = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }

        val decoded = try {
            val utf8 = String(effectiveBytes, StandardCharsets.UTF_8)
            if (utf8.contains("\uFFFD")) {
                try {
                    val win1252 = String(effectiveBytes, Charset.forName("windows-1252"))
                    if (!win1252.contains("\uFFFD")) win1252 else utf8
                } catch (e: Exception) {
                    try {
                        String(effectiveBytes, StandardCharsets.ISO_8859_1)
                    } catch (e2: Exception) {
                        utf8
                    }
                }
            } else {
                utf8
            }
        } catch (e: Exception) {
            String(effectiveBytes, StandardCharsets.ISO_8859_1)
        }

        return fixCommonEncodingCorruption(decoded)
    }

    private fun fixCommonEncodingCorruption(text: String): String {
        return text
            .replace("Ã¨", "è")
            .replace("Ã©", "é")
            .replace("Ã²", "ò")
            .replace("Ã*", "à")
            .replace("Ã¹", "ù")
            .replace("Ã¬", "ì")
            .replace("Ã§", "ç")
            .replace("Ã±", "ñ")
            .replace("Ã¶", "ö")
            .replace("Ã¼", "ü")
            .replace("Ã¤", "ä")
            .replace("â€™", "'")
            .replace("â€œ", "\"")
            .replace("â€", "\"")
            .replace("â€“", "-")
    }

    private fun detectDelimiter(text: String): Char {
        val sampleLines = text.lineSequence().filter { it.isNotBlank() }.take(15).toList()
        if (sampleLines.isEmpty()) return ';'

        var tabCount = 0
        var semiCount = 0
        var commaCount = 0
        var pipeCount = 0

        for (line in sampleLines) {
            tabCount += line.count { it == '\t' }
            semiCount += line.count { it == ';' }
            commaCount += line.count { it == ',' }
            pipeCount += line.count { it == '|' }
        }

        return when {
            tabCount > semiCount && tabCount > commaCount -> '\t'
            semiCount >= commaCount && semiCount >= pipeCount -> ';'
            commaCount > semiCount && commaCount >= pipeCount -> ','
            pipeCount > 0 -> '|'
            else -> ';'
        }
    }

    private fun parseCsvRows(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val c = content[i]
            when {
                c == '\"' -> {
                    if (inQuotes && i + 1 < content.length && content[i + 1] == '\"') {
                        currentField.append('\"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(currentField.toString().trim())
                    currentField.clear()
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < content.length && content[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentField.toString().trim())
                    currentField.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }

        return rows
    }

    private data class ColumnIndices(
        val roleIdx: Int = -1,
        val nameIdx: Int = -1,
        val teamIdx: Int = -1,
        val quotationIdx: Int = -1,
        val fvmIdx: Int = -1,
        val starterIdx: Int = -1,
        val fmIdx: Int = -1
    )

    private fun findHeaderAndColumns(rows: List<List<String>>): Pair<Int, ColumnIndices> {
        val maxSearchRows = minOf(15, rows.size)
        for (i in 0 until maxSearchRows) {
            val row = rows[i]
            val normRow = row.map { normalizeHeader(it) }

            var roleIdx = -1
            var nameIdx = -1
            var teamIdx = -1
            var quotationIdx = -1
            var fvmIdx = -1
            var starterIdx = -1
            var fmIdx = -1

            for (colIdx in normRow.indices) {
                val h = normRow[colIdx]
                when {
                    roleIdx == -1 && (h == "r" || h == "ruolo" || h == "role" || h == "pos" || h == "posizione") -> roleIdx = colIdx
                    nameIdx == -1 && (h == "nome" || h == "calciatore" || h == "giocatore" || h == "player" || h == "cognome") -> nameIdx = colIdx
                    teamIdx == -1 && (h == "squadra" || h == "team" || h == "club" || h == "sq") -> teamIdx = colIdx
                    quotationIdx == -1 && (h == "qta" || h == "qt" || h == "quotazione" || h == "qa" || h == "quota" || h == "costo") -> quotationIdx = colIdx
                    fvmIdx == -1 && (h == "fvm" || h == "fvma" || h == "valore" || h == "fanta valore" || h == "fvm1000") -> fvmIdx = colIdx
                    starterIdx == -1 && (h.contains("tit") || h.contains("perc") || h.contains("starter")) -> starterIdx = colIdx
                    fmIdx == -1 && (h == "fm" || h == "fantamedia" || h == "fanta media" || h == "mediavoto" || h == "mv" || h == "media") -> fmIdx = colIdx
                }
            }

            if (nameIdx != -1 || roleIdx != -1) {
                return Pair(i, ColumnIndices(roleIdx, nameIdx, teamIdx, quotationIdx, fvmIdx, starterIdx, fmIdx))
            }
        }

        return Pair(-1, ColumnIndices())
    }

    private fun inferColumnsFromData(rows: List<List<String>>): ColumnIndices {
        if (rows.isEmpty()) return ColumnIndices()
        val sample = rows.take(10)
        val colCount = sample.maxOfOrNull { it.size } ?: 0

        var bestRoleCol = -1
        var bestNameCol = -1
        var bestTeamCol = -1

        for (c in 0 until colCount) {
            val values = sample.mapNotNull { it.getOrNull(c)?.trim() }
            val roleMatches = values.count { it.equals("P", true) || it.equals("D", true) || it.equals("C", true) || it.equals("A", true) }
            if (roleMatches >= values.size / 2 && bestRoleCol == -1) {
                bestRoleCol = c
            }
        }

        for (c in 0 until colCount) {
            if (c == bestRoleCol) continue
            val values = sample.mapNotNull { it.getOrNull(c)?.trim() }
            val avgLen = if (values.isNotEmpty()) values.map { it.length }.average() else 0.0
            val hasLetters = values.count { it.any { ch -> ch.isLetter() } }
            if (avgLen > 3.0 && hasLetters >= values.size / 2) {
                if (bestNameCol == -1) {
                    bestNameCol = c
                } else if (bestTeamCol == -1) {
                    bestTeamCol = c
                }
            }
        }

        return ColumnIndices(
            roleIdx = if (bestRoleCol != -1) bestRoleCol else 0,
            nameIdx = if (bestNameCol != -1) bestNameCol else 1,
            teamIdx = if (bestTeamCol != -1) bestTeamCol else 2,
            quotationIdx = -1,
            fvmIdx = -1
        )
    }

    private fun normalizeHeader(text: String): String {
        return text.lowercase(Locale.ROOT)
            .replace(".", "")
            .replace("_", "")
            .replace(" ", "")
            .trim()
    }

    private fun sanitizeAndFixPlayerName(raw: String): String {
        if (raw.isBlank()) return ""
        val fixed = fixCommonEncodingCorruption(raw)
            .replace("\"", "")
            .replace("`", "'")
            .replace("’", "'")
            .trim()

        if (fixed.matches(Regex("^[A-Z\\s.'-]+$")) && fixed.length > 2) {
            return fixed.split(" ").joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
        return fixed
    }

    private fun sanitizeTeamName(raw: String): String {
        val clean = raw.replace("\"", "").trim()
        if (clean.isBlank()) return "Serie A"
        return clean.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun isHeaderLike(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower == "nome" || lower == "calciatore" || lower == "player" || lower == "r" || lower == "ruolo"
    }

    private fun parseNumericValue(valueStr: String?): Int? {
        if (valueStr.isNullOrBlank()) return null
        val clean = valueStr.trim()
            .replace("\"", "")
            .replace("%", "")
            .replace("€", "")
            .replace(",", ".")
            .trim()
        val d = clean.toDoubleOrNull() ?: return null
        return d.toInt()
    }

    private fun parseDecimalValue(valueStr: String?): Double? {
        if (valueStr.isNullOrBlank()) return null
        val clean = valueStr.trim()
            .replace("\"", "")
            .replace("%", "")
            .replace("€", "")
            .replace(",", ".")
            .trim()
        return clean.toDoubleOrNull()
    }

    private fun estimateStarterFromFvm(fvm: Int, role: Role): Int {
        val base = when (role) {
            Role.P -> if (fvm >= 25) 95 else if (fvm >= 10) 70 else 25
            Role.D -> if (fvm >= 20) 90 else if (fvm >= 10) 75 else 45
            Role.C -> if (fvm >= 30) 90 else if (fvm >= 15) 75 else 50
            Role.A -> if (fvm >= 60) 95 else if (fvm >= 30) 80 else 55
        }
        return base.coerceIn(10, 99)
    }

    private fun calculateExpectedPoints(fvm: Int, role: Role, starterProb: Int, explicitFm: Double? = null): Double {
        if (explicitFm != null && explicitFm in 4.0..10.0) {
            return Math.round(explicitFm * 10.0) / 10.0
        }
        val (baseAvg, amplitude) = when (role) {
            Role.P -> Pair(5.5, 0.9)
            Role.D -> Pair(5.7, 1.3)
            Role.C -> Pair(5.9, 1.8)
            Role.A -> Pair(6.0, 2.7)
        }
        val fvmNormalized = (fvm.toDouble() / 350.0).coerceIn(0.0, 1.0)
        val starterFactor = (0.75 + 0.25 * (starterProb / 100.0)).coerceIn(0.75, 1.0)
        val calc = (baseAvg + fvmNormalized * amplitude) * starterFactor
        return Math.round(calc.coerceIn(4.5, 9.2) * 10.0) / 10.0
    }
}
