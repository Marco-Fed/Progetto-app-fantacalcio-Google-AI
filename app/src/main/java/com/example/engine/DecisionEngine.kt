package com.example.engine

import com.example.data.model.*

object DecisionEngine {

    fun generateDecision(
        player: PlayerEntity,
        theoreticalValue: Double,
        marginalValue: Double,
        replacementValue: Double,
        scarcity: Double,
        rolePhase: RoleAuctionPhase,
        optimalPriceMin: Int,
        optimalPriceMax: Int,
        maximumBid: Int,
        userRoleSlotsRemaining: Int,
        userRemainingCredits: Int,
        maxAffordableBid: Int,
        alternatives: List<AlternativeComparable>,
        config: LeagueConfig
    ): Pair<DecisionType, DecisionReasons> {
        val buyReasons = mutableListOf<String>()
        val cautionReasons = mutableListOf<String>()

        // 1. Hard blocker checks
        if (userRoleSlotsRemaining <= 0) {
            val decision = DecisionType.DO_NOT_BID
            cautionReasons.add("Slot completati: La tua rosa ha già tutti i ${config.slotsForRole(player.role)} giocatori per il ruolo ${player.role.displayName}.")
            cautionReasons.add("Regola asta: Non puoi spendere crediti in un ruolo già saturo.")
            return Pair(
                decision,
                DecisionReasons(
                    buyReasons = listOf("Profilo valido, ma non tesserabile per esaurimento slot."),
                    cautionReasons = cautionReasons,
                    summaryRecommendation = "Non rilanciare: slot esauriti per ${player.role.displayName}."
                )
            )
        }

        if (maxAffordableBid < 1 || userRemainingCredits <= 1) {
            val decision = DecisionType.PASS
            cautionReasons.add("Budget insufficiente: Crediti residui ($userRemainingCredits) non consentono offerte competitive.")
            cautionReasons.add("Vincolo rosa: Necessario preservare almeno 1 credito per ciascuno slot vuoto rimasto.")
            return Pair(
                decision,
                DecisionReasons(
                    buyReasons = listOf("Giocatore di valore, ma matematicamente insostenibile."),
                    cautionReasons = cautionReasons,
                    summaryRecommendation = "Passa: budget insufficiente per completare la rosa."
                )
            )
        }

        val isUnavailable = player.status.equals("Infortunato", ignoreCase = true) ||
                player.status.equals("Squalificato", ignoreCase = true) ||
                player.status.equals("Indisponibile", ignoreCase = true) ||
                player.status.equals("Fuori rosa", ignoreCase = true)

        if (isUnavailable) {
            val decision = DecisionType.PASS
            cautionReasons.add("Stato giocatore: ${player.status} (${player.injuryNotes.ifBlank { "Indisponibile" }}).")
            if (player.expectedReturnDate.isNotBlank()) {
                cautionReasons.add("Rientro previsto: ${player.expectedReturnDate}.")
            }
            cautionReasons.add("Rischio elevato di minutaggio nullo o assenza prolungata.")
            return Pair(
                decision,
                DecisionReasons(
                    buyReasons = emptyList(),
                    cautionReasons = cautionReasons,
                    summaryRecommendation = "Non acquistare: giocatore attualmente non disponibile (${player.expectedReturnDate.ifBlank { player.status }})."
                )
            )
        }

        // 2. Build Multi-Factor Buy Reasons
        // [1] Titolarità prevista 2026-27
        if (player.starterProb2026_27 >= 85) {
            buyReasons.add("Titolarità prevista 2026-27 eccellente: ${player.starterProb2026_27}% garantito nell'undici titolare.")
        } else if (player.starterProb2026_27 >= 70) {
            buyReasons.add("Titolarità prevista 2026-27 affidabile: ${player.starterProb2026_27}% con buona continuità.")
        } else {
            buyReasons.add("Ballottaggio aperto (${player.starterProb2026_27}% stima titolarità): slot di rotazione o copertura.")
        }

        // [2] Expected Performance & Specialists
        val expPointsFormatted = String.format("%.2f", player.expectedFantasyPoints)
        if (player.isPenaltyTaker) {
            buyReasons.add("Specialista Rigori (gerarchia #${player.penaltyOrder}): FantaMedia attesa elevata ($expPointsFormatted).")
        } else if (player.isFreeKickTaker || player.isCornerTaker) {
            buyReasons.add("Incaricato piazzati/corner: bonus assist attesi frequenti (FM attesa: $expPointsFormatted).")
        } else if (player.expectedFantasyPoints >= 7.0) {
            buyReasons.add("Rendimento fanta-statistico top: FantaMedia attesa $expPointsFormatted.")
        } else {
            buyReasons.add("Rendimento solido e costante: FantaMedia attesa $expPointsFormatted.")
        }

        // [3] Role Auction Phase & Scarcity Context
        when (rolePhase) {
            RoleAuctionPhase.EARLY -> {
                buyReasons.add("Fase ${player.role.displayName}: EARLY. Opportunità di assicurarsi un cardine solido prima dell'inflazione di fine asta.")
            }
            RoleAuctionPhase.MID -> {
                if (scarcity >= 0.5) {
                    buyReasons.add("Fase ${player.role.displayName}: MID ad alta scarsità (${(scarcity * 100).toInt()}%). Le opzioni primarie iniziano a scarseggiare.")
                } else {
                    buyReasons.add("Fase ${player.role.displayName}: MID equilibrata. Rapporto qualità/prezzo favorevole.")
                }
            }
            RoleAuctionPhase.LATE -> {
                buyReasons.add("Fase ${player.role.displayName}: LATE (scarsità estrema). Uno degli ultimi titolari affidabili disponibili nel ruolo.")
            }
        }

        // [4] Value vs Alternatives
        if (marginalValue >= 10.0) {
            buyReasons.add("Marginal Value netto (+${String.format("%.1f", marginalValue)} pts): netto salto di qualità rispetto alle alternative libere.")
        } else if (marginalValue >= 4.0) {
            buyReasons.add("Vantaggio marginale positivo (+${String.format("%.1f", marginalValue)} pts) rispetto al replacement player.")
        } else {
            buyReasons.add("Valore di base stabile (replacement player di livello vicino a ${String.format("%.1f", replacementValue)} pts).")
        }

        // 3. Build Multi-Factor Caution Reasons
        cautionReasons.add("Prezzo massimo consigliato: non superare quota $maximumBid crediti per mantenere flessibilità negli altri ruoli.")
        
        if (alternatives.isNotEmpty()) {
            val bestAlt = alternatives.first()
            cautionReasons.add("Alternative valide nel ruolo: ${bestAlt.player.name} (${bestAlt.player.team}, stimato ~${bestAlt.estimatedPrice} crediti).")
        }

        val remainingBudgetAfterMax = userRemainingCredits - maximumBid
        cautionReasons.add("Costo opportunità: pagando il massimo resterebbero $remainingBudgetAfterMax crediti per gli altri ${userRoleSlotsRemaining - 1} slot.")

        if (player.riskLevel == RiskLevel.ALTO) {
            cautionReasons.add("Profilo ad alto rischio: possibile alternanza tattica o storico infortuni.")
        }

        // 4. Decision Synthesis
        val decision = when {
            maximumBid < optimalPriceMin -> {
                DecisionType.PASS
            }
            maximumBid >= optimalPriceMax && (player.expectedFantasyPoints >= 6.4 || player.starterProb2026_27 >= 70 || theoreticalValue >= 60.0) -> {
                DecisionType.BUY
            }
            maximumBid >= optimalPriceMin -> {
                DecisionType.BUY_IF_UNDER
            }
            player.starterProb2026_27 >= 55 -> {
                DecisionType.CONSIDER
            }
            else -> {
                DecisionType.PASS
            }
        }

        val summary = when (decision) {
            DecisionType.BUY -> "Acquisto fortemente consigliato entro il target $optimalPriceMin–$optimalPriceMax crediti (max $maximumBid)."
            DecisionType.BUY_IF_UNDER -> "Acquista se l'asta rimane sotto $optimalPriceMax crediti. Oltre $maximumBid il costo opportunità è sfavorevole."
            DecisionType.CONSIDER -> "Valuta l'andamento dei rilanci; ottimo acquisto se strappato sotto i $optimalPriceMin crediti."
            DecisionType.PASS, DecisionType.DO_NOT_BID -> "Lascia andare: sono disponibili alternative più efficienti per il tuo budget residuo."
        }

        return Pair(decision, DecisionReasons(buyReasons, cautionReasons, summary))
    }
}
