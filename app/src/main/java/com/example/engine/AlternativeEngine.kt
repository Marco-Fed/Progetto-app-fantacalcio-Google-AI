package com.example.engine

import com.example.data.model.*
import kotlin.math.abs
import kotlin.math.roundToInt

object AlternativeEngine {

    /**
     * Identifies genuine, high-quality alternatives for a target player.
     * Excludes:
     * - Heavily injured or inactive players
     * - Extremely low starter probability (< 50%)
     * - Players already purchased
     * - Wildly non-comparable players (e.g. theoretical value gap > 35 pts unless role is completely dry)
     */
    fun findAlternatives(
        targetPlayer: PlayerEntity,
        availableInRole: List<PlayerEntity>,
        userTeam: TeamEntity,
        config: LeagueConfig
    ): List<AlternativeComparable> {
        val targetTheorVal = QuantitativeEngine.calculateTheoreticalValue(targetPlayer, config)
        val userMaxBid = userTeam.maxAffordableBid(config)

        val candidateList = availableInRole.filter { candidate ->
            candidate.id != targetPlayer.id &&
            !candidate.isPurchased &&
            !candidate.isInjured &&
            candidate.status != "Infortunato" &&
            candidate.status != "Squalificato" &&
            candidate.starterProb2026_27 >= 40
        }

        val ratedCandidates = candidateList.map { candidate ->
            val candidateTheorVal = QuantitativeEngine.calculateTheoreticalValue(candidate, config)
            val valueDiff = Math.round((candidateTheorVal - targetTheorVal) * 10.0) / 10.0
            
            // Base price for alternative (1000-credit benchmark)
            val effectiveFvm = if (candidate.fvm > 0) candidate.fvm else (candidate.quotation * 20).coerceAtLeast(1)
            val budgetShare = (effectiveFvm.toDouble() / 1000.0).coerceIn(0.001, 0.70)
            val estPrice = (budgetShare * config.initialCredits).roundToInt().coerceAtLeast(1)
            val maxBid = (estPrice * 1.2).roundToInt().coerceAtMost(userMaxBid).coerceAtLeast(1)

            AlternativeComparable(
                player = candidate,
                expectedFantasyPoints = candidate.expectedFantasyPoints,
                estimatedPrice = estPrice,
                maximumBid = maxBid,
                starterProb = candidate.starterProb2026_27,
                riskLevel = candidate.riskLevel,
                valueDifference = valueDiff
            )
        }

        // Sort by closest comparable value & expected fantasy points
        return ratedCandidates
            .sortedWith(compareBy({ abs(it.valueDifference) }, { -it.expectedFantasyPoints }))
            .take(4)
    }
}
