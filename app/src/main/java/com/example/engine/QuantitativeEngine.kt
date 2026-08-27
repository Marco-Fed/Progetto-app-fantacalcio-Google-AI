package com.example.engine

import com.example.data.model.*
import com.example.data.remote.KaggleHistoricalStatsService
import kotlin.math.*

object QuantitativeEngine {

    /**
     * Determines the specific auction phase for a given role: EARLY, MID, LATE.
     * Evaluates filled slots across the league, quality of remaining players, and scarcity.
     */
    fun determineRoleAuctionPhase(
        role: Role,
        availablePlayersInRole: List<PlayerEntity>,
        allTeams: List<TeamEntity>,
        config: LeagueConfig
    ): RoleAuctionPhase {
        val totalSlotsInLeague = allTeams.size * config.slotsForRole(role)
        if (totalSlotsInLeague <= 0) return RoleAuctionPhase.EARLY

        val filledSlotsInLeague = allTeams.sumOf { it.purchasedForRole(role) }
        val fillRatio = filledSlotsInLeague.toDouble() / totalSlotsInLeague.toDouble()

        val qualityAvailableCount = availablePlayersInRole.count { it.starterProb2026_27 >= 70 && it.expectedFantasyPoints >= 6.0 }

        return when {
            fillRatio < 0.35 && qualityAvailableCount >= 5 -> RoleAuctionPhase.EARLY
            fillRatio >= 0.75 || qualityAvailableCount <= 2 -> RoleAuctionPhase.LATE
            else -> RoleAuctionPhase.MID
        }
    }

    /**
     * Calculates the Scarcity Index (0.0 to 1.0) for a role.
     * 1.0 means extreme scarcity (very few viable players left for many empty slots).
     */
    fun calculateScarcityIndex(
        role: Role,
        availablePlayersInRole: List<PlayerEntity>,
        allTeams: List<TeamEntity>,
        config: LeagueConfig
    ): Double {
        val neededSlotsInLeague = allTeams.sumOf { it.remainingSlotsForRole(role, config) }
        if (neededSlotsInLeague <= 0) return 0.0

        val qualityAvailable = availablePlayersInRole.count { it.starterProb2026_27 >= 65 }
        if (qualityAvailable == 0) return 1.0

        val ratio = qualityAvailable.toDouble() / neededSlotsInLeague.toDouble()
        // Ratio < 0.5 is very scarce, > 1.5 is abundant
        val scarcity = (1.5 - ratio).coerceIn(0.0, 1.0)
        return Math.round(scarcity * 100.0) / 100.0
    }

    /**
     * Calculates observed inflation for a specific role based on completed auction events.
     */
    fun calculateRoleInflation(
        role: Role,
        events: List<AuctionEventEntity>
    ): Double {
        val roleEvents = events.filter { it.playerRole == role && it.quotationAtTime > 0 }
        if (roleEvents.size < 2) return 1.0 // Normal / neutral

        val totalPaid = roleEvents.sumOf { it.price }
        val totalQuotation = roleEvents.sumOf { it.quotationAtTime }
        if (totalQuotation == 0) return 1.0

        // Ratio of paid vs base reference quotation (with reasonable cap)
        val rawInflation = totalPaid.toDouble() / (totalQuotation * 1.8).toDouble()
        return (rawInflation).coerceIn(0.75, 1.45)
    }

    /**
     * Calculates historical reliability modifier from real FBref/Kaggle datasets (2024-25 & 2025-26).
     * If historical statistics are missing, returns neutral 0.0 so other criteria have full priority.
     */
    fun calculateHistoricalModifier(player: PlayerEntity): Double {
        val s25 = KaggleHistoricalStatsService.parseStats(player.stats2025_26Json)
        val s24 = KaggleHistoricalStatsService.parseStats(player.stats2024_25Json)

        if (s25 == null && s24 == null) {
            // Neutral fallback: no historical penalty, relies purely on FVM, Quotazione and Forecast
            return 0.0
        }

        var modifier = 0.0
        val validSeasons = listOfNotNull(s25, s24).filter { it.appearances > 0 }
        if (validSeasons.isEmpty()) return 0.0

        val avgStarterPct = validSeasons.map { it.starterPercentage }.average()
        val totalApps = validSeasons.sumOf { it.appearances }
        val totalStarts = validSeasons.sumOf { it.starterAppearances }
        val totalGoals = validSeasons.sumOf { it.goals }
        val totalAssists = validSeasons.sumOf { it.assists }
        val totalYellow = validSeasons.sumOf { it.yellowCards }
        val totalRed = validSeasons.sumOf { it.redCards }
        val totalCleanSheets = validSeasons.sumOf { it.cleanSheets }

        // 1. Proven Starter Continuity in top 5 leagues
        if (avgStarterPct >= 80 && totalApps >= 25) {
            modifier += 0.35
        } else if (avgStarterPct >= 65 && totalApps >= 18) {
            modifier += 0.15
        } else if (avgStarterPct < 35 && totalApps >= 15) {
            modifier -= 0.20
        }

        // 2. Goal / Assist / Offensive production by role
        when (player.role) {
            Role.A -> {
                if (totalGoals >= 20) modifier += 0.40
                else if (totalGoals >= 12) modifier += 0.20
                if (totalAssists >= 8) modifier += 0.15
            }
            Role.C -> {
                if (totalGoals >= 8) modifier += 0.30
                if (totalAssists >= 8) modifier += 0.20
            }
            Role.D -> {
                if (totalGoals >= 3) modifier += 0.20
                if (totalAssists >= 4) modifier += 0.15
            }
            Role.P -> {
                if (totalCleanSheets >= 16) modifier += 0.35
                else if (totalCleanSheets >= 10) modifier += 0.15
            }
        }

        // 3. Discipline penalty if very high cards
        if (totalRed >= 2 || totalYellow >= 14) {
            modifier -= 0.15
        }

        return modifier.coerceIn(-0.40, 0.60)
    }

    /**
     * Evaluates theoretical player score (0 to 100).
     */
    fun calculateTheoreticalValue(player: PlayerEntity, config: LeagueConfig): Double {
        val basePoints = player.expectedFantasyPoints // e.g. 8.65 for Lautaro
        val starterFactor = (player.starterProb2026_27.toDouble() / 100.0).coerceIn(0.2, 1.0)
        val historicalMod = calculateHistoricalModifier(player)
        
        var bonus = 0.0 + historicalMod
        if (player.isPenaltyTaker) {
            bonus += if (player.penaltyOrder == 1) 0.6 else 0.3
        }
        if (player.isFreeKickTaker) bonus += 0.2
        if (player.isCornerTaker) bonus += 0.15

        // Defense modifier impact on defenders & goalkeepers
        if (config.defenseModifierEnabled) {
            if (player.role == Role.D && player.expectedFantasyPoints >= 6.3) {
                bonus += 0.4
            } else if (player.role == Role.P && player.team in listOf("Inter", "Juventus", "Napoli", "Milan", "Atalanta")) {
                bonus += 0.35
            }
        }

        // Scale into 0..100 domain based on role expectations
        val rawScore = when (player.role) {
            Role.P -> (basePoints - 4.5 + bonus) * 35.0
            Role.D -> (basePoints - 5.5 + bonus) * 38.0
            Role.C -> (basePoints - 5.8 + bonus) * 32.0
            Role.A -> (basePoints - 6.0 + bonus) * 28.0
        }

        val weightedScore = (rawScore * (0.6 + 0.4 * starterFactor)).coerceIn(10.0, 99.0)
        return Math.round(weightedScore * 10.0) / 10.0
    }

    /**
     * Finds the replacement value: theoretical score of the best alternative player
     * realistically available for the user in this role.
     */
    fun calculateReplacementValue(
        targetPlayer: PlayerEntity,
        availableInRole: List<PlayerEntity>,
        config: LeagueConfig
    ): Double {
        val otherPlayers = availableInRole.filter { it.id != targetPlayer.id && it.starterProb2026_27 >= 60 }
        if (otherPlayers.isEmpty()) return 40.0 // Baseline floor

        // Rank by theoretical value
        val sorted = otherPlayers.map { calculateTheoreticalValue(it, config) }.sortedDescending()
        // Take the 2nd or 3rd best available as the true opportunity cost replacement
        return if (sorted.size >= 3) sorted[2] else sorted.first()
    }

    /**
     * Computes the dynamic optimal price range and maximum recommended bid for a player.
     */
    fun evaluatePlayer(
        player: PlayerEntity,
        userTeam: TeamEntity,
        allTeams: List<TeamEntity>,
        availablePlayers: List<PlayerEntity>,
        events: List<AuctionEventEntity>,
        config: LeagueConfig
    ): QuantitativeEvaluation {
        val role = player.role
        val availableInRole = availablePlayers.filter { it.role == role }
        val rolePhase = determineRoleAuctionPhase(role, availableInRole, allTeams, config)
        val scarcity = calculateScarcityIndex(role, availableInRole, allTeams, config)
        val roleInflation = calculateRoleInflation(role, events)
        
        val theoreticalVal = calculateTheoreticalValue(player, config)
        val replacementVal = calculateReplacementValue(player, availableInRole, config)
        val marginalVal = (theoreticalVal - replacementVal).coerceAtLeast(0.0)

        // User budget constraints
        val userRemainingSlots = userTeam.totalRemainingSlots(config)
        val userRoleSlotsRemaining = userTeam.remainingSlotsForRole(role, config)
        val maxAffordableBid = userTeam.maxAffordableBid(config)
        val minRequiredBudget = (userRemainingSlots - 1).coerceAtLeast(0) * 1

        // Base price estimation from FVM & theoretical value scaled to league initial credits
        // Standard Italian Fantacalcio FVM is expressed on a 1000-credit benchmark (e.g. 414/1000 = 41.4% of budget).
        val effectiveFvm = if (player.fvm > 0) player.fvm else (player.quotation * 20).coerceAtLeast(1)
        val budgetShare = (effectiveFvm.toDouble() / 1000.0).coerceIn(0.001, 0.70)
        val baseFvmPrice = (budgetShare * config.initialCredits * roleInflation).coerceAtLeast(1.0)

        // Price modulation by role auction phase & marginal value
        val phaseMultiplier = when (rolePhase) {
            RoleAuctionPhase.EARLY -> 0.95 + (theoreticalVal / 200.0) // Value absolute quality
            RoleAuctionPhase.MID -> 1.0 + (marginalVal / 100.0) * 0.4
            RoleAuctionPhase.LATE -> 1.05 + (scarcity * 0.25) // High scarcity premium
        }

        val estimatedCenterPrice = (baseFvmPrice * phaseMultiplier).roundToInt().coerceAtLeast(1)

        // Optimal interval: [estimated - 10%, estimated + 5%]
        val optMin = (estimatedCenterPrice * 0.90).roundToInt().coerceAtLeast(1)
        val optMax = (estimatedCenterPrice * 1.05).roundToInt().coerceAtLeast(optMin)

        // Maximum recommended bid: beyond this, passing and saving budget for alternatives is optimal
        val maxRecBidCalculated = (estimatedCenterPrice * (1.18 + scarcity * 0.12)).roundToInt()
        val finalMaxBid = min(maxRecBidCalculated, maxAffordableBid).coerceAtLeast(1)

        // Expected auction price range
        val expPriceMin = (estimatedCenterPrice * 0.95).roundToInt().coerceAtLeast(1)
        val expPriceMax = (estimatedCenterPrice * 1.15).roundToInt().coerceAtLeast(expPriceMin)

        // Fast heuristic Monte Carlo win probability
        val mcWinProb = calculateFastMonteCarloProbability(
            targetPrice = estimatedCenterPrice,
            player = player,
            userTeam = userTeam,
            allTeams = allTeams,
            config = config,
            rolePhase = rolePhase
        )

        // Generate Alternatives
        val alternatives = AlternativeEngine.findAlternatives(
            targetPlayer = player,
            availableInRole = availableInRole,
            userTeam = userTeam,
            config = config
        )

        // Decision logic & Reasons
        val (decision, reasons) = DecisionEngine.generateDecision(
            player = player,
            theoreticalValue = theoreticalVal,
            marginalValue = marginalVal,
            replacementValue = replacementVal,
            scarcity = scarcity,
            rolePhase = rolePhase,
            optimalPriceMin = optMin,
            optimalPriceMax = optMax,
            maximumBid = finalMaxBid,
            userRoleSlotsRemaining = userRoleSlotsRemaining,
            userRemainingCredits = userTeam.remainingCredits,
            maxAffordableBid = maxAffordableBid,
            alternatives = alternatives,
            config = config
        )

        return QuantitativeEvaluation(
            player = player,
            theoreticalValue = theoreticalVal,
            userMarginalValue = Math.round(marginalVal * 10.0) / 10.0,
            replacementValue = Math.round(replacementVal * 10.0) / 10.0,
            scarcityIndex = scarcity,
            roleAuctionPhase = rolePhase,
            roleInflationFactor = Math.round(roleInflation * 100.0) / 100.0,
            optimalPriceMin = optMin,
            optimalPriceMax = optMax,
            maximumBid = finalMaxBid,
            expectedAuctionPriceMin = expPriceMin,
            expectedAuctionPriceMax = expPriceMax,
            minimumRequiredBudgetAfterPurchase = minRequiredBudget,
            winProbabilityMonteCarlo = mcWinProb,
            decision = decision,
            reasons = reasons,
            alternatives = alternatives,
            risk = player.riskLevel,
            confidence = player.confidenceLevel,
            hasSufficientAlternatives = alternatives.size >= 3
        )
    }

    private fun calculateFastMonteCarloProbability(
        targetPrice: Int,
        player: PlayerEntity,
        userTeam: TeamEntity,
        allTeams: List<TeamEntity>,
        config: LeagueConfig,
        rolePhase: RoleAuctionPhase
    ): Double {
        val opponents = allTeams.filter { !it.isUserTeam }
        if (opponents.isEmpty()) return 0.95

        val competingOpponents = opponents.filter { opp ->
            val needsRole = opp.remainingSlotsForRole(player.role, config) > 0
            val canAfford = opp.maxAffordableBid(config) >= targetPrice
            needsRole && canAfford
        }

        if (competingOpponents.isEmpty()) return 0.92

        // Estimate competition aggressiveness
        val avgCompetitorCredits = competingOpponents.map { it.remainingCredits }.average()
        val creditRatio = userTeam.remainingCredits.toDouble() / (avgCompetitorCredits + 1.0)
        
        val baseProb = when {
            creditRatio > 1.4 -> 0.85
            creditRatio > 1.0 -> 0.72
            creditRatio > 0.7 -> 0.55
            else -> 0.35
        }

        val phaseBonus = if (rolePhase == RoleAuctionPhase.EARLY) 0.05 else -0.05
        return (baseProb + phaseBonus).coerceIn(0.15, 0.95)
    }
}
