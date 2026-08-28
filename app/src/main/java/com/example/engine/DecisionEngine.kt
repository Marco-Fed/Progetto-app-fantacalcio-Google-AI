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

        // 2. Build Multi-Factor Buy Reasons (Ranked with explicit tags [1], [2], [3])
        // [1] Titolarità & Continuità
        if (player.starterProb2026_27 >= 85) {
            buyReasons.add("[1] Titolarità prevista 2026-27 eccellente: ${player.starterProb2026_27}% garantito nell'undici titolare.")
        } else if (player.starterProb2026_27 >= 70) {
            buyReasons.add("[1] Titolarità prevista 2026-27 solida: ${player.starterProb2026_27}% con minutaggio continuo.")
        } else {
            buyReasons.add("[1] Rotazione / Ballottaggio (${player.starterProb2026_27}% titolarità): utile come copertura strategica.")
        }

        // [2] Rendimento fanta-statistico & Specialisti
        val expPointsFormatted = String.format("%.2f", player.expectedFantasyPoints)
        if (player.isPenaltyTaker) {
            buyReasons.add("[2] Specialista Rigori (gerarchia #${player.penaltyOrder}): FantaMedia attesa $expPointsFormatted con bonus pesanti.")
        } else if (player.isFreeKickTaker || player.isCornerTaker) {
            buyReasons.add("[2] Incaricato piazzati/corner: bonus assist attesi frequenti (FM attesa: $expPointsFormatted).")
        } else if (player.expectedFantasyPoints >= 7.0) {
            buyReasons.add("[2] Rendimento fanta-statistico top di reparto: FantaMedia attesa $expPointsFormatted.")
        } else {
            buyReasons.add("[2] Rendimento costante e affidabile: FantaMedia attesa $expPointsFormatted.")
        }

        // [3] Dinamica di Fase & Scarsità di Ruolo
        when (rolePhase) {
            RoleAuctionPhase.EARLY -> {
                buyReasons.add("[3] Fase ${player.role.displayName} (EARLY): opportunità di assicurarsi un top/semitop prima dell'inflazione di fine asta.")
            }
            RoleAuctionPhase.MID -> {
                if (scarcity >= 0.5) {
                    buyReasons.add("[3] Fase ${player.role.displayName} (MID - Scarsità ${(scarcity * 100).toInt()}%): slot primari in rapido esaurimento.")
                } else {
                    buyReasons.add("[3] Fase ${player.role.displayName} (MID): rapporto qualità/prezzo favorevole.")
                }
            }
            RoleAuctionPhase.LATE -> {
                buyReasons.add("[3] Fase ${player.role.displayName} (LATE - Scarsità ${(scarcity * 100).toInt()}%): uno degli ultimi titolari affidabili disponibili.")
            }
        }

        // [4] Valore Marginale vs Alternative
        if (marginalValue >= 10.0) {
            buyReasons.add("[4] Marginal Value netto (+${String.format("%.1f", marginalValue)} pts): netto salto di qualità rispetto alle alternative libere.")
        } else if (marginalValue >= 4.0) {
            buyReasons.add("[4] Vantaggio marginale positivo (+${String.format("%.1f", marginalValue)} pts) rispetto al replacement player.")
        } else {
            buyReasons.add("[4] Valore di base stabile (replacement player di livello a ${String.format("%.1f", replacementValue)} pts).")
        }

        // 3. Build Multi-Factor Caution Reasons (Ranked with explicit tags [1], [2], [3])
        cautionReasons.add("[1] Tetto di rilancio consigliato: non superare $maximumBid crediti per mantenere flessibilità negli altri ruoli.")
        
        if (alternatives.isNotEmpty()) {
            val bestAlt = alternatives.first()
            cautionReasons.add("[2] Alternativa comparabile disponibile: ${bestAlt.player.name} (${bestAlt.player.team}, stimato ~${bestAlt.estimatedPrice} crediti, max ${bestAlt.maximumBid}).")
        } else {
            cautionReasons.add("[2] Nessuna alternativa comparabile di pari livello disponibile nel ruolo.")
        }

        val remainingBudgetAfterMax = userRemainingCredits - maximumBid
        cautionReasons.add("[3] Impatto sul budget: dopo un eventuale acquisto a $maximumBid rimarrebbero $remainingBudgetAfterMax crediti per i restanti ${userRoleSlotsRemaining - 1} slot.")

        if (player.riskLevel == RiskLevel.ALTO) {
            cautionReasons.add("[4] Profilo ad alto rischio: possibile alternanza tattica o storico infortuni.")
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
            DecisionType.BUY_IF_UNDER -> "Acquista se l'asta rimane sotto $optimalPriceMax crediti (max $maximumBid). Oltre il costo opportunità è sfavorevole."
            DecisionType.CONSIDER -> "Valuta l'andamento dei rilanci; ottimo acquisto se strappato sotto i $optimalPriceMin crediti (max $maximumBid)."
            DecisionType.PASS, DecisionType.DO_NOT_BID -> "Lascia andare: supera il limite di convenienza rispetto alle alternative e al budget residuo."
        }

        return Pair(decision, DecisionReasons(buyReasons, cautionReasons, summary))
    }
}
