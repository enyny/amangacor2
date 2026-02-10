package com.JavHey

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

// --- DAFTAR SERVER (DoodStream Clones Only) ---
// Bysebuho DIHAPUS karena menggunakan proteksi F75s (Fingerprint) yang bikin error timeout.
class Hglink : JavHeyDood("https://hglink.to", "Hglink")
class Haxloppd : JavHeyDood("https://haxloppd.com", "Haxloppd")
class Minochinos : JavHeyDood("https://minochinos.com", "Minochinos")
class GoTv : JavHeyDood("https://go-tv.lol", "GoTv")

// --- LOGIKA UTAMA ---
open class JavHeyDood(override var mainUrl: String, override var name: String) : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Standarisasi URL (ubah /v/ menjadi /e/ agar dapat embed)
        val targetUrl = url.replace("/v/", "/e/")
        
        try {
            // 2. Ambil halaman Embed
            val responseReq = app.get(targetUrl, referer = "https://javhey.com/")
            val response = responseReq.text
            
            // 3. Dapatkan Domain Asli (PENTING untuk Haxloppd/Minochinos)
            val currentHost = "https://" + URI(responseReq.url).host

            // 4. Cari endpoint pass_md5
            val md5Pattern = Regex("""/pass_md5/[^']*""")
            val md5Match = md5Pattern.find(response)?.value

            if (md5Match != null) {
                val trueUrl = "$currentHost$md5Match"
                
                // 5. Request Token
                val tokenResponse = app.get(trueUrl, referer = targetUrl).text

                // 6. Buat String acak & URL Video
                val randomString = generateRandomString()
                val videoUrl = "$tokenResponse$randomString?token=${md5Match.substringAfterLast("/")}&expiry=${System.currentTimeMillis()}"

                // 7. Kirim link video
                M3u8Helper.generateM3u8(
                    name,
                    videoUrl,
                    targetUrl,
                    headers = mapOf("Origin" to currentHost)
                ).forEach(callback)
            } else {
                // Fallback: Cari redirect langsung
                val redirectMatch = Regex("""window\.location\.replace\('([^']*)'\)""").find(response)
                if (redirectMatch != null) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = redirectMatch.groupValues[1],
                            type = INFER_TYPE
                        ) {
                            this.referer = targetUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            // Cegah error satu server mematikan server lain
            e.printStackTrace()
        }
    }

    private fun generateRandomString(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
