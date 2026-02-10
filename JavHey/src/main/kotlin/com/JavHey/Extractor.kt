package com.JavHey

import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.newExtractorLink // PENTING: Import baru ini

class Hglink : Haxloppd() {
    override var mainUrl = "https://hglink.to"
    override var name = "Hglink"
}

open class Haxloppd : ExtractorApi() {
    override var mainUrl = "https://haxloppd.com"
    override var name = "Haxloppd"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (com.lagradost.cloudstream3.SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // 1. Tangani Redirect dari hglink.to ke haxloppd.com jika perlu
        var targetUrl = url
        if (url.contains("hglink.to")) {
            targetUrl = url.replace("hglink.to", "haxloppd.com")
        }

        // 2. Ambil halaman Embed
        val response = app.get(targetUrl, referer = "https://javhey.com/").text

        // 3. Cari endpoint pass_md5 (Kunci rahasia Doodstream)
        val md5Pattern = Regex("""/pass_md5/[^']*""")
        val md5Match = md5Pattern.find(response)?.value

        if (md5Match != null) {
            val trueUrl = "$mainUrl$md5Match"
            
            // 4. Request ke pass_md5 untuk mendapatkan Token Stream awal
            val tokenResponse = app.get(trueUrl, referer = targetUrl).text

            // 5. Buat String acak
            val randomString = generateRandomString()
            val videoUrl = "$tokenResponse$randomString?token=${md5Match.substringAfterLast("/")}&expiry=${System.currentTimeMillis()}"

            // 6. Dapatkan kualitas & kirim link (M3u8Helper aman digunakan)
            M3u8Helper.generateM3u8(
                name,
                videoUrl,
                targetUrl 
            ).forEach(callback)
        } else {
            // Fallback: Cari redirect langsung
            val redirectMatch = Regex("""window\.location\.replace\('([^']*)'\)""").find(response)
            if (redirectMatch != null) {
                // PERBAIKAN DI SINI: Menggunakan newExtractorLink
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
    }

    private fun generateRandomString(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10)
            .map { allowedChars.random() }
            .joinToString("")
    }
}
