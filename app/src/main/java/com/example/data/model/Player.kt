package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Role(val code: String, val displayName: String, val order: Int) {
    P("P", "Portiere", 1),
    D("D", "Difensore", 2),
    C("C", "Centrocampista", 3),
    A("A", "Attaccante", 4);

    companion object {
        fun fromString(str: String): Role {
            return when (str.trim().uppercase()) {
                "P", "POR", "PORTIERE" -> P
                "D", "DIF", "DIFENSORE" -> D
                "C", "CEN", "CENTROCAMPISTA" -> C
                "A", "ATT", "ATTACCANTE" -> A
                else -> C
            }
        }
    }
}

enum class RiskLevel { BASSO, MEDIO, ALTO }
enum class ConfidenceLevel { BASSA, MEDIA, ALTA }

data class HistoricalSeasonStats(
    val season: String, // "2024/25", "2025/26"
    val competition: String = "", // "Serie A", "Premier League", "La Liga", "Bundesliga", "Ligue 1"
    val team: String = "",
    val appearances: Int = 0, // MP (Matches Played)
    val starterAppearances: Int = 0, // Starts (Titolare)
    val starterPercentage: Int = 0, // % Titolarità (Starts / MP * 100)
    val minutes: Int = 0, // Minuti giocati
    val goals: Int = 0,
    val assists: Int = 0,
    val expectedGoals: Double = 0.0, // xG
    val expectedAssists: Double = 0.0, // xAG / xA
    val ratingAvg: Double = 6.0, // Media Voto
    val fantaRatingAvg: Double = 6.0, // FantaMedia
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val penaltiesScored: Int = 0,
    val penaltiesAttempted: Int = 0,
    val cleanSheets: Int = 0, // Portieri
    val saves: Int = 0, // Portieri
    val goalsAgainst: Int = 0 // Portieri
)

data class SpecialistsInfo(
    val isPenaltyTaker: Boolean = false,
    val penaltyOrder: Int = 0, // 1 for first choice, 2 for second
    val isFreeKickTaker: Boolean = false,
    val isCornerTaker: Boolean = false,
    val confidence: ConfidenceLevel = ConfidenceLevel.ALTA
)

data class BallottaggioInfo(
    val rivalName: String? = null,
    val rivalRole: String? = null,
    val starterShare: Int = 100, // percentage of starter share
    val pairRecommended: Boolean = false
)

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val team: String,
    val role: Role,
    val mantraRole: String = "",
    val quotation: Int, // Quotazione attuale
    val fvm: Int, // FantaValore Mercato iniziale
    
    // Titolarità Prevista 2026-2027
    val starterProb2026_27: Int, // Forecast percentage e.g. 84%
    val expectedFantasyPoints: Double, // Expected FantaMedia/pts
    val expectedMinutes: Int, // Internal Expected minutes per match
    val riskLevel: RiskLevel = RiskLevel.MEDIO,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.ALTA,
    
    // Specialists & Ballottaggio
    val isPenaltyTaker: Boolean = false,
    val penaltyOrder: Int = 0,
    val isFreeKickTaker: Boolean = false,
    val isCornerTaker: Boolean = false,
    val ballottaggioRival: String? = null,
    val ballottaggioShare: Int = 100,
    
    // Historical stats serialised or stored
    val stats2023_24Json: String = "",
    val stats2024_25Json: String = "",
    val stats2025_26Json: String = "",
    
    // Status & Notes
    val status: String = "Disponibile", // "Disponibile", "Infortunato", "Squalificato"
    val injuryNotes: String = "",
    val expectedReturnDate: String = "", // e.g. "Rientro metà Settembre 2026"
    val isPurchased: Boolean = false,
    val purchasedByTeam: String? = null,
    val purchasePrice: Int? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isInjured: Boolean get() = status.equals("Infortunato", ignoreCase = true) || expectedReturnDate.isNotBlank()
}
