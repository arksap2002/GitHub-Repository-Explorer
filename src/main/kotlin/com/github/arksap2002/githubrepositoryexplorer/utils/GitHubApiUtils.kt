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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Utility class for GitHub API interactions.
 */
object GitHubApiUtils {
    private fun <T> executeWithHttpClient(
        scope: CoroutineScope,
        operationFailLogMessage: String,
        notFoundLogMessage: String,
        notFoundMessageKey: String,
        generalFailMessageKey: String,
        engine: HttpClientEngine,
        action: suspend (client: HttpClient) -> T,
    ): T {
        val client = HttpClient(engine)
        try {
            return runBlocking(scope.coroutineContext) { action(client) }
        } catch (e: ClientRequestException) {
            when (e.response.status.value) {
                404 -> {
                    thisLogger().warn(notFoundLogMessage)
                    throw Exception(GithubRepositoryExplorer.message(notFoundMessageKey))
                }

                else -> {
                    thisLogger().warn("GitHub API error: ${e.response.status.value} - ${e.message}")
                    throw Exception(
                        GithubRepositoryExplorer.message(
                            "githubApi.error.apiError",
                            e.response.status.value,
                            e.message
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().error(operationFailLogMessage, e)
            throw Exception(GithubRepositoryExplorer.message(generalFailMessageKey, e.message ?: ""))
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
        return try {
            executeWithHttpClient(
                scope = scope,
                operationFailLogMessage = "Failed to validate token",
                notFoundLogMessage = "User endpoint not found",
                notFoundMessageKey = "githubApi.error.apiError",
                generalFailMessageKey = "githubApi.error.apiError",
                engine = engine,
            ) { client ->
                val url = "https://api.github.com/user"
                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }
                val isValid = isSuccessful(response)
                if (isValid) {
                    thisLogger().info("GitHub token validation successful")
                } else {
                    thisLogger().warn("GitHub token validation failed: HTTP ${response.status.value}")
                }
                isValid
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("GitHub token validation failed with exception", e)
            false
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
    ): Pair<Boolean, String> {
        thisLogger().info("Fetching file content from GitHub API: $downloadUrl")
        return try {
            executeWithHttpClient(
                scope = scope,
                operationFailLogMessage = "Failed to fetch file content",
                notFoundLogMessage = "File not found: $downloadUrl",
                notFoundMessageKey = "githubApi.error.fileNotFound",
                generalFailMessageKey = "githubApi.error.fetchFileFailed",
                engine = engine,
            ) { client ->
                val response: HttpResponse = client.get(downloadUrl) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/vnd.github.v3.raw")
                }
                val content = response.bodyAsText()
                val success = isSuccessful(response)
                if (success) {
                    thisLogger().info("Successfully fetched file content")
                } else {
                    thisLogger().warn("Fetched file content with non-2xx status: HTTP ${'$'}{response.status.value}")
                }
                Pair(success, content)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("fetchFileContent failed: ${e.message}")
            Pair(false, "")
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
    ): Pair<Boolean, ByteArray> {
        thisLogger().info("Fetching binary file content from GitHub API: $downloadUrl")
        return try {
            executeWithHttpClient(
                scope = scope,
                operationFailLogMessage = "Failed to fetch binary file content",
                notFoundLogMessage = "File not found: $downloadUrl",
                notFoundMessageKey = "githubApi.error.fileNotFound",
                generalFailMessageKey = "githubApi.error.fetchFileFailed",
                engine = engine,
            ) { client ->
                val response: HttpResponse = client.get(downloadUrl) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/vnd.github.v3.raw")
                }
                val bytes: ByteArray = response.body()
                val success = isSuccessful(response)
                if (success) {
                    thisLogger().info("Successfully fetched binary file content")
                } else {
                    thisLogger().warn("Fetched binary content with non-2xx status: HTTP ${'$'}{response.status.value}")
                }
                Pair(success, bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("fetchFileBytes failed: ${e.message}")
            Pair(false, ByteArray(0))
        }
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
    ): Pair<Boolean, List<FileTreeNode>> {
        val baseUrl = "https://api.github.com/repos/${owner}/${repo}/contents/"
        thisLogger().info("Listing directory (non-recursive): repo=$owner/$repo path=$path")
        return try {
            executeWithHttpClient(
                scope = scope,
                operationFailLogMessage = "Failed to list directory",
                notFoundLogMessage = "Directory not found: ${baseUrl}${path}",
                notFoundMessageKey = "githubApi.error.repoNotFound",
                generalFailMessageKey = "githubApi.error.fetchRepoFailed",
                engine = engine,
            ) { client ->
                val url = if (path.isEmpty()) baseUrl else "$baseUrl$path"

                // Make the API request with authentication
                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }

                if (!isSuccessful(response)) {
                    val statusCode = response.status.value
                    throw ClientRequestException(response, "HTTP $statusCode while fetching contents")
                }

                // Parse the JSON response
                val responseText = response.bodyAsText()
                val json = Json { ignoreUnknownKeys = true }
                val contents = json.decodeFromString<List<GitHubContent>>(responseText)
                val result = mutableListOf<FileTreeNode>()

                // Build the file tree structure with recursive directory traversal
                for (content in contents) {
                    val node = FileTreeNode(
                        name = content.name,
                        path = content.path,
                        type = content.type,
                        download_url = content.download_url
                    )
                    result.add(node)
                }

                Pair(true, result)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("listDirectory failed: ${e.message}")
            Pair(false, emptyList())
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
        return try {
            executeWithHttpClient(
                scope = scope,
                operationFailLogMessage = "Failed to validate owner",
                notFoundLogMessage = "Owner not found: $owner",
                notFoundMessageKey = "githubApi.error.ownerNotFound",
                generalFailMessageKey = "githubApi.error.apiError",
                engine = engine,
            ) { client ->
                val url = "https://api.github.com/users/${owner}"
                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }
                val isValid = isSuccessful(response)
                if (isValid) {
                    thisLogger().info("GitHub owner validation successful for $owner")
                } else {
                    thisLogger().warn("GitHub owner validation failed: HTTP ${response.status.value}")
                }
                isValid
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            thisLogger().warn("GitHub owner validation failed with exception", e)
            false
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
