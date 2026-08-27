package com.example.data.model

enum class RoleAuctionPhase(val label: String, val description: String) {
    EARLY("EARLY", "Fase iniziale: abbondanza di alternative e costruzione base rosa"),
    MID("MID", "Fase intermedia: selezione mirata e calcolo costo opportunità"),
    LATE("LATE", "Fase finale: scarsità estrema, priorità copertura titolari")
}

enum class DecisionType(val label: String, val emoji: String, val colorHex: Long) {
    BUY("COMPRA / RILANCIA", "🟢", 0xFF2E7D32),
    BUY_IF_UNDER("COMPRA SE <= TARGET", "🟢", 0xFF388E3C),
    CONSIDER("VALUTA CON CAUTELA", "🟡", 0xFFF57F17),
    PASS("LASCIA ANDARE / PASSA", "🔴", 0xFFC62828),
    DO_NOT_BID("NON RILANCIARE", "🔴", 0xFFB71C1C)
}

data class DecisionReasons(
    val buyReasons: List<String>,
    val cautionReasons: List<String>,
    val summaryRecommendation: String
)

data class AlternativeComparable(
    val player: PlayerEntity,
    val expectedFantasyPoints: Double,
    val estimatedPrice: Int,
    val maximumBid: Int,
    val starterProb: Int,
    val riskLevel: RiskLevel,
    val valueDifference: Double // e.g. -0.8 vs target player
)

data class QuantitativeEvaluation(
    val player: PlayerEntity,
    val theoreticalValue: Double, // FantaValore 0-100
    val userMarginalValue: Double, // Incremental value for user roster
    val replacementValue: Double, // Value of best accessible alternative
    val scarcityIndex: Double, // 0.0 - 1.0 (0=abundant, 1=scarce)
    val roleAuctionPhase: RoleAuctionPhase,
    val roleInflationFactor: Double, // e.g. 1.15 = 15% inflation in this role
    val optimalPriceMin: Int,
    val optimalPriceMax: Int,
    val maximumBid: Int,
    val expectedAuctionPriceMin: Int,
    val expectedAuctionPriceMax: Int,
    val minimumRequiredBudgetAfterPurchase: Int,
    val winProbabilityMonteCarlo: Double, // 0.0 - 1.0
    val decision: DecisionType,
    val reasons: DecisionReasons,
    val alternatives: List<AlternativeComparable>,
    val risk: RiskLevel,
    val confidence: ConfidenceLevel,
    val hasSufficientAlternatives: Boolean
)
