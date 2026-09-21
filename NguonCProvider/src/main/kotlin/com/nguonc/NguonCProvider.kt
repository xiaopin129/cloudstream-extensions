package com.nguonc

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.net.URLEncoder

class NguonCProvider : MainAPI() {
    override var mainUrl = "https://phim.nguonc.com"
    override var name = "Nguồn C"
    override var lang = "vi"
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Anime
    )

    // 1. TRANG CHỦ
    override val mainPage = mainPageOf(
        "$mainUrl/api/films/phim-moi-cap-nhat" to "Phim Mới Cập Nhật",
        "$mainUrl/api/films/danh-sach/phim-bo" to "Phim Bộ",
        "$mainUrl/api/films/danh-sach/phim-le" to "Phim Lẻ",
        "$mainUrl/api/films/danh-sach/hoat-hinh" to "Hoạt Hình",
        "$mainUrl/api/films/danh-sach/tv-shows" to "TV Shows"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}?page=$page"
        val response = app.get(url).parsedSafe<NguonCResponse>()
        
        val homeItems = response?.items?.mapNotNull { item ->
            item.toSearchResponse()
        } ?: emptyList()

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = homeItems
            ),
            hasNext = response?.paginate?.currentPage ?: 1 < (response?.paginate?.totalPages ?: 1)
        )
    }

    // 2. TÌM KIẾM
    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api/films/search?keyword=$encodedQuery"
        val response = app.get(url).parsedSafe<NguonCResponse>()

        return response?.items?.mapNotNull { item ->
            item.toSearchResponse()
        } ?: emptyList()
    }

    // 3. TẢI THÔNG TIN PHIM
    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).parsedSafe<NguonCDetailResponse>() ?: return null
        val movie = response.movie ?: return null

        val title = movie.name ?: ""
        val poster = movie.posterUrl ?: movie.thumbUrl
        val description = movie.description
        val year = movie.year

        val episodes = mutableListOf<Episode>()
        response.movie.episodes?.forEach { server ->
            server.items?.forEach { ep ->
                episodes.add(
                    Episode(
                        data = ep.embed ?: ep.m3u8 ?: "",
                        name = ep.name ?: "Tập ${ep.slug}",
                        episode = ep.slug?.toIntOrNull()
                    )
                )
            }
        }

        val tvType = if (episodes.size > 1) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    // 4. LẤY LINK VIDEO
    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isEmpty()) return false

        if (data.contains(".m3u8")) {
            callback(
                ExtractorLink(
                    source = name,
                    name = "$name - HLS",
                    url = data,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8
                )
            )
            return true
        }

        if (data.startsWith("http")) {
            loadExtractor(data, mainUrl, subtitleCallback, callback)
            return true
        }

        return false
    }

    // DATA CLASSES
    private fun NguonCItem.toSearchResponse(): SearchResponse? {
        val title = name ?: return null
        val detailApiUrl = "$mainUrl/api/film/$slug"
        return newMovieSearchResponse(title, detailApiUrl, TvType.Movie) {
            this.posterUrl = thumbUrl ?: posterUrl
        }
    }

    data class NguonCResponse(
        @JsonProperty("items") val items: List<NguonCItem>?,
        @JsonProperty("paginate") val paginate: Paginate?
    )

    data class Paginate(
        @JsonProperty("current_page") val currentPage: Int?,
        @JsonProperty("total_pages") val totalPages: Int?
    )

    data class NguonCItem(
        @JsonProperty("name") val name: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("thumb_url") val thumbUrl: String?,
        @JsonProperty("poster_url") val posterUrl: String?
    )

    data class NguonCDetailResponse(
        @JsonProperty("movie") val movie: MovieDetail?
    )

    data class MovieDetail(
        @JsonProperty("name") val name: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("poster_url") val posterUrl: String?,
        @JsonProperty("thumb_url") val thumbUrl: String?,
        @JsonProperty("year") val year: Int?,
        @JsonProperty("episodes") val episodes: List<ServerItem>?
    )

    data class ServerItem(
        @JsonProperty("server_name") val serverName: String?,
        @JsonProperty("items") val items: List<EpisodeItem>?
    )

    data class EpisodeItem(
        @JsonProperty("name") val name: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("embed") val embed: String?,
        @JsonProperty("m3u8") val m3u8: String?
    )
}
