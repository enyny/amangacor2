package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.Base64

class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // Header kita buat semirip mungkin dengan browser PC
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Referer" to "$mainUrl/",
        "Connection" to "keep-alive" // Tambahan agar koneksi stabil
    )

    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/category/21/drama/page=" to "Drama",
        "$mainUrl/page=" to "Terbaru" // Menambahkan kategori Home/Terbaru explicit
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url, headers = headers).document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.item_content h3 a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = this.selectFirst("div.item_header img")?.getHighQualityImageAttr()
        return newMovieSearchResponse(title, href, TvType.NSFW) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val document = app.get(url, headers = headers).document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document
        val title = document.selectFirst("h1.product_title")?.text()?.trim() ?: "No Title"
        val description = document.select("p.video-description").text().replace("Description: ", "", ignoreCase = true).trim()
        val poster = document.selectFirst("div.images img")?.getHighQualityImageAttr()
        
        // DEBUG: Cek apakah halaman berhasil dimuat
        System.out.println("JAVHEY_DEBUG: Loading Page -> $url")
        System.out.println("JAVHEY_DEBUG: Title Found -> $title")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        System.out.println("JAVHEY_DEBUG: Start loadLinks for -> $data")

        try {
            // Kita tambah timeout jadi 30 detik untuk halaman yang berat
            val document = app.get(data, headers = headers, timeout = 30).document
            
            // 1. Cek Input Links (Metode Utama)
            val hiddenInput = document.selectFirst("input#links")
            val hiddenLinksEncrypted = hiddenInput?.attr("value")

            System.out.println("JAVHEY_DEBUG: Input#links exists? -> ${hiddenInput != null}")
            
            if (!hiddenLinksEncrypted.isNullOrEmpty()) {
                System.out.println("JAVHEY_DEBUG: Found Base64 string! Decoding...")
                try {
                    val decodedBytes = Base64.getDecoder().decode(hiddenLinksEncrypted)
                    val decodedString = String(decodedBytes)
                    System.out.println("JAVHEY_DEBUG: Decoded Links -> $decodedString")
                    
                    val urls = decodedString.split(",,,")
                    urls.forEach { sourceUrl ->
                        val cleanUrl = sourceUrl.trim()
                        if (cleanUrl.isNotBlank() && cleanUrl.startsWith("http")) {
                            System.out.println("JAVHEY_DEBUG: Loading Extractor for -> $cleanUrl")
                            loadExtractor(cleanUrl, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) {
                    System.out.println("JAVHEY_DEBUG: Error decoding Base64 -> ${e.message}")
                    e.printStackTrace()
                }
            } else {
                // Jika Input kosong, cek apakah ada HTML lain yang mencurigakan (mungkin struktur berubah)
                System.out.println("JAVHEY_DEBUG: WARNING! No Base64 found in input#links")
                
                // Cek Fallback (Button Download manual)
                val downloadLinks = document.select("div.links-download a")
                System.out.println("JAVHEY_DEBUG: Checking fallback div.links-download -> Found ${downloadLinks.size} links")
                
                downloadLinks.forEach { linkTag ->
                    val downloadUrl = linkTag.attr("href")
                    if (downloadUrl.isNotBlank() && downloadUrl.startsWith("http")) {
                        System.out.println("JAVHEY_DEBUG: Fallback loading -> $downloadUrl")
                        loadExtractor(downloadUrl, subtitleCallback, callback)
                    }
                }
            }

        } catch (e: Exception) {
            System.out.println("JAVHEY_DEBUG: CRITICAL ERROR during loadLinks -> ${e.message}")
            e.printStackTrace()
        }
        return true
    }

    private fun Element.getHighQualityImageAttr(): String? {
        val url = when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-original") -> this.attr("data-original")
            else -> this.attr("src")
        }
        return url.toHighRes()
    }

    private fun String?.toHighRes(): String? {
        return this?.replace(Regex("-\\d+x\\d+(?=\\.[a-zA-Z]+$)"), "")
                   ?.replace("-scaled", "")
    }
}
