package com.MissAV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import org.jsoup.nodes.Element

@OptIn(com.lagradost.cloudstream3.Prerelease::class)
class MissAVProvider : MainAPI() {
    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/$lang/uncensored-leak" to "Kebocoran Tanpa Sensor",
        "$mainUrl/$lang/release" to "Keluaran Terbaru",
        "$mainUrl/$lang/new" to "Recent Update",
        "$mainUrl/$lang/genres/Wanita%20Menikah/Ibu%20Rumah%20Tangga" to "Wanita Menikah"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(url).document
        val homeItems = ArrayList<SearchResponse>()

        document.select("div.thumbnail").forEach { element ->
            val linkElement = element.selectFirst("a.text-secondary") ?: return@forEach
            val href = linkElement.attr("href")
            val fixedUrl = fixUrl(href)
            val title = linkElement.text().trim()
            val img = element.selectFirst("img")
            val posterUrl = img?.attr("data-src") ?: img?.attr("src")

            homeItems.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                this.posterUrl = posterUrl
            })
        }
        
        return newHomePageResponse(
            HomePageList(name = request.name, list = homeItems, isHorizontalImages = true),
            hasNext = true
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val fixedQuery = query.trim().replace(" ", "-")
        val url = "$mainUrl/$lang/search/$fixedQuery"
        return try {
            val document = app.get(url).document
            val results = ArrayList<SearchResponse>()

            document.select("div.thumbnail").forEach { element ->
                val linkElement = element.selectFirst("a.text-secondary") ?: return@forEach
                val href = linkElement.attr("href")
                val fixedUrl = fixUrl(href)
                val title = linkElement.text().trim()
                val img = element.selectFirst("img")
                val posterUrl = img?.attr("data-src") ?: img?.attr("src")

                results.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                    this.posterUrl = posterUrl
                })
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            ArrayList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("video.player")?.attr("poster")
        val description = document.select("div.text-secondary")
            .maxByOrNull { it.text().length }?.text()?.trim()
            ?: document.selectFirst("meta[property='og:description']")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // --- LOGIKA SUBTITLE GROUPING (Sudah Fix, Jangan Diubah) ---
    private suspend fun fetchSubtitleCat(code: String, subtitleCallback: (SubtitleFile) -> Unit) {
        try {
            val searchUrl = "https://www.subtitlecat.com/index.php?search=$code"
            val searchDoc = app.get(searchUrl).document
            
            val searchResults = searchDoc.select("table.sub-table tbody tr td:nth-child(1) > a")
            
            // Ambil 10 varian agar user punya banyak pilihan cadangan
            searchResults.take(10).forEach { linkElement ->
                var detailPath = linkElement.attr("href")
                if (!detailPath.startsWith("http")) {
                    detailPath = if (detailPath.startsWith("/")) detailPath else "/$detailPath"
                    detailPath = "https://www.subtitlecat.com$detailPath"
                }

                try {
                    val detailDoc = app.get(detailPath).document
                    
                    detailDoc.select("div.sub-single").forEach { item ->
                        // RAHASIA GROUPING: Nama bahasa harus sama persis (misal: "Indonesian").
                        // Cloudstream otomatis bikin angka 1, 2, 3 di UI.
                        val rawLang = item.select("span").getOrNull(1)?.text()?.trim() ?: "Unknown"
                        
                        val downloadEl = item.selectFirst("a.green-link")
                        val downloadHref = downloadEl?.attr("href")

                        if (downloadHref != null) {
                            val finalUrl = if (downloadHref.startsWith("http")) {
                                downloadHref
                            } else {
                                "https://www.subtitlecat.com$downloadHref"
                            }
                            
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = rawLang, 
                                    url = finalUrl
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Skip error
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION_ERROR") 
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        var text = app.get(data).text
        text = getAndUnpack(text) 

        val m3u8Regex = Regex("""(https?:\\?\/\\?\/[^"']+\.m3u8)""")
        val matches = m3u8Regex.findAll(text)
        
        // Deduping URL supaya tidak ada link kembar
        val uniqueUrls = matches.map { 
            it.groupValues[1].replace("\\/", "/") 
        }.toSet()

        if (uniqueUrls.isNotEmpty()) {
            uniqueUrls.forEach { fixedUrl ->
                
                // --- KUNCI UTAMA PERBAIKAN SUMBER ---
                // Kita set Quality ke Unknown.
                // Ini mencegah Cloudstream membuat entri terpisah (Surrit 1080p, Surrit 720p).
                // Hasilnya: Cuma ada satu "Surrit" di daftar sumber.
                // Resolusi (Tracks) akan dibaca otomatis oleh Player dari file .m3u8
                
                val quality = Qualities.Unknown.value

                val sourceName = if (fixedUrl.contains("surrit")) "Surrit" else "MissAV"

                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = sourceName,
                        url = fixedUrl,
                        referer = data,
                        quality = quality, // Set ke Unknown agar UI bersih
                        isM3u8 = true      // Player akan parsing resolusi secara otomatis
                    )
                )
            }

            // --- PROSES SUBTITLE ---
            val codeRegex = Regex("""([a-zA-Z]{2,5}-\d{3,5})""")
            val codeMatch = codeRegex.find(data)
            val code = codeMatch?.value
            
            if (code != null) {
                try {
                    fetchSubtitleCat(code, subtitleCallback)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return true
        }
        return false
    }
}
