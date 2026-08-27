package com.example.engine

import com.example.data.model.*
import kotlin.random.Random

object MonteCarloSimulation {

    data class SimulationResult(
        val player: PlayerEntity,
        val simulatedRounds: Int,
        val winRateWithPlayer: Double, // % of simulated auctions where buying X yielded higher final roster score
        val avgFinalRosterPointsWithPlayer: Double,
        val avgFinalRosterPointsWithoutPlayer: Double,
        val executionTimeMs: Long,
        val mode: String // "LIVE" or "DEEP"
    )

    /**
     * Fast Monte Carlo Simulation:
     * Simulates completion of the auction under two branches:
     * Branch A: User purchases the target player at current bid price.
     * Branch B: User passes on the player and purchases the best remaining alternatives with remaining budget.
     *
     * Highly optimized for execution under 100-300ms in LIVE mode, < 1.5s in DEEP mode.
     */
    fun runSimulation(
        player: PlayerEntity,
        bidPrice: Int,
        userTeam: TeamEntity,
        allTeams: List<TeamEntity>,
        availablePlayers: List<PlayerEntity>,
        config: LeagueConfig,
        isDeepMode: Boolean = false
    ): SimulationResult {
        val startTime = System.currentTimeMillis()
        val numSimulations = if (isDeepMode) 500 else 100

        val remainingAvailable = availablePlayers.filter { !it.isPurchased && it.id != player.id }
        val role = player.role
        val userNeededSlots = userTeam.remainingSlotsForRole(role, config)

        if (userNeededSlots <= 0 || userTeam.remainingCredits < bidPrice) {
            val elapsed = System.currentTimeMillis() - startTime
            return SimulationResult(
                player = player,
                simulatedRounds = 1,
                winRateWithPlayer = 0.0,
                avgFinalRosterPointsWithPlayer = 0.0,
                avgFinalRosterPointsWithoutPlayer = 0.0,
                executionTimeMs = elapsed,
                mode = if (isDeepMode) "DEEP" else "LIVE"
            )
        }

        var winsWithPlayer = 0
        var totalPointsWith = 0.0
        var totalPointsWithout = 0.0

        val availableByRole = Role.values().associateWith { r ->
            remainingAvailable.filter { it.role == r }
        }

        val random = Random(42)

        for (sim in 0 until numSimulations) {
            // Branch A: With Player
            val budgetWith = userTeam.remainingCredits - bidPrice
            val scoreWith = player.expectedFantasyPoints + simulateRemainingRoster(
                currentRole = role,
                filledInCurrentRole = 1,
                remainingBudget = budgetWith,
                userTeam = userTeam,
                availableByRole = availableByRole,
                config = config,
                random = random
            )

            // Branch B: Without Player (Pass)
            val budgetWithout = userTeam.remainingCredits
            val scoreWithout = simulateRemainingRoster(
                currentRole = role,
                filledInCurrentRole = 0,
                remainingBudget = budgetWithout,
                userTeam = userTeam,
                availableByRole = availableByRole,
                config = config,
                random = random
            )

            totalPointsWith += scoreWith
            totalPointsWithout += scoreWithout

            if (scoreWith >= scoreWithout) {
                winsWithPlayer++
            }
        }

        val winRate = winsWithPlayer.toDouble() / numSimulations.toDouble()
        val avgWith = totalPointsWith / numSimulations
        val avgWithout = totalPointsWithout / numSimulations
        val elapsed = System.currentTimeMillis() - startTime

        return SimulationResult(
            player = player,
            simulatedRounds = numSimulations,
            winRateWithPlayer = Math.round(winRate * 100.0) / 100.0,
            avgFinalRosterPointsWithPlayer = Math.round(avgWith * 100.0) / 100.0,
            avgFinalRosterPointsWithoutPlayer = Math.round(avgWithout * 100.0) / 100.0,
            executionTimeMs = elapsed,
            mode = if (isDeepMode) "DEEP" else "LIVE"
        )
    }

    private fun simulateRemainingRoster(
        currentRole: Role,
        filledInCurrentRole: Int,
        remainingBudget: Int,
        userTeam: TeamEntity,
        availableByRole: Map<Role, List<PlayerEntity>>,
        config: LeagueConfig,
        random: Random
    ): Double {
        var totalExpectedPts = 0.0
        var currentBudget = remainingBudget

        for (role in Role.values()) {
            val initialPurchased = userTeam.purchasedForRole(role)
            val alreadyFilled = if (role == currentRole) filledInCurrentRole else 0
            val totalFilled = initialPurchased + alreadyFilled
            val needed = (config.slotsForRole(role) - totalFilled).coerceAtLeast(0)

            val rolePool = availableByRole[role] ?: emptyList()
            if (rolePool.isEmpty() || needed == 0) continue

            // Pick realistic players based on available budget per slot
            val budgetPerSlot = if (needed > 0) (currentBudget / needed).coerceAtLeast(1) else 1

            for (i in 0 until needed) {
                // Approximate available pick with small random noise
                val viablePicks = rolePool.filter { it.fvm <= (budgetPerSlot * 1.5) + 2 }
                val chosen = if (viablePicks.isNotEmpty()) {
                    viablePicks[random.nextInt(viablePicks.size)]
                } else {
                    rolePool.minByOrNull { it.fvm } ?: rolePool.first()
                }

                totalExpectedPts += chosen.expectedFantasyPoints
                currentBudget = (currentBudget - (chosen.fvm / 2).coerceAtLeast(1)).coerceAtLeast(0)
            }
        }

        return totalExpectedPts
    }
}
