package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val name: String,
    val isUserTeam: Boolean = false,
    val initialCredits: Int = 500,
    val remainingCredits: Int = 500,
    val purchasedCountP: Int = 0,
    val purchasedCountD: Int = 0,
    val purchasedCountC: Int = 0,
    val purchasedCountA: Int = 0,
    val spentCreditsP: Int = 0,
    val spentCreditsD: Int = 0,
    val spentCreditsC: Int = 0,
    val spentCreditsA: Int = 0,
    val aggressivenessFactor: Double = 1.0 // Learned / estimated bidding aggressiveness
) {
    val totalPurchased: Int get() = purchasedCountP + purchasedCountD + purchasedCountC + purchasedCountA
    val totalSpent: Int get() = spentCreditsP + spentCreditsD + spentCreditsC + spentCreditsA
    
    fun purchasedForRole(role: Role): Int = when (role) {
        Role.P -> purchasedCountP
        Role.D -> purchasedCountD
        Role.C -> purchasedCountC
        Role.A -> purchasedCountA
    }
    
    fun remainingSlotsForRole(role: Role, config: LeagueConfig): Int {
        val maxSlots = config.slotsForRole(role)
        return (maxSlots - purchasedForRole(role)).coerceAtLeast(0)
    }
    
    fun totalRemainingSlots(config: LeagueConfig): Int {
        return (config.totalSlots - totalPurchased).coerceAtLeast(0)
    }
    
    /**
     * Minimum credits strictly needed to purchase 1 credit per remaining empty slot.
     */
    fun minimumCompletionBudget(config: LeagueConfig): Int {
        return totalRemainingSlots(config).coerceAtLeast(1)
    }
    
    /**
     * Maximum price this team can legally bid on a single player while still leaving at least
     * 1 credit for every other unfilled slot.
     */
    fun maxAffordableBid(config: LeagueConfig): Int {
        val remainingSlots = totalRemainingSlots(config)
        if (remainingSlots <= 0) return 0
        val requiredForOtherSlots = (remainingSlots - 1).coerceAtLeast(0)
        return (remainingCredits - requiredForOtherSlots).coerceAtLeast(0)
    }
}

@Entity(tableName = "auction_events")
data class AuctionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playerId: String,
    val playerName: String,
    val playerRole: Role,
    val playerTeam: String,
    val buyerTeamName: String,
    val price: Int,
    val quotationAtTime: Int,
    val timestamp: Long = System.currentTimeMillis()
)

enum class WatchlistTag {
    TARGET, // Top target
    SLEEPER, // Scommessa
    AVOID, // Da evitare
    MUST_HAVE, // Imprescindibile
    PAIR // Coppia
}

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val playerId: String,
    val tag: WatchlistTag,
    val notes: String = "",
    val customMaxBid: Int? = null
)
