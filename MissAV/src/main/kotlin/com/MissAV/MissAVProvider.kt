package com.MissAV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType // Import Baru
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class MissAVProvider : MainAPI() {
    override var mainUrl = "https://missav.ws"
    override var name = "MissAV"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // --- 1. SEARCH ---
    override suspend fun search(query: String): List<SearchResponse> {
        val fixedQuery = query.trim().replace(" ", "-")
        val url = "$mainUrl/$lang/search/$fixedQuery"
        
        return try {
            val document = app.get(url).document
            val results = ArrayList<SearchResponse>()

            document.select("div.grid div.group").forEach { element ->
                val linkElement = element.selectFirst("a")
                val href = linkElement?.attr("href") ?: return@forEach
                val fixedUrl = fixUrl(href)
                
                val title = element.selectFirst("div.text-secondary")?.text()?.trim() 
                    ?: linkElement.attr("alt") 
                    ?: "No Title"
                
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

    // --- 2. MAIN PAGE ---
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = "$mainUrl/$lang/new"
        val document = app.get(url).document
        val homeItems = ArrayList<SearchResponse>()

        document.select("div.grid div.group").forEach { element ->
            val linkElement = element.selectFirst("a") ?: return@forEach
            val fixedUrl = fixUrl(linkElement.attr("href"))
            val title = element.selectFirst("div.text-secondary")?.text()?.trim() ?: "No Title"
            val img = element.selectFirst("img")
            val posterUrl = img?.attr("data-src") ?: img?.attr("src")

            homeItems.add(newMovieSearchResponse(title, fixedUrl, TvType.NSFW) {
                this.posterUrl = posterUrl
            })
        }
        return newHomePageResponse(HomePageList("Latest Videos", homeItems, isHorizontal = false), false)
    }

    // --- 3. LOAD ---
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.text-base")?.text()?.trim() ?: "Unknown Title"
        
        val poster = document.selectFirst("meta[property='og:image']")?.attr("content")
            ?: document.selectFirst("video.player")?.attr("poster")

        val description = document.selectFirst("div.text-secondary.mb-2")?.text()?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, LinkData(url)) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // --- 4. LOAD LINKS (Updated Standard) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        val text = app.get(data).text
        val m3u8Regex = Regex("""(https:\\/\\/[a-zA-Z0-9\-\._~:\/\?#\[\]@!$&'\(\)*+,;=]+?\.m3u8)""")
        val matches = m3u8Regex.findAll(text)
        
        if (matches.count() > 0) {
            matches.forEach { match ->
                val rawUrl = match.groupValues[1]
                val fixedUrl = rawUrl.replace("\\/", "/")

                val quality = when {
                    fixedUrl.contains("1280x720") || fixedUrl.contains("720p") -> Qualities.P720.value
                    fixedUrl.contains("1920x1080") || fixedUrl.contains("1080p") -> Qualities.P1080.value
                    fixedUrl.contains("842x480") || fixedUrl.contains("480p") -> Qualities.P480.value
                    fixedUrl.contains("240p") -> Qualities.P240.value
                    else -> Qualities.Unknown.value
                }

                val sourceName = if (fixedUrl.contains("surrit")) "Surrit (HD)" else "MissAV (Backup)"

                // PERUBAHAN DI SINI:
                // Menggunakan konstruktor ExtractorLink yang sesuai dengan standar file ExtractorApi.kt
                // Menggunakan parameter 'type' alih-alih 'isM3u8'
                callback.invoke(
                    ExtractorLink(
                        source = this.name,
                        name = "$sourceName $quality",
                        url = fixedUrl,
                        referer = data,
                        quality = quality,
                        type = ExtractorLinkType.M3U8 // Standar Baru
                    )
                )
            }
            return true
        }
        return false
    }
}
