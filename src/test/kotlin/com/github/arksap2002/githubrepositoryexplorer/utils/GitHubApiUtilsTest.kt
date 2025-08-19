package com.github.arksap2002.githubrepositoryexplorer.utils

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class GitHubApiUtilsTest {

    private val scope = CoroutineScope(EmptyCoroutineContext)

    private fun jsonHeaders(): Headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `isTokenValid returns true on 200 OK`() {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isTokenValid(scope, token = "t", engine = engine)
        assertTrue(result)
    }

    @Test
    fun `isTokenValid returns false on 401 Unauthorized`() {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.Unauthorized, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isTokenValid(scope, token = "t", engine = engine)
        assertFalse(result)
    }

    @Test
    fun `fetchFileContent returns body on success`() {
        val body = "hello world"
        val engine = MockEngine { _ ->
            respond(body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val result = GitHubApiUtils.fetchFileContent(scope, token = "t", downloadUrl = "http://example", engine = engine)
        assertEquals(body, result)
    }

    @Test
    fun `fetchFileContent returns body as-is on non-2xx`() {
        val body = "error page"
        val engine = MockEngine { _ ->
            respond(body, status = HttpStatusCode.BadRequest, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val result = GitHubApiUtils.fetchFileContent(scope, token = "t", downloadUrl = "http://example", engine = engine)
        assertEquals(body, result)
    }

    @Test
    fun `fetchFileBytes returns bytes on success`() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine { _ ->
            respond(bytes, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val result = GitHubApiUtils.fetchFileBytes(scope, token = "t", downloadUrl = "http://example", engine = engine)
        assertNotNull(result)
        assertArrayEquals(bytes, result!!)
    }

    @Test
    fun `fetchFileBytes returns bytes as-is on non-2xx`() {
        val bytes = byteArrayOf(9, 8, 7)
        val engine = MockEngine { _ ->
            respond(bytes, status = HttpStatusCode.BadRequest, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val result = GitHubApiUtils.fetchFileBytes(scope, token = "t", downloadUrl = "http://example", engine = engine)
        assertNotNull(result)
        assertArrayEquals(bytes, result!!)
    }

    @Test
    fun `listDirectory parses nodes on success`() {
        val json = """
            [
              {
                "name": "README.md",
                "path": "README.md",
                "sha": "abc",
                "size": 12,
                "url": "u1",
                "html_url": "h1",
                "git_url": "g1",
                "download_url": "d1",
                "type": "file"
              },
              {
                "name": "src",
                "path": "src",
                "sha": "def",
                "size": 0,
                "url": "u2",
                "html_url": "h2",
                "git_url": "g2",
                "download_url": null,
                "type": "dir"
              }
            ]
        """.trimIndent()
        val engine = MockEngine { _ ->
            respond(json, status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val nodes = GitHubApiUtils.listDirectory(scope, token = "t", owner = "o", repo = "r", path = "", engine = engine)
        assertNotNull(nodes)
        assertEquals(2, nodes!!.size)
        assertEquals("README.md", nodes[0].name)
        assertEquals("file", nodes[0].type)
        assertEquals("src", nodes[1].name)
        assertEquals("dir", nodes[1].type)
    }

    @Test
    fun `listDirectory returns null on 404`() {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.NotFound, headers = jsonHeaders())
        }
        val nodes = GitHubApiUtils.listDirectory(scope, token = "t", owner = "o", repo = "r", path = "", engine = engine)
        assertNull(nodes)
    }

    @Test
    fun `isOwnerValid returns true on 200 OK`() {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isOwnerValid(scope, token = "t", owner = "o", engine = engine)
        assertTrue(result)
    }

    @Test
    fun `isOwnerValid returns false on 404`() {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.NotFound, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isOwnerValid(scope, token = "t", owner = "o", engine = engine)
        assertFalse(result)
    }
}
