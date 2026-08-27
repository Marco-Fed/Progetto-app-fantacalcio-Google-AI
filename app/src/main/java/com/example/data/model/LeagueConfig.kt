package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "league_config")
data class LeagueConfig(
    @PrimaryKey val id: Int = 1,
    val leagueName: String = "Lega Serie A 2026/27",
    val numTeams: Int = 8,
    val initialCredits: Int = 500,
    val slotsP: Int = 3,
    val slotsD: Int = 8,
    val slotsC: Int = 8,
    val slotsA: Int = 6,
    
    // Modifiers & Scoring
    val defenseModifierEnabled: Boolean = true,
    val includeGoalkeeperInDefenseModifier: Boolean = true,
    val goalScoredPoints: Double = 3.0,
    val goalConcededPoints: Double = -1.0,
    val penaltyScoredPoints: Double = 3.0,
    val penaltySavedPoints: Double = 3.0,
    val penaltyMissedPoints: Double = -2.0,
    val assistPoints: Double = 1.0,
    val yellowCardPoints: Double = -0.5,
    val redCardPoints: Double = -1.0,
    val ownGoalPoints: Double = -1.0,
    val cleanSheetPoints: Double = 0.0,
    
    // Thresholds
    val goalThreshold: Int = 66,
    val goalBandStep: Int = 4,
    
    // Weight parameters for 3 seasons (2025-26 > 2024-25 > 2023-24)
    val weight2025_26: Double = 0.50,
    val weight2024_25: Double = 0.35,
    val weight2023_24: Double = 0.15
) {
    val totalSlots: Int get() = slotsP + slotsD + slotsC + slotsA
    
    fun slotsForRole(role: Role): Int = when (role) {
        Role.P -> slotsP
        Role.D -> slotsD
        Role.C -> slotsC
        Role.A -> slotsA
    }
}
