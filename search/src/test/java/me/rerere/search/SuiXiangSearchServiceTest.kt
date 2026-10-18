package me.rerere.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiXiangSearchServiceTest {
    @Test
    fun `search sends query and maps grok web search results`() = runBlocking {
        val previousClient = SearchService.httpClient
        SearchService.httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("https://sui-xiang.com/v1/web_search", request.url.toString())
                assertEquals("POST", request.method)
                assertEquals("Bearer test-key", request.header("Authorization"))
                val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertEquals("{\"query\":\"latest news\"}", body)

                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        {
                          "max_results": 5,
                          "provider": "grok-native",
                          "query": "latest news",
                          "results": [
                            {"url":"https://example.com/1","title":"First","snippet":"Summary 1"},
                            {"url":"https://example.com/2","title":"Second","snippet":"Summary 2"}
                          ]
                        }
                        """.trimIndent().toResponseBody()
                    )
                    .build()
            }
            .build()

        try {
            val result = SuiXiangSearchService.search(
                params = buildJsonObject { put("query", "latest news") },
                commonOptions = SearchCommonOptions(resultSize = 1),
                serviceOptions = SearchServiceOptions.SuiXiangOptions(apiKey = "test-key"),
            ).getOrThrow()

            assertEquals(1, result.items.size)
            assertEquals("First", result.items.single().title)
            assertEquals("https://example.com/1", result.items.single().url)
            assertEquals("Summary 1", result.items.single().text)
        } finally {
            SearchService.httpClient = previousClient
        }
    }

    @Test
    fun `search requires an api key`() = runBlocking {
        val result = SuiXiangSearchService.search(
            params = buildJsonObject { put("query", "latest news") },
            commonOptions = SearchCommonOptions(),
            serviceOptions = SearchServiceOptions.SuiXiangOptions(),
        )

        assertTrue(result.isFailure)
        assertEquals("随想 API key is required", result.exceptionOrNull()?.message)
    }
}
