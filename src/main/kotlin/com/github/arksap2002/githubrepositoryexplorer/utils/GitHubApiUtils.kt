package com.github.arksap2002.githubrepositoryexplorer.utils

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.intellij.openapi.diagnostic.thisLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Utility class for GitHub API interactions.
 */
object GitHubApiUtils {
    data class OperationResult<T>(val success: Boolean, val data: T)

    private fun <T> withAuthorizedGet(
        scope: CoroutineScope,
        engine: HttpClientEngine,
        url: String,
        token: String,
        accept: String,
        onError: () -> T,
        handler: suspend (HttpResponse) -> T,
    ): T {
        val client = HttpClient(engine)
        return try {
            runBlocking(scope.coroutineContext) {
                delay(10.seconds)
                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", accept)
                }
                handler(response)
            }
        } catch (e: ClientRequestException) {
            throw Exception(
                GithubRepositoryExplorer.message(
                    "githubApi.error.apiError",
                    e.response.status.value,
                    e.message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("Error: ${e.message}")
            onError()
        } finally {
            client.close()
        }
    }

    private fun isSuccessful(response: HttpResponse): Boolean {
        return response.status.value in 200..299
    }

    /**
     * Validates a GitHub token by making a request to the GitHub API.
     *
     * @param token The GitHub token to validate
     * @return true if the token is valid, false otherwise
     */
    fun isTokenValid(scope: CoroutineScope, token: String, engine: HttpClientEngine = CIO.create()): Boolean {
        thisLogger().info("Validating GitHub token")
        val url = "https://api.github.com/user"
        return withAuthorizedGet(scope, engine, url, token, "application/json", onError = { false }) { response ->
            isSuccessful(response)
        }
    }

    /**
     * Fetches file content from GitHub API using the provided download URL.
     *
     * @param token The GitHub personal access token to authenticate the request.
     * @param downloadUrl The URL to download the raw file content.
     * @return The content of the file as a string, or null if not found or on error.
     */
    fun fetchFileContent(
        scope: CoroutineScope,
        token: String,
        downloadUrl: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<String> {
        thisLogger().info("Fetching file content from GitHub API: $downloadUrl")
        return withAuthorizedGet(
            scope,
            engine,
            downloadUrl,
            token,
            "application/vnd.github.v3.raw",
            onError = { OperationResult(false, "") }) { response ->
            OperationResult(isSuccessful(response), response.bodyAsText())
        }
    }

    /**
     * Fetches binary file content (e.g., images) from GitHub API using the provided download URL.
     *
     * @param token The GitHub personal access token to authenticate the request.
     * @param downloadUrl The URL to download the raw file content.
     * @return The content of the file as a byte array, or null if not found or on error.
     */
    fun fetchFileBytes(
        scope: CoroutineScope,
        token: String,
        downloadUrl: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<ByteArray> {
        thisLogger().info("Fetching binary file content from GitHub API: $downloadUrl")
        return withAuthorizedGet(
            scope,
            engine,
            downloadUrl,
            token,
            "application/vnd.github.v3.raw",
            onError = { OperationResult(false, ByteArray(0)) }) { response ->
            OperationResult(isSuccessful(response), response.body())
        }
    }

    private fun parseDirectoryContents(responseText: String): List<FileTreeNode> {
        val json = Json { ignoreUnknownKeys = true }
        val contents = json.decodeFromString<List<GitHubContent>>(responseText)
        val result = mutableListOf<FileTreeNode>()
        for (content in contents) {
            val node = FileTreeNode(
                name = content.name,
                path = content.path,
                type = content.type,
                download_url = content.download_url
            )
            result.add(node)
        }
        return result
    }

    /**
     * Lists immediate children for a given directory path (non-recursive).
     * @param token GitHub token
     * @param owner Repository owner
     * @param repo Repository name
     * @param path Directory path relative to repo root ("" or "dir/subdir")
     */
    fun listDirectory(
        scope: CoroutineScope,
        token: String,
        owner: String,
        repo: String,
        path: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<List<FileTreeNode>> {
        val baseUrl = "https://api.github.com/repos/${owner}/${repo}/contents/"
        val url = if (path.isEmpty()) baseUrl else "$baseUrl$path"
        return withAuthorizedGet(
            scope,
            engine,
            url,
            token,
            "application/json",
            onError = { OperationResult(false, emptyList()) }) { response ->
            OperationResult(true, parseDirectoryContents(response.bodyAsText()))
        }
    }

    /**
     * Checks if a GitHub owner (user or organization) exists.
     * @param token GitHub token
     * @param owner Owner login (user or organization)
     * @return true if the owner exists and is accessible, false otherwise
     */
    fun isOwnerValid(
        scope: CoroutineScope,
        token: String,
        owner: String,
        engine: HttpClientEngine = CIO.create()
    ): Boolean {
        thisLogger().info("Validating GitHub owner: $owner")
        val url = "https://api.github.com/users/${owner}"
        return withAuthorizedGet(scope, engine, url, token, "application/json", onError = { false }) { response ->
            isSuccessful(response)
        }
    }
}

@Serializable
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int = 0,
    val url: String,
    val html_url: String,
    val git_url: String,
    val download_url: String? = null,
    val type: String,
)

@Serializable
data class FileTreeNode(
    val name: String,
    val path: String,
    val type: String,
    var isProcessed: Boolean = false,
    val download_url: String? = null,
) {
    override fun toString(): String {
        return name
    }
}
