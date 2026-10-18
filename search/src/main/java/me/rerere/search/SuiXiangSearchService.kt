package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import me.rerere.search.SearchService.Companion.keyRoulette
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object SuiXiangSearchService : SearchService<SearchServiceOptions.SuiXiangOptions> {
    override val name: String = "grok-web-search"

    private const val SEARCH_URL = "https://sui-xiang.com/v1/web_search"

    @Composable
    override fun Description() {
        val uriHandler = LocalUriHandler.current
        TextButton(onClick = { uriHandler.openUri("https://sui-xiang.com") }) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override fun parameters(options: SearchServiceOptions.SuiXiangOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.SuiXiangOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SuiXiangOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val apiKey = keyRoulette.next(serviceOptions.apiKey, serviceOptions.id.toString())
            if (apiKey.isBlank()) {
                error("随想 API key is required")
            }

            val body = buildJsonObject {
                put("query", query)
            }
            val request = Request.Builder()
                .url(SEARCH_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(request).await().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("随想搜索失败 #${response.code}: $responseBody")
                }

                val searchResponse = json.decodeFromString<SuiXiangSearchResponse>(responseBody)
                SearchResult(
                    items = searchResponse.results
                        .take(commonOptions.resultSize.coerceAtLeast(0))
                        .map { result ->
                            SearchResultItem(
                                title = result.title,
                                url = result.url,
                                text = result.snippet,
                            )
                        }
                )
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.SuiXiangOptions
    ): Result<ScrapedResult> =
        Result.failure(Exception("随想 grok-web-search 不支持抓取"))

    @Serializable
    internal data class SuiXiangSearchResponse(
        val results: List<SuiXiangSearchResult> = emptyList(),
    )

    @Serializable
    internal data class SuiXiangSearchResult(
        val url: String,
        val title: String,
        val snippet: String = "",
    )
}
