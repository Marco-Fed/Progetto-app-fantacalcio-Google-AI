package com.example.data.remote

import android.util.Log
import com.example.data.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class QuotazioneOnlineInfo(
    val playerName: String,
    val team: String,
    val role: Role,
    val quotation: Int,
    val fvm: Int
)

object FantacalcioWebService {
    private const val TAG = "FantacalcioWebService"
    private const val QUOTAZIONI_URL = "https://www.fantacalcio.it/quotazioni-fantacalcio"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetches complete official listone with current quotations and FVM.
     */
    suspend fun fetchFullListoneOnline(): List<QuotazioneOnlineInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<QuotazioneOnlineInfo>()
        try {
            val request = Request.Builder()
                .url(QUOTAZIONI_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                if (html.isNotBlank()) {
                    val parsed = parseQuotazioniHtml(html)
                    if (parsed.isNotEmpty()) {
                        Log.d(TAG, "Parsed ${parsed.size} players from online listone")
                        list.addAll(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online listone fetch error: ${e.message}")
        }
        return@withContext list
    }

    private fun parseQuotazioniHtml(html: String): List<QuotazioneOnlineInfo> {
        val list = mutableListOf<QuotazioneOnlineInfo>()
        try {
            val rowPattern = Pattern.compile("""<tr[^>]*data-player-row[^>]*>(.*?)<\/tr>""", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
            val matcher = rowPattern.matcher(html)

            while (matcher.find()) {
                val rowHtml = matcher.group(1) ?: continue

                val roleMatch = Regex("""class="player-role[^"]*"[^>]*>([PDCACR])<""", RegexOption.IGNORE_CASE).find(rowHtml)
                val roleStr = roleMatch?.groupValues?.get(1)?.uppercase() ?: "C"
                val role = when (roleStr) {
                    "P" -> Role.P
                    "D" -> Role.D
                    "C" -> Role.C
                    "A" -> Role.A
                    else -> Role.C
                }

                val nameMatch = Regex("""class="player-name[^"]*"[^>]*>(?:<a[^>]*>)?([^<]+)<""", RegexOption.IGNORE_CASE).find(rowHtml)
                val playerName = nameMatch?.groupValues?.get(1)?.trim() ?: continue

                val teamMatch = Regex("""class="player-team[^"]*"[^>]*>(?:<[^>]+>)?([^<]+)<""", RegexOption.IGNORE_CASE).find(rowHtml)
                val team = teamMatch?.groupValues?.get(1)?.trim() ?: "Serie A"

                val qtMatch = Regex("""class="player-classic-current-price[^"]*"[^>]*>(\d+)<""", RegexOption.IGNORE_CASE).find(rowHtml)
                val qt = qtMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                val fvmMatch = Regex("""class="player-classic-initial-price[^"]*"[^>]*>(\d+)<""", RegexOption.IGNORE_CASE).find(rowHtml)
                val fvm = fvmMatch?.groupValues?.get(1)?.toIntOrNull() ?: (qt * 2)

                list.add(
                    QuotazioneOnlineInfo(
                        playerName = playerName,
                        team = team,
                        role = role,
                        quotation = qt,
                        fvm = fvm
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing quotazioni table", e)
        }
        return list
    }
}
