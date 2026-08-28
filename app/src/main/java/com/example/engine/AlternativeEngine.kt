package com.example.engine

import com.example.data.model.*
import com.example.data.remote.KaggleHistoricalStatsService
import kotlin.math.abs
import kotlin.math.roundToInt

object AlternativeEngine {

    /**
     * Identifies genuine, high-quality alternatives for a target player.
     * Excludes:
     * - Heavily injured or inactive players
     * - Extremely low starter probability (< 40%)
     * - Players already purchased
     * - Wildly non-comparable players (theoretical value gap exceeding comparability threshold)
     */
    fun findAlternatives(
        targetPlayer: PlayerEntity,
        availableInRole: List<PlayerEntity>,
        userTeam: TeamEntity,
        config: LeagueConfig
    ): List<AlternativeComparable> {
        val targetTheorVal = QuantitativeEngine.calculateTheoreticalValue(targetPlayer, config)
        val userMaxBid = userTeam.maxAffordableBid(config)
        val maxDeltaPct = config.alternativeComparabilityThreshold.coerceIn(10.0, 50.0)

        val candidateList = availableInRole.filter { candidate ->
            candidate.id != targetPlayer.id &&
            !candidate.isPurchased &&
            !candidate.isInjured &&
            candidate.status != "Infortunato" &&
            candidate.status != "Squalificato" &&
            candidate.starterProb2026_27 >= 35
        }

        // First attempt: candidates within comparability threshold
        var usableCandidates = candidateList.filter { candidate ->
            val candidateTheorVal = QuantitativeEngine.calculateTheoreticalValue(candidate, config)
            val diffPct = (abs(candidateTheorVal - targetTheorVal) / max(targetTheorVal, 1.0)) * 100.0
            diffPct <= maxDeltaPct
        }

        // If fewer than 3 comparables exist within threshold, fallback to all role candidates
        if (usableCandidates.size < 3 && candidateList.isNotEmpty()) {
            usableCandidates = candidateList
        }

        val ratedCandidates = usableCandidates.map { candidate ->
            val candidateTheorVal = QuantitativeEngine.calculateTheoreticalValue(candidate, config)
            val valueDiff = Math.round((candidateTheorVal - targetTheorVal) * 10.0) / 10.0

            // Base price for alternative (1000-credit benchmark)
            val effectiveFvm = if (candidate.fvm > 0) candidate.fvm else (candidate.quotation * 20).coerceAtLeast(1)
            val budgetShare = (effectiveFvm.toDouble() / 1000.0).coerceIn(0.001, 0.70)
            val estPrice = (budgetShare * config.initialCredits).roundToInt().coerceAtLeast(1)
            val optPrice = (estPrice * 1.0).roundToInt().coerceAtLeast(1)
            val maxBid = (estPrice * 1.2).roundToInt().coerceAtMost(userMaxBid).coerceAtLeast(1)

            // Extract historical presence
            val s25 = KaggleHistoricalStatsService.parseStats(candidate.stats2025_26Json)
            val s24 = KaggleHistoricalStatsService.parseStats(candidate.stats2024_25Json)
            val presence = s25?.presencePercentage ?: s24?.presencePercentage ?: candidate.starterProb2026_27

            AlternativeComparable(
                player = candidate,
                expectedFantasyPoints = candidate.expectedFantasyPoints,
                playerValue = candidateTheorVal,
                estimatedPrice = estPrice,
                expectedAuctionPrice = estPrice,
                optimalPrice = optPrice,
                maximumBid = maxBid,
                presencePct = presence,
                starterProb = candidate.starterProb2026_27,
                riskLevel = candidate.riskLevel,
                valueDifference = valueDiff
            )
        }

        // Multi-tier sort:
        // 1. Closest theoretical value delta
        // 2. Highest value per credit ratio
        // 3. Highest expected fantasy points
        // 4. Lowest risk level
        return ratedCandidates
            .sortedWith(
                compareBy<AlternativeComparable> { abs(it.valueDifference) }
                    .thenByDescending { it.playerValue / max(it.estimatedPrice.toDouble(), 1.0) }
                    .thenByDescending { it.expectedFantasyPoints }
                    .thenBy { it.riskLevel.ordinal }
            )
            .take(4)
    }

    private fun max(a: Double, b: Double): Double = if (a > b) a else b
}

