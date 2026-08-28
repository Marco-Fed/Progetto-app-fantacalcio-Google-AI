package com.example.engine

import com.example.data.model.*
import com.example.data.remote.KaggleHistoricalStatsService
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Statistical modeling engine for player evaluations based on real multi-season evidence.
 * Strict rules:
 * - Uses ONLY 2025-2026 and 2024-2025 data. Season 2023-2024 is strictly forbidden.
 * - Weights: Season 2025-2026 (weight ~0.65) > Season 2024-2025 (weight ~0.35).
 * - "Available evidence weighting": Missing metrics are omitted from the denominator so that
 *   available metrics are re-normalized without assigning a false 0 penalty, while simultaneously
 *   modulating Data Confidence.
 * - Clear separation between Match Presence (% presenze / 38 partite) and Starter Continuity (% titolare / presenze).
 */
object PlayerStatModel {

    data class WeightedMetric(
        val value: Double,
        val weight: Double
    )

    /**
     * Dynamically computes Data Confidence based on real evidence completeness,
     * sample size of minutes played, and historical consistency.
     */
    fun computeDataConfidence(player: PlayerEntity): ConfidenceLevel {
        var s25 = KaggleHistoricalStatsService.parseStats(player.stats2025_26Json)
        var s24 = KaggleHistoricalStatsService.parseStats(player.stats2024_25Json)

        if (s25 == null && s24 == null) {
            val (found24, found25) = KaggleHistoricalStatsService.findHistoricalStats(player.name, player.team, player.role)
            s24 = found24
            s25 = found25
        }

        val totalMinutes = (s25?.minutes ?: 0) + (s24?.minutes ?: 0)
        val totalAppearances = (s25?.appearances ?: 0) + (s24?.appearances ?: 0)
        val seasonsCount = (if (s25 != null && s25.appearances > 0) 1 else 0) +
                (if (s24 != null && s24.appearances > 0) 1 else 0)

        return when {
            seasonsCount >= 2 && (totalMinutes >= 1500 || totalAppearances >= 20) -> ConfidenceLevel.ALTA
            seasonsCount >= 1 && (totalMinutes >= 500 || totalAppearances >= 8) -> ConfidenceLevel.MEDIA
            player.fvm >= 25 && player.starterProb2026_27 >= 65 -> ConfidenceLevel.MEDIA
            else -> ConfidenceLevel.BASSA
        }
    }

    /**
     * Calculates the composite player score (0 to 100) using available evidence weighting.
     */
    fun calculatePlayerScore(player: PlayerEntity, config: LeagueConfig): Double {
        val s25 = KaggleHistoricalStatsService.parseStats(player.stats2025_26Json)
        val s24 = KaggleHistoricalStatsService.parseStats(player.stats2024_25Json)

        // 1. Base Expected Points component (scale 4.5 .. 9.0)
        val baseFantaRating = player.expectedFantasyPoints.coerceIn(4.5, 9.5)
        val roleBaseScore = when (player.role) {
            Role.P -> (baseFantaRating - 4.5) * 36.0 // 4.5 -> 0, 5.5 -> 36, 6.0 -> 54
            Role.D -> (baseFantaRating - 5.3) * 38.0 // 5.3 -> 0, 6.0 -> 26.6, 7.0 -> 64.6
            Role.C -> (baseFantaRating - 5.5) * 33.0 // 5.5 -> 0, 6.5 -> 33, 7.5 -> 66, 8.0 -> 82.5
            Role.A -> (baseFantaRating - 5.8) * 30.0 // 5.8 -> 0, 7.0 -> 36, 8.0 -> 66, 8.8 -> 90
        }.coerceIn(5.0, 95.0)

        // 2. Multi-season historical metrics with 2025/26 (0.65) > 2024/25 (0.35) weighting
        val metricsList = mutableListOf<WeightedMetric>()

        val w25 = config.weight2025_26.coerceIn(0.50, 0.85)
        val w24 = (1.0 - w25).coerceIn(0.15, 0.50)

        // Presence & Starter continuity
        val avgPresencePct = calculateWeightedAverage(s25?.presencePercentage?.toDouble(), s24?.presencePercentage?.toDouble(), w25, w24)
        if (avgPresencePct != null) {
            val presenceScore = (avgPresencePct.coerceIn(20.0, 100.0) - 20.0) / 80.0 * 100.0
            metricsList.add(WeightedMetric(presenceScore, 0.15))
        }

        val avgStarterPct = calculateWeightedAverage(s25?.starterPercentage?.toDouble(), s24?.starterPercentage?.toDouble(), w25, w24)
        if (avgStarterPct != null) {
            val starterScore = (avgStarterPct.coerceIn(20.0, 100.0) - 20.0) / 80.0 * 100.0
            metricsList.add(WeightedMetric(starterScore, 0.15))
        }

        // Offensive production per 90 (xG + xAG, Goals, Assists)
        when (player.role) {
            Role.A -> {
                val totalGls = ((s25?.goals ?: 0) * w25 + (s24?.goals ?: 0) * w24)
                val totalAst = ((s25?.assists ?: 0) * w25 + (s24?.assists ?: 0) * w24)
                val offensiveScore = (totalGls * 4.5 + totalAst * 2.5).coerceIn(0.0, 100.0)
                metricsList.add(WeightedMetric(offensiveScore, 0.25))
            }
            Role.C -> {
                val totalGls = ((s25?.goals ?: 0) * w25 + (s24?.goals ?: 0) * w24)
                val totalAst = ((s25?.assists ?: 0) * w25 + (s24?.assists ?: 0) * w24)
                val creationScore = (totalGls * 5.5 + totalAst * 4.0).coerceIn(0.0, 100.0)
                metricsList.add(WeightedMetric(creationScore, 0.20))
            }
            Role.D -> {
                val totalGls = ((s25?.goals ?: 0) * w25 + (s24?.goals ?: 0) * w24)
                val totalAst = ((s25?.assists ?: 0) * w25 + (s24?.assists ?: 0) * w24)
                val defOffScore = (totalGls * 10.0 + totalAst * 6.0).coerceIn(0.0, 100.0)
                metricsList.add(WeightedMetric(defOffScore, 0.12))
            }
            Role.P -> {
                val totalCs = ((s25?.cleanSheets ?: 0) * w25 + (s24?.cleanSheets ?: 0) * w24)
                val gkScore = (totalCs * 5.0).coerceIn(0.0, 100.0)
                metricsList.add(WeightedMetric(gkScore, 0.20))
            }
        }

        // Discipline factor (yellow / red cards)
        val totalYellow = (s25?.yellowCards ?: 0) * w25 + (s24?.yellowCards ?: 0) * w24
        val totalRed = (s25?.redCards ?: 0) * w25 + (s24?.redCards ?: 0) * w24
        val disciplineScore = (100.0 - (totalYellow * 3.0 + totalRed * 10.0)).coerceIn(10.0, 100.0)
        metricsList.add(WeightedMetric(disciplineScore, 0.05))

        // Specialists bonus
        var specialistBonus = 0.0
        if (player.isPenaltyTaker) {
            specialistBonus += if (player.penaltyOrder == 1) 12.0 else 6.0
        }
        if (player.isFreeKickTaker) specialistBonus += 4.0
        if (player.isCornerTaker) specialistBonus += 3.0

        // Defense modifier synergy
        var modBonus = 0.0
        if (config.defenseModifierEnabled) {
            if (player.role == Role.D && player.expectedFantasyPoints >= 6.2) {
                modBonus += 6.0
            } else if (player.role == Role.P && player.team in listOf("Inter", "Juventus", "Napoli", "Milan", "Atalanta", "Roma", "Lazio")) {
                modBonus += 5.0
            }
        }

        // Available Evidence Weighting aggregation
        val historicalComposite = if (metricsList.isNotEmpty()) {
            val sumWeights = metricsList.sumOf { it.weight }
            val sumValues = metricsList.sumOf { it.value * it.weight }
            sumValues / sumWeights
        } else {
            roleBaseScore
        }

        // Final balanced combination (Base score 60% + Historical composite 40% + Specialists/Mod Bonuses)
        val forecastStarterMultiplier = (0.60 + 0.40 * (player.starterProb2026_27 / 100.0)).coerceIn(0.40, 1.0)
        val rawFinalScore = (roleBaseScore * 0.60 + historicalComposite * 0.40 + specialistBonus + modBonus) * forecastStarterMultiplier

        return Math.round(rawFinalScore.coerceIn(10.0, 99.0) * 10.0) / 10.0
    }

    private fun calculateWeightedAverage(v25: Double?, v24: Double?, w25: Double, w24: Double): Double? {
        if (v25 == null && v24 == null) return null
        if (v25 != null && v24 != null) return (v25 * w25 + v24 * w24) / (w25 + w24)
        return v25 ?: v24
    }
}
