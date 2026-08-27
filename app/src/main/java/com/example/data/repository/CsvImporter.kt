package com.example.data.repository

import com.example.data.model.PlayerEntity
import java.io.InputStream

sealed class CsvImportResult {
    data class Success(val players: List<PlayerEntity>, val warningCount: Int, val sourceFormat: String = "CSV") : CsvImportResult()
    data class Error(val message: String, val rowNumber: Int? = null) : CsvImportResult()
}

/**
 * Backward compatibility wrapper delegating to universal ListoneImporter.
 */
object CsvImporter {

    fun parseCsv(inputStream: InputStream): CsvImportResult {
        return when (val res = ListoneImporter.parseStream(inputStream)) {
            is ListoneImportResult.Success -> CsvImportResult.Success(res.players, res.warningCount, res.sourceFormat)
            is ListoneImportResult.Error -> CsvImportResult.Error(res.message, res.rowNumber)
        }
    }

    fun parseCsvText(text: String): CsvImportResult {
        return when (val res = ListoneImporter.parseText(text)) {
            is ListoneImportResult.Success -> CsvImportResult.Success(res.players, res.warningCount, res.sourceFormat)
            is ListoneImportResult.Error -> CsvImportResult.Error(res.message, res.rowNumber)
        }
    }
}
