package com.JavHey

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.network.WebViewResolver
import org.jsoup.nodes.Document
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ==========================================
// 1. MANAJER UTAMA (JAVHEY EXTRACTOR) - SMART FILTER
// ==========================================
object JavHeyExtractor {
    suspend fun invoke(
        document: Document,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val uniqueUrls = mutableSetOf<String>()
        // Set untuk mencatat provider mana yang SUDAH diambil
        val registeredProviders = mutableSetOf<String>()

        // 1. Ambil Link dari Input Base64
        try {
            val hiddenInput = document.selectFirst("input#links")
            val hiddenLinksEncrypted = hiddenInput?.attr("value")
            if (!hiddenLinksEncrypted.isNullOrEmpty()) {
                val decodedBytes = Base64.getDecoder().decode(hiddenLinksEncrypted)
                val urls = String(decodedBytes).split(",,,")
                urls.forEach { sourceUrl ->
                    val cleanUrl = sourceUrl.trim()
                    if (cleanUrl.startsWith("http")) uniqueUrls.add(cleanUrl)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Ambil Link dari Tombol Download
        try {
            document.select("div.links-download a").forEach { linkTag ->
                val downloadUrl = linkTag.attr("href").trim()
                if (downloadUrl.startsWith("http")) uniqueUrls.add(downloadUrl)
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 3. PROSES LINK DENGAN FILTER "SATU NAMA SAJA"
        uniqueUrls.forEach { url ->
            val providerTag = getProviderTag(url)
            
            // Jika provider ini (misal VidHide) BELUM ada di daftar, maka muat.
            // Jika SUDAH ada, lewati (skip) agar tidak duplikat di menu Sumber.
            if (!registeredProviders.contains(providerTag)) {
                
                // Tandai provider ini sudah diambil
                // Catatan: Jika tag-nya "Unknown", kita selalu izinkan (jangan di-filter)
                if (providerTag != "Unknown") {
                    registeredProviders.add(providerTag)
                }

                loadExtractor(url, subtitleCallback, callback)
            }
        }
    }

    // Fungsi untuk mengelompokkan URL berdasarkan Keluarga Server
    private fun getProviderTag(url: String): String {
        val u = url.lowercase()
        return when {
            // Keluarga VidHidePro
            u.contains("vidhide") || u.contains("filelions") || u.contains("kinoger.be") -> "VidHide"
            
            // Keluarga EarnVids
            u.contains("smoothpre") || u.contains("dhtpre") || u.contains("peytonepre") -> "EarnVids"
            
            // Keluarga MixDrop
            u.contains("mixdrop") -> "MixDrop"
            
            // Keluarga Swdyu (Varian Streamwish tapi ingin nama beda)
            u.contains("swdyu") -> "Swdyu"
            
            // Keluarga StreamWish (Umum)
            u.contains("streamwish") || u.contains("mwish") || u.contains("dwish") || 
            u.contains("wishembed") || u.contains("wishfast") || u.contains("jodwish") ||
            u.contains("swhoi") || u.contains("awish") -> "StreamWish"
            
            // Keluarga DoodStream
            u.contains("dood") || u.contains("ds2play") || u.contains("ds2video") || u.contains("dooood") -> "DoodStream"
            
            // Keluarga LuluStream
            u.contains("lulustream") || u.contains("luluvdo") || u.contains("kinoger.pw") -> "LuluStream"
            
            // Keluarga Byse (Hanya untuk jaga-jaga)
            u.contains("byse") -> "Byse"
            
            else -> "Unknown" // Biarkan lolos jika tidak dikenali
        }
    }
}

// ==========================================
// 2. DAPUR VIDHIDE / FILELIONS
// ==========================================
class VidHidePro1 : VidHidePro() { override var mainUrl = "https://filelions.live" }
class VidHidePro2 : VidHidePro() { override var mainUrl = "https://filelions.online" }
class VidHidePro3 : VidHidePro() { override var mainUrl = "https://filelions.to" }
class VidHidePro4 : VidHidePro() { override val mainUrl = "https://kinoger.be" }
class VidHidePro5 : VidHidePro() { override val mainUrl = "https://vidhidevip.com" }
class VidHidePro6 : VidHidePro() { override val mainUrl = "https://vidhidepre.com" }
// EarnVids
class Smoothpre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://smoothpre.com" }
class Dhtpre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://dhtpre.com" }
class Peytonepre : VidHidePro() { override var name = "EarnVids"; override var mainUrl = "https://peytonepre.com" }

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf("Origin" to mainUrl, "User-Agent" to USER_AGENT)
        val response = app.get(getEmbedUrl(url), referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            var result = getAndUnpack(response.text)
            if(result.contains("var links")) result = result.substringAfter("var links")
            result
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return

        Regex(":\\s*\"(.*?m3u8.*?)\"").findAll(script).forEach { m3u8Match ->
            generateM3u8(name, fixUrl(m3u8Match.groupValues[1]), referer = "$mainUrl/", headers = headers).forEach(callback)
        }
    }
    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}

// ==========================================
// 3. DAPUR MIXDROP
// ==========================================
class MixDropBz : MixDrop(){ override var mainUrl = "https://mixdrop.bz" }
class MixDropAg : MixDrop(){ override var mainUrl = "https://mixdrop.ag" }
class MixDropCh : MixDrop(){ override var mainUrl = "https://mixdrop.ch" }
class MixDropTo : MixDrop(){ override var mainUrl = "https://mixdrop.to" }

open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    private val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
    override val requiresReferer = false
    override fun getExtractorUrl(id: String): String = "$mainUrl/e/$id"
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        with(app.get(url.replaceFirst("/f/", "/e/"))) {
            getAndUnpack(this.text).let { unpackedText ->
                srcRegex.find(unpackedText)?.groupValues?.get(1)?.let { link ->
                    return listOf(newExtractorLink(name, name, httpsify(link)) {
                        this.referer = url
                        this.quality = Qualities.Unknown.value
                    })
                }
            }
        }
        return null
    }
}

// ==========================================
// 4. DAPUR STREAMWISH
// ==========================================
class Mwish : StreamWishExtractor() { override val name = "Mwish"; override val mainUrl = "https://mwish.pro" }
class Dwish : StreamWishExtractor() { override val name = "Dwish"; override val mainUrl = "https://dwish.pro" }
class Streamwish2 : StreamWishExtractor() { override val mainUrl = "https://streamwish.site" }
class WishembedPro : StreamWishExtractor() { override val name = "Wishembed"; override val mainUrl = "https://wishembed.pro" }
class Wishfast : StreamWishExtractor() { override val name = "Wishfast"; override val mainUrl = "https://wishfast.top" }
// Swdyu (Varian Streamwish tapi dipisah namanya sesuai screenshot)
class Swdyu : StreamWishExtractor() { override val name = "Swdyu"; override val mainUrl = "https://swdyu.com" }

open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val headers = mapOf("Origin" to "$mainUrl/", "User-Agent" to USER_AGENT)
        val pageResponse = app.get(resolveEmbedUrl(url), referer = referer)
        val playerScriptData = when {
            !getPacked(pageResponse.text).isNullOrEmpty() -> getAndUnpack(pageResponse.text)
            pageResponse.document.select("script").any { it.html().contains("jwplayer(\"vplayer\").setup(") } ->
                pageResponse.document.select("script").firstOrNull { it.html().contains("jwplayer(\"vplayer\").setup(") }?.html()
            else -> pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val directStreamUrl = playerScriptData?.let { Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1) }

        if (!directStreamUrl.isNullOrEmpty()) {
            generateM3u8(name, directStreamUrl, mainUrl, headers = headers).forEach(callback)
        } else {
            val webViewM3u8Resolver = WebViewResolver( Regex("""txt|m3u8"""), listOf(Regex("""txt|m3u8""")), false, 15_000L )
            val interceptedStreamUrl = app.get(url, referer = referer, interceptor = webViewM3u8Resolver).url
            if (interceptedStreamUrl.isNotEmpty()) generateM3u8(name, interceptedStreamUrl, mainUrl, headers = headers).forEach(callback)
        }
    }
    private fun resolveEmbedUrl(inputUrl: String): String {
        return if (inputUrl.contains("/f/")) "$mainUrl/${inputUrl.substringAfter("/f/")}"
        else if (inputUrl.contains("/e/")) "$mainUrl/${inputUrl.substringAfter("/e/")}"
        else inputUrl
    }
}

// ==========================================
// 5. DAPUR DOODSTREAM
// ==========================================
class D0000d : DoodLaExtractor() { override var mainUrl = "https://d0000d.com" }
class DoodstreamCom : DoodLaExtractor() { override var mainUrl = "https://doodstream.com" }
class Dooood : DoodLaExtractor() { override var mainUrl = "https://dooood.com" }
class DoodWfExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.wf" }
class DoodCxExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.cx" }
class DoodShExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.sh" }
class DoodWatchExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.watch" }
class DoodPmExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.pm" }
class DoodToExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.to" }
class DoodSoExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.so" }
class DoodWsExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.ws" }
class DoodYtExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.yt" }
class DoodLiExtractor : DoodLaExtractor() { override var mainUrl = "https://dood.li" }
class Ds2play : DoodLaExtractor() { override var mainUrl = "https://ds2play.com" }
class Ds2video : DoodLaExtractor() { override var mainUrl = "https://ds2video.com" }
class MyVidPlay : DoodLaExtractor() { override var mainUrl = "https://myvidplay.com" }

open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val embedUrl = url.replace("/d/", "/e/")
        val req = app.get(embedUrl)
        val host = URI(req.url).let { "${it.scheme}://${it.host}" }
        val response0 = req.text
        val md5 = host + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return)
        val trueUrl = app.get(md5, referer = req.url).text + buildString { repeat(10) { append(alphabet.random()) } } + "?token=" + md5.substringAfterLast("/")
        val quality = Regex("\\d{3,4}p").find(response0.substringAfter("<title>").substringBefore("</title>"))?.groupValues?.getOrNull(0)
        callback.invoke(newExtractorLink(name, name, trueUrl) { this.referer = "$mainUrl/"; this.quality = getQualityFromName(quality) })
    }
}

// ==========================================
// 6. DAPUR LULUSTREAM
// ==========================================
class Lulustream1 : LuluStream() { override val name = "Lulustream"; override val mainUrl = "https://lulustream.com" }
class Lulustream2 : LuluStream() { override val name = "Lulustream"; override val mainUrl = "https://kinoger.pw" }

open class LuluStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://luluvdo.com"
    override val requiresReferer = true
    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val filecode = url.substringAfterLast("/")
        val post = app.post("$mainUrl/dl", data = mapOf("op" to "embed", "file_code" to filecode, "auto" to "1", "referer" to (referer ?: ""))).document
        post.selectFirst("script:containsData(vplayer)")?.data()?.let { script ->
            Regex("file:\"(.*)\"").find(script)?.groupValues?.get(1)?.let { link ->
                callback(newExtractorLink(name, name, link) { this.referer = mainUrl; this.quality = Qualities.P1080.value })
            }
        }
    }
}
