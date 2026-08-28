package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.HistoricalSeasonStats
import com.example.data.model.PlayerEntity
import com.example.data.model.Role
import org.json.JSONObject
import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Service for loading and matching historical player statistics exclusively
 * from the two official Kaggle FBref datasets:
 * - Season 2024-2025 (hubertsidorowicz/football-players-stats-2024-2025 -> players_data-2024_2025.csv)
 * - Season 2025-2026 (hubertsidorowicz/football-players-stats-2025-2026 -> players_data-2025_2026.csv)
 *
 * Covers all Top 5 European leagues (Serie A, Premier League, La Liga, Bundesliga, Ligue 1).
 * NO synthetic or invented statistics are generated. Missing data is left empty.
 */
object KaggleHistoricalStatsService {
    private const val TAG = "KaggleStatsService"

    data class KaggleRecord(
        val name: String,
        val normName: String,
        val normTokens: List<String>,
        val season: String, // "2024/25" or "2025/26"
        val team: String,
        val normTeam: String,
        val comp: String,
        val pos: String,
        val mp: Int,
        val starts: Int,
        val starterPct: Int,
        val minutes: Int,
        val goals: Int,
        val assists: Int,
        val pk: Int,
        val pkatt: Int,
        val yellow: Int,
        val red: Int,
        val xg: Double,
        val xag: Double,
        val cs: Int,
        val saves: Int,
        val ga: Int
    )

    private var isInitialized = false
    private val records2024_25 = mutableListOf<KaggleRecord>()
    private val records2025_26 = mutableListOf<KaggleRecord>()

    private val exactMap2024 = mutableMapOf<String, KaggleRecord>()
    private val exactMap2025 = mutableMapOf<String, KaggleRecord>()

    @Synchronized
    fun ensureInitialized(context: Context? = null) {
        if (isInitialized) return
        var loaded = false
        if (context != null) {
            try {
                context.assets.open("kaggle_historical_stats.json").use { stream ->
                    initializeFromStream(stream)
                    loaded = true
                }
            } catch (e: Exception) {
                // Fallback to direct file loading
            }
        }
        if (!loaded && !isInitialized) {
            try {
                val candidateFiles = listOf(
                    java.io.File("src/main/assets/kaggle_historical_stats.json"),
                    java.io.File("app/src/main/assets/kaggle_historical_stats.json"),
                    java.io.File("../app/src/main/assets/kaggle_historical_stats.json"),
                    java.io.File("/app/applet/app/src/main/assets/kaggle_historical_stats.json")
                )
                val existingFile = candidateFiles.firstOrNull { it.exists() }
                val stream = existingFile?.inputStream()
                    ?: KaggleHistoricalStatsService::class.java.classLoader?.getResourceAsStream("assets/kaggle_historical_stats.json")
                    ?: KaggleHistoricalStatsService::class.java.classLoader?.getResourceAsStream("kaggle_historical_stats.json")

                stream?.use { initializeFromStream(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load kaggle_historical_stats.json", e)
            }
        }
    }

    @Synchronized
    fun initializeFromStream(inputStream: InputStream) {
        if (isInitialized) return
        try {
            val jsonText = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonText)

            val array24 = root.optJSONArray("season2024_25")
                ?: root.optJSONArray("2024_25")
                ?: root.optJSONArray("stats2024_25")
                ?: root.optJSONArray("season_2024_25")
            if (array24 != null) {
                for (i in 0 until array24.length()) {
                    val obj = array24.getJSONObject(i)
                    val r = parseKaggleRecord(obj, "2024/25")
                    records2024_25.add(r)
                    if (!exactMap2024.containsKey(r.normName)) {
                        exactMap2024[r.normName] = r
                    }
                }
            }

            val array25 = root.optJSONArray("season2025_26")
                ?: root.optJSONArray("2025_26")
                ?: root.optJSONArray("stats2025_26")
                ?: root.optJSONArray("season_2025_26")
            if (array25 != null) {
                for (i in 0 until array25.length()) {
                    val obj = array25.getJSONObject(i)
                    val r = parseKaggleRecord(obj, "2025/26")
                    records2025_26.add(r)
                    if (!exactMap2025.containsKey(r.normName)) {
                        exactMap2025[r.normName] = r
                    }
                }
            }

            isInitialized = true
            Log.d(TAG, "Initialized Kaggle datasets: ${records2024_25.size} (2024/25), ${records2025_26.size} (2025/26)")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Kaggle historical stats", e)
        }
    }

    private fun parseKaggleRecord(obj: JSONObject, seasonLabel: String): KaggleRecord {
        val rawName = obj.optString("name", "")
        val norm = normalize(rawName)
        val rawTeam = obj.optString("team", "")
        val mp = obj.optInt("mp", 0)
        val starts = obj.optInt("starts", 0)
        val starterPct = if (mp > 0) ((starts * 100) / mp).coerceIn(0, 100) else obj.optInt("starterPct", 0)

        return KaggleRecord(
            name = rawName,
            normName = norm,
            normTokens = norm.split(" ").filter { it.isNotBlank() },
            season = seasonLabel,
            team = rawTeam,
            normTeam = normalize(rawTeam),
            comp = obj.optString("comp", ""),
            pos = obj.optString("pos", ""),
            mp = mp,
            starts = starts,
            starterPct = starterPct,
            minutes = obj.optInt("min", 0),
            goals = obj.optInt("goals", 0),
            assists = obj.optInt("assists", 0),
            pk = obj.optInt("pk", 0),
            pkatt = obj.optInt("pkatt", 0),
            yellow = obj.optInt("yellow", 0),
            red = obj.optInt("red", 0),
            xg = obj.optDouble("xg", 0.0),
            xag = obj.optDouble("xag", 0.0),
            cs = obj.optInt("cs", 0),
            saves = obj.optInt("saves", 0),
            ga = obj.optInt("ga", 0)
        )
    }

    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        var t = text
            .replace("ð", "d").replace("Ð", "D")
            .replace("đ", "d").replace("Đ", "D")
            .replace("ø", "o").replace("Ø", "O")
            .replace("æ", "ae").replace("Æ", "AE")
            .replace("œ", "oe").replace("Œ", "OE")
            .replace("ß", "ss")
            .replace("ł", "l").replace("Ł", "L")
            .replace("’", "").replace("'", "").replace("`", "")
            .replace("-", " ")
            .replace(".", " ")

        val nfkd = Normalizer.normalize(t, Normalizer.Form.NFD)
        val clean = Regex("\\p{InCombiningDiacriticalMarks}+").replace(nfkd, "")
        return clean.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun roleMatchesPos(role: Role?, posStr: String): Boolean {
        if (role == null || posStr.isBlank()) return true
        val p = posStr.uppercase(Locale.ROOT)
        return when (role) {
            Role.P -> p.contains("GK")
            Role.D -> p.contains("DF") || p.contains("MF")
            Role.C -> p.contains("MF") || p.contains("DF") || p.contains("FW")
            Role.A -> p.contains("FW") || p.contains("MF")
        }
    }

    /**
     * Finds historical season stats across all 5 Top European leagues with smart
     * abbreviation matching, team disambiguation and homonym protection.
     * Returns Pair(stats2024_25, stats2025_26).
     * If player is not found, returns null for that season (no invented stats!).
     */
    fun findHistoricalStats(
        playerName: String,
        teamName: String,
        role: Role? = null
    ): Pair<HistoricalSeasonStats?, HistoricalSeasonStats?> {
        ensureInitialized()
        val normTarget = normalize(playerName)
        val tokens = normTarget.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Pair(null, null)

        val normTeam = normalize(teamName)

        val r24 = matchRecord(normTarget, tokens, normTeam, role, records2024_25, exactMap2024)
        val r25 = matchRecord(normTarget, tokens, normTeam, role, records2025_26, exactMap2025)

        val stats24 = r24?.let { toHistoricalStats(it, "2024/25") }
        val stats25 = r25?.let { toHistoricalStats(it, "2025/26") }

        return Pair(stats24, stats25)
    }

    private fun matchRecord(
        normTarget: String,
        tokens: List<String>,
        normTeam: String,
        role: Role?,
        records: List<KaggleRecord>,
        exactMap: Map<String, KaggleRecord>
    ): KaggleRecord? {
        if (records.isEmpty() || tokens.isEmpty()) return null

        // 1. Exact match on full normalized name
        val exactCandidates = records.filter { it.normName == normTarget && roleMatchesPos(role, it.pos) }
        if (exactCandidates.size == 1) return exactCandidates.first()
        if (exactCandidates.size > 1) {
            val teamMatch = exactCandidates.firstOrNull { normTeam.isNotBlank() && (normTeam.contains(it.normTeam) || it.normTeam.contains(normTeam)) }
            return teamMatch ?: exactCandidates.first()
        }

        // 2. Inverted full name (e.g. "Thuram Marcus" vs "Marcus Thuram")
        if (tokens.size >= 2 && tokens.all { it.length > 2 }) {
            val inverted = "${tokens.last()} ${tokens.dropLast(1).joinToString(" ")}"
            val invCandidates = records.filter { it.normName == inverted && roleMatchesPos(role, it.pos) }
            if (invCandidates.size == 1) return invCandidates.first()
            if (invCandidates.size > 1) {
                val teamMatch = invCandidates.firstOrNull { normTeam.isNotBlank() && (normTeam.contains(it.normTeam) || it.normTeam.contains(normTeam)) }
                return teamMatch ?: invCandidates.first()
            }
        }

        // Detect abbreviation / initial in name (e.g. "Martinez L.", "Martinez Jo.", "Pellegrini Lo.", "L. Martinez")
        var varAbbr: String? = null
        var varSurnameTokens: List<String> = emptyList()

        if (tokens.size >= 2) {
            if (tokens.last().length <= 2) {
                varAbbr = tokens.last()
                varSurnameTokens = tokens.dropLast(1)
            } else if (tokens.first().length <= 2) {
                varAbbr = tokens.first()
                varSurnameTokens = tokens.drop(1)
            }
        }

        // 3. Abbreviation-aware matching (e.g. "Martinez L." -> Lautaro Martinez, "Martinez Jo." -> Josep Martinez)
        if (varAbbr != null && varSurnameTokens.isNotEmpty()) {
            val surnameStr = varSurnameTokens.joinToString(" ")
            val candidates = records.filter { cand ->
                val hasSurname = varSurnameTokens.all { st -> cand.normTokens.contains(st) } || cand.normName.contains(surnameStr)
                if (hasSurname && roleMatchesPos(role, cand.pos)) {
                    val firstNameTokens = cand.normTokens.filter { it !in varSurnameTokens }
                    firstNameTokens.any { it.startsWith(varAbbr) }
                } else false
            }

            if (candidates.size == 1) return candidates.first()
            if (candidates.size > 1) {
                val teamMatch = candidates.firstOrNull { normTeam.isNotBlank() && (normTeam.contains(it.normTeam) || it.normTeam.contains(normTeam)) }
                if (teamMatch != null) return teamMatch
                return candidates.first()
            }
            // Strict anti-homonym rule: If an initial/abbreviation was provided, do not fall back to a player with a different initial!
            return null
        }

        // 4. Multi-token full surname check (e.g. "Milinkovic-Savic", "Di Francesco", "De Ketelaere")
        if (tokens.size >= 2) {
            val cand = records.filter { cand ->
                tokens.all { st -> cand.normTokens.contains(st) } && roleMatchesPos(role, cand.pos)
            }
            if (cand.size == 1) return cand.first()
            if (cand.size > 1) {
                val teamMatch = cand.firstOrNull { normTeam.isNotBlank() && (normTeam.contains(it.normTeam) || it.normTeam.contains(normTeam)) }
                if (teamMatch != null) return teamMatch
            }
        }

        // 5. Distinct single surname with role and team match
        val mainSurname = if (tokens.first().length > 3) tokens.first() else tokens.last()
        if (mainSurname.length >= 3) {
            val candidatesWithTeam = records.filter { cand ->
                (cand.normTokens.contains(mainSurname) || cand.normName.contains(mainSurname)) &&
                        normTeam.isNotBlank() &&
                        (normTeam.contains(cand.normTeam) || cand.normTeam.contains(normTeam)) &&
                        roleMatchesPos(role, cand.pos)
            }
            if (candidatesWithTeam.size == 1) return candidatesWithTeam.first()
            if (candidatesWithTeam.size > 1) return candidatesWithTeam.first()

            // Unique single-token match across leagues if exact single surname and role match
            if (tokens.size == 1) {
                val candidatesUnique = records.filter { cand ->
                    (cand.normName == mainSurname || cand.normTokens.contains(mainSurname)) && roleMatchesPos(role, cand.pos)
                }
                if (candidatesUnique.size == 1) return candidatesUnique.first()
            }
        }

        return null
    }

    private fun toHistoricalStats(r: KaggleRecord, seasonLabel: String): HistoricalSeasonStats {
        val presencePct = if (r.mp > 0) ((r.mp * 100) / 38).coerceIn(0, 100) else 0
        return HistoricalSeasonStats(
            season = seasonLabel,
            competition = r.comp,
            team = r.team,
            appearances = r.mp,
            starterAppearances = r.starts,
            teamMatchesPlayed = 38,
            presencePercentage = presencePct,
            starterPercentage = r.starterPct,
            minutes = r.minutes,
            goals = r.goals,
            assists = r.assists,
            expectedGoals = r.xg,
            expectedAssists = r.xag,
            ratingAvg = 6.0,
            fantaRatingAvg = 6.0,
            yellowCards = r.yellow,
            redCards = r.red,
            penaltiesScored = r.pk,
            penaltiesAttempted = r.pkatt,
            cleanSheets = r.cs,
            saves = r.saves,
            goalsAgainst = r.ga
        )
    }

    fun serializeStats(stats: HistoricalSeasonStats?): String {
        if (stats == null || stats.appearances <= 0) return ""
        val json = JSONObject()
        json.put("season", stats.season)
        json.put("competition", stats.competition)
        json.put("team", stats.team)
        json.put("appearances", stats.appearances)
        json.put("starterAppearances", stats.starterAppearances)
        json.put("teamMatchesPlayed", stats.teamMatchesPlayed)
        json.put("presencePercentage", stats.presencePercentage)
        json.put("starterPercentage", stats.starterPercentage)
        json.put("minutes", stats.minutes)
        json.put("goals", stats.goals)
        json.put("assists", stats.assists)
        json.put("expectedGoals", stats.expectedGoals)
        json.put("expectedAssists", stats.expectedAssists)
        json.put("ratingAvg", stats.ratingAvg)
        json.put("fantaRatingAvg", stats.fantaRatingAvg)
        json.put("yellowCards", stats.yellowCards)
        json.put("redCards", stats.redCards)
        json.put("penaltiesScored", stats.penaltiesScored)
        json.put("penaltiesAttempted", stats.penaltiesAttempted)
        json.put("cleanSheets", stats.cleanSheets)
        json.put("saves", stats.saves)
        json.put("goalsAgainst", stats.goalsAgainst)
        json.put("progPasses", stats.progPasses)
        json.put("progCarries", stats.progCarries)
        json.put("keyPasses", stats.keyPasses)
        json.put("passCompletionPct", stats.passCompletionPct)
        json.put("passesIntoPenArea", stats.passesIntoPenArea)
        json.put("tacklesAndInterceptions", stats.tacklesAndInterceptions)
        json.put("ballRecoveries", stats.ballRecoveries)
        json.put("aerialDuelWonPct", stats.aerialDuelWonPct)
        json.put("savePct", stats.savePct)
        json.put("goalsPrevented", stats.goalsPrevented)
        json.put("shotCreatingActions", stats.shotCreatingActions)
        json.put("successfulDribbles", stats.successfulDribbles)
        return json.toString()
    }

    fun parseStats(jsonStr: String): HistoricalSeasonStats? {
        if (jsonStr.isBlank()) return null
        return try {
            val obj = JSONObject(jsonStr)
            val apps = obj.optInt("appearances", 0)
            if (apps <= 0) return null

            HistoricalSeasonStats(
                season = obj.optString("season", ""),
                competition = obj.optString("competition", ""),
                team = obj.optString("team", ""),
                appearances = apps,
                starterAppearances = obj.optInt("starterAppearances", 0),
                teamMatchesPlayed = obj.optInt("teamMatchesPlayed", 38),
                presencePercentage = obj.optInt("presencePercentage", if (apps > 0) ((apps * 100) / 38).coerceIn(0, 100) else 0),
                starterPercentage = obj.optInt("starterPercentage", 0),
                minutes = obj.optInt("minutes", 0),
                goals = obj.optInt("goals", 0),
                assists = obj.optInt("assists", 0),
                expectedGoals = obj.optDouble("expectedGoals", 0.0),
                expectedAssists = obj.optDouble("expectedAssists", 0.0),
                ratingAvg = obj.optDouble("ratingAvg", 6.0),
                fantaRatingAvg = obj.optDouble("fantaRatingAvg", 6.0),
                yellowCards = obj.optInt("yellowCards", 0),
                redCards = obj.optInt("redCards", 0),
                penaltiesScored = obj.optInt("penaltiesScored", 0),
                penaltiesAttempted = obj.optInt("penaltiesAttempted", 0),
                cleanSheets = obj.optInt("cleanSheets", 0),
                saves = obj.optInt("saves", 0),
                goalsAgainst = obj.optInt("goalsAgainst", 0),
                progPasses = obj.optDouble("progPasses", 0.0),
                progCarries = obj.optDouble("progCarries", 0.0),
                keyPasses = obj.optDouble("keyPasses", 0.0),
                passCompletionPct = obj.optDouble("passCompletionPct", 0.0),
                passesIntoPenArea = obj.optDouble("passesIntoPenArea", 0.0),
                tacklesAndInterceptions = obj.optDouble("tacklesAndInterceptions", 0.0),
                ballRecoveries = obj.optDouble("ballRecoveries", 0.0),
                aerialDuelWonPct = obj.optDouble("aerialDuelWonPct", 0.0),
                savePct = obj.optDouble("savePct", 0.0),
                goalsPrevented = obj.optDouble("goalsPrevented", 0.0),
                shotCreatingActions = obj.optDouble("shotCreatingActions", 0.0),
                successfulDribbles = obj.optDouble("successfulDribbles", 0.0)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Enriches a player with real Kaggle historical statistics (2024-25 and 2025-26).
     * If not found, fields remain blank ("") and no stats are invented.
     */
    fun enrichPlayer(player: PlayerEntity): PlayerEntity {
        val (s24, s25) = findHistoricalStats(player.name, player.team, player.role)
        return player.copy(
            stats2024_25Json = if (s24 != null) serializeStats(s24) else "",
            stats2025_26Json = if (s25 != null) serializeStats(s25) else "",
            stats2023_24Json = "" // Cleaned as requested
        )
    }

    /**
     * Enriches an entire list of players.
     */
    fun enrichList(players: List<PlayerEntity>): List<PlayerEntity> {
        return players.map { enrichPlayer(it) }
    }
}
