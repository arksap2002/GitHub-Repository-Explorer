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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Utility class for GitHub API interactions.
 */
object GitHubApiUtils {
    data class OperationResult<T>(val success: Boolean, val data: T)

    private suspend fun <T> withAuthorizedGet(
        engine: HttpClientEngine,
        url: String,
        token: String,
        accept: String,
        onError: () -> T,
        handler: suspend (HttpResponse) -> T,
    ): T {
        val client = HttpClient(engine)
        return try {
            withContext(Dispatchers.IO) {
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
    suspend fun isTokenValid(token: String, engine: HttpClientEngine = CIO.create()): Boolean {
        thisLogger().info("Validating GitHub token")
        val url = "https://api.github.com/user"
        val onError = { false }

        return withAuthorizedGet(engine, url, token, "application/json", onError) { response ->
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
    suspend fun fetchFileContent(
        token: String,
        downloadUrl: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<String> {
        thisLogger().info("Fetching file content from GitHub API: $downloadUrl")
        val onError = { OperationResult(false, "") }

        return withAuthorizedGet(engine, downloadUrl, token, "application/vnd.github.v3.raw", onError) { response ->
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
    suspend fun fetchFileBytes(
        token: String,
        downloadUrl: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<ByteArray> {
        thisLogger().info("Fetching binary file content from GitHub API: $downloadUrl")
        val onError = { OperationResult(false, ByteArray(0)) }

        return withAuthorizedGet(engine, downloadUrl, token, "application/vnd.github.v3.raw", onError) { response ->
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
    suspend fun listDirectory(
        token: String,
        owner: String,
        repo: String,
        path: String,
        engine: HttpClientEngine = CIO.create()
    ): OperationResult<List<FileTreeNode>> {
        val baseUrl = "https://api.github.com/repos/${owner}/${repo}/contents/"
        val url = if (path.isEmpty()) baseUrl else "$baseUrl$path"
        val onError = { OperationResult(false, emptyList<FileTreeNode>()) }

        return withAuthorizedGet(engine, url, token, "application/json", onError) { response ->
            OperationResult(true, parseDirectoryContents(response.bodyAsText()))
        }
    }

    /**
     * Checks if a GitHub owner (user or organization) exists.
     * @param token GitHub token
     * @param owner Owner login (user or organization)
     * @return true if the owner exists and is accessible, false otherwise
     */
    suspend fun isOwnerValid(
        token: String,
        owner: String,
        engine: HttpClientEngine = CIO.create()
    ): Boolean {
        thisLogger().info("Validating GitHub owner: $owner")
        val url = "https://api.github.com/users/${owner}"
        val onError = { false }

        return withAuthorizedGet(engine, url, token, "application/json", onError) { response ->
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
