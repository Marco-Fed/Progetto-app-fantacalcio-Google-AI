package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class IndisponibileInfo(
    val playerName: String,
    val team: String,
    val injuryDescription: String,
    val expectedReturnDate: String
)

object InjuryParserService {
    private const val TAG = "InjuryParserService"

    const val DEFAULT_INJURIES_TEXT = """ATALANTA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Ahanor - Distrazione agli adduttori - Rientro fine agosto

Hien - Operato dopo lesione muscolare - Rientro inizio ottobre

Kristensen - Problema a una caviglia - Da valutare

Sulemana - Lesione al collaterale del ginocchio - Rientro metà ottobre

BOLOGNA
SQUALIFICATI: 

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Nessuno

CAGLIARI
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Idrissi - Lesione del legamento crociato anteriore - Rientro settembre/ottobre

COMO
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Addai - Rottura del tendine d'Achille - Rientro settembre/ottobre

FIORENTINA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Parisi - Lesione legamento crociato anteriore - Rientro novembre/dicembre

FROSINONE
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Nessuno

GENOA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Venturino - Operato al tendine rotuleo - Rientro inizio settembre

INTER
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Nessuno

JUVENTUS
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Ekhator - Lesione al bicipite femorale - Rientro fine agosto

Gatti - Problema alla caviglia - Da valutare

Vicario - Problema muscolare - Da valutare

Yildiz - Problema al piede - Da valutare

LAZIO
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Cataldi - Pubalgia - Rientro inizio settembre

Dele-Bashiru - Problema fisico - Da valutare

Marusic - Problema muscolare - Da valutare

LECCE
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Gallo - Problema allo zigomo - Da valutare

MILAN
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Gimenez - Distorsione alla caviglia - Rientro fine agosto/inizio settembre

Leao - Risentimento muscolare - Da valutare

MONZA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Pessina - Lesione alla rotula - Rientro fine ottobre-inizio novembre

NAPOLI
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI

Buongiorno - Infortunio al menisco - Rientro novembre 

Marianucci - Lesione di alto grado del collaterale mediale del ginocchio sinistro - Rientro metà ottobre

Marin - Problema fisico - Da valutare

PARMA
SQUALIFICATI

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Nicolussi Caviglia - Lesione di medio grado alla coscia - Rientro metà settembre

ROMA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Rensch - Affaticamento muscolare - Da valutare

Vaz - Lesione al collaterale - Rientro metà settembre

SASSUOLO
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Berardi - Sovraccarico alla caviglia - Rientro fine agosto-inizio settembre

Candé - Rottura del crociato - Rientro metà settembre

Koné - Frattura di tibia e perone - Rientro gennaio 2027

Pinamonti - Problema fisico - Da valutare

TORINO
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Comuzzo - Infortunio muscolare - Da valutare

Israel - Infortunio alla spalla - Rientro novembre-dicembre

UDINESE
SQUALIFICATI:

Kabasele - 1 giornata - Salta Monza (2ª)

DIFFIDATI:

Nessuno

INFORTUNATI:

Chakvetadze - Frattura al piede - Rientro fine agosto/inizio settembre

Gueye - Problema muscolare - Da valutare

Zanoli - Recupero dall'infortunio al crociato - Rientro metà-fine settembre

VENEZIA
SQUALIFICATI:

Nessuno

DIFFIDATI:

Nessuno

INFORTUNATI:

Adorante - Operato alla schiena - Rientro metà-fine ottobre

Sverko - Infortunio alle anche - Rientro metà-fine ottobre"""

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Parses the provided input text using LLM (Gemini) or robust local parser fallback.
     */
    suspend fun parseInjuriesFromText(text: String): List<IndisponibileInfo> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext emptyList()
        }

        // 1. Try Gemini LLM parsing first
        val llmResults = extractInjuriesWithGemini(trimmed)
        if (llmResults.isNotEmpty()) {
            Log.d(TAG, "Successfully extracted ${llmResults.size} indisponibili via Gemini LLM")
            return@withContext llmResults
        }

        // 2. Fallback to robust deterministic local text parser
        Log.d(TAG, "Using local parser for injury text")
        return@withContext parseInjuriesLocal(trimmed)
    }

    private suspend fun extractInjuriesWithGemini(inputText: String): List<IndisponibileInfo> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key not configured, will use local parser")
            return@withContext emptyList()
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt = """
                Sei un analista esperto di Serie A e Fantacalcio.
                Analizza attentamente il seguente testo fornito dall'utente contenente la situazione di infortuni, squalifiche e indisponibili per ciascuna squadra di Serie A.
                
                Estrai TUTTI i calciatori infortunati o squalificati e restituisci ESCLUSIVAMENTE un JSON array valido.
                Ogni oggetto deve avere questa struttura esatta:
                {
                  "playerName": "Cognome Nome o Cognome del giocatore",
                  "team": "Nome della squadra (es. Atalanta, Juventus, Milan, ecc.)",
                  "injuryDescription": "Descrizione esatta del problema fisico o squalifica",
                  "expectedReturnDate": "Data o periodo di rientro previsto (es. 'Rientro inizio ottobre', 'Da valutare', 'Prossima giornata')"
                }

                Regole:
                - Includi solo giocatori che hanno un infortunio o una squalifica effettiva (ignora i 'Nessuno' o chi è solo diffidato).
                - Se il giocatore è squalificato, inserisci come injuryDescription la squalifica (es. 'Squalifica 1 giornata') e come expectedReturnDate il rientro stimato.
                - Restituisci SOLO il JSON array, senza blocchi markdown né testo introduttivo.

                Testo da analizzare:
                $inputText
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val rootJson = JSONObject(responseBody)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val textResult = parts.getJSONObject(0).optString("text", "")
                        return@withContext parseJsonResult(textResult)
                    }
                }
            } else {
                Log.w(TAG, "Gemini API call failed with code: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking Gemini LLM for injuries text parsing", e)
        }
        return@withContext emptyList()
    }

    private fun parseJsonResult(jsonText: String): List<IndisponibileInfo> {
        val list = mutableListOf<IndisponibileInfo>()
        try {
            val clean = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val array = JSONArray(clean)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pName = obj.optString("playerName", "").trim()
                val team = obj.optString("team", "").trim()
                val desc = obj.optString("injuryDescription", "").trim()
                val ret = obj.optString("expectedReturnDate", "").trim()
                if (pName.isNotBlank() && !pName.equals("Nessuno", ignoreCase = true)) {
                    list.add(
                        IndisponibileInfo(
                            playerName = pName,
                            team = team,
                            injuryDescription = desc,
                            expectedReturnDate = ret
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON result from LLM", e)
        }
        return list
    }

    /**
     * Local deterministic parser for user input text.
     */
    fun parseInjuriesLocal(text: String): List<IndisponibileInfo> {
        val list = mutableListOf<IndisponibileInfo>()
        val lines = text.lines()
        var currentTeam = "Serie A"
        var currentSection = "" // "SQUALIFICATI", "DIFFIDATI", "INFORTUNATI"

        val serieATeams = listOf(
            "ATALANTA", "BOLOGNA", "CAGLIARI", "COMO", "EMPOLI", "FIORENTINA", "FROSINONE",
            "GENOA", "HELLAS VERONA", "VERONA", "INTER", "JUVENTUS", "LAZIO", "LECCE",
            "MILAN", "MONZA", "NAPOLI", "PARMA", "PISA", "ROMA", "SASSUOLO", "TORINO", "UDINESE", "VENEZIA"
        )

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            val upper = line.uppercase()
            val matchedTeam = serieATeams.firstOrNull { upper == it || upper == "$it:" }
            if (matchedTeam != null) {
                currentTeam = matchedTeam.lowercase().replaceFirstChar { it.uppercase() }
                currentSection = ""
                continue
            }

            if (upper.startsWith("SQUALIFICATI")) {
                currentSection = "SQUALIFICATI"
                continue
            } else if (upper.startsWith("DIFFIDATI")) {
                currentSection = "DIFFIDATI"
                continue
            } else if (upper.startsWith("INFORTUNATI")) {
                currentSection = "INFORTUNATI"
                continue
            }

            if (line.equals("Nessuno", ignoreCase = true)) {
                continue
            }

            // Ignore diffidati as they are not currently injured or suspended
            if (currentSection == "DIFFIDATI") {
                continue
            }

            if (currentSection == "SQUALIFICATI") {
                // Line format: Kabasele - 1 giornata - Salta Monza (2ª)
                val parts = line.split(Regex("[-–]")).map { it.trim() }
                if (parts.isNotEmpty()) {
                    val name = parts[0]
                    val reason = if (parts.size > 1) "Squalificato (${parts.drop(1).joinToString(" - ")})" else "Squalificato per 1 giornata"
                    val returnDate = "Prossima giornata"
                    list.add(
                        IndisponibileInfo(
                            playerName = name,
                            team = currentTeam,
                            injuryDescription = reason,
                            expectedReturnDate = returnDate
                        )
                    )
                }
            } else if (currentSection == "INFORTUNATI" || currentSection == "") {
                // Line format: Ahanor - Distrazione agli adduttori - Rientro fine agosto
                val parts = line.split(Regex("[-–]")).map { it.trim() }
                if (parts.size >= 2) {
                    val name = parts[0]
                    val desc = parts[1]
                    val returnDate = if (parts.size >= 3) parts[2] else "Da valutare"
                    if (name.length in 2..35 && !name.contains("SQUALIFICATI", ignoreCase = true)) {
                        list.add(
                            IndisponibileInfo(
                                playerName = name,
                                team = currentTeam,
                                injuryDescription = desc,
                                expectedReturnDate = returnDate
                            )
                        )
                    }
                }
            }
        }
        return list
    }
}
