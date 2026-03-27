package com.github.arksap2002.githubrepositoryexplorer.utils

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class GitHubApiUtilsTest {

    private fun jsonHeaders(): Headers = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `isTokenValid returns true on 200 OK`() = runTest {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isTokenValid(token = "t", engine = engine)
        assertTrue(result)
    }

    @Test
    fun `isTokenValid returns false on 401 Unauthorized`() = runTest {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.Unauthorized, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isTokenValid(token = "t", engine = engine)
        assertFalse(result)
    }

    @Test
    fun `fetchFileContent returns body on success`() = runTest {
        val body = "hello world"
        val engine = MockEngine { _ ->
            respond(body, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val result = GitHubApiUtils.fetchFileContent(token = "t", downloadUrl = "http://example", engine = engine)
        assertTrue(result.success)
        assertEquals(body, result.data)
    }

    @Test
    fun `fetchFileContent returns body as-is on non-2xx`() = runTest {
        val body = "error page"
        val engine = MockEngine { _ ->
            respond(body, status = HttpStatusCode.BadRequest, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val result = GitHubApiUtils.fetchFileContent(token = "t", downloadUrl = "http://example", engine = engine)
        assertFalse(result.success)
        assertEquals(body, result.data)
    }

    @Test
    fun `fetchFileBytes returns bytes on success`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine { _ ->
            respond(bytes, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val result = GitHubApiUtils.fetchFileBytes(token = "t", downloadUrl = "http://example", engine = engine)
        assertTrue(result.success)
        assertArrayEquals(bytes, result.data)
    }

    @Test
    fun `fetchFileBytes returns bytes as-is on non-2xx`() = runTest {
        val bytes = byteArrayOf(9, 8, 7)
        val engine = MockEngine { _ ->
            respond(bytes, status = HttpStatusCode.BadRequest, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
        }
        val result = GitHubApiUtils.fetchFileBytes(token = "t", downloadUrl = "http://example", engine = engine)
        assertFalse(result.success)
        assertArrayEquals(bytes, result.data)
    }

    @Test
    fun `listDirectory parses nodes on success`() = runTest {
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
        val result = GitHubApiUtils.listDirectory(token = "t", owner = "o", repo = "r", path = "", engine = engine)
        assertTrue(result.success)
        assertEquals(2, result.data.size)
        assertEquals("README.md", result.data[0].name)
        assertEquals("file", result.data[0].type)
        assertEquals("src", result.data[1].name)
        assertEquals("dir", result.data[1].type)
    }

    @Test
    fun `listDirectory returns empty with false on 404`() = runTest {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.NotFound, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.listDirectory(token = "t", owner = "o", repo = "r", path = "", engine = engine)
        assertFalse(result.success)
        assertEquals(0, result.data.size)
    }

    @Test
    fun `isOwnerValid returns true on 200 OK`() = runTest {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isOwnerValid(token = "t", owner = "o", engine = engine)
        assertTrue(result)
    }

    @Test
    fun `isOwnerValid returns false on 404`() = runTest {
        val engine = MockEngine { _ ->
            respond("{}", status = HttpStatusCode.NotFound, headers = jsonHeaders())
        }
        val result = GitHubApiUtils.isOwnerValid(token = "t", owner = "o", engine = engine)
        assertFalse(result)
    }
}
