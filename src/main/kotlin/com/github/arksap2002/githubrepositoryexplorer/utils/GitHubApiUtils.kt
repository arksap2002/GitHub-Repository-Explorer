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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Utility class for GitHub API interactions.
 */
object GitHubApiUtils {
    /**
     * Makes a GET request to the given URL using the provided token and returns the fetched repository structure
     * as a JSON string.
     *
     * @param token The GitHub personal access token to authenticate the request.
     * @param url The URL for the GitHub API endpoint to fetch the repository contents.
     * @return A JSON string representing the directory structure of the repository.
     * @throws Exception if the repository is not found (HTTP 404) or other errors occur during the request.
     */
    fun makeGetRequest(token: String, url: String): String {
        thisLogger().info("Making GET request to GitHub API: $url")
        return runBlocking {
            val client = HttpClient(CIO)

            try {
                // Fetch repository structure and convert to JSON
                val fileTree = fetchDirectoryContents(client, url, "", token)
                val result = Json.encodeToString(fileTree)
                thisLogger().info("Successfully fetched repository structure")
                result
            } catch (e: ClientRequestException) {
                // Handle specific HTTP error responses
                when (e.response.status.value) {
                    404 -> {
                        thisLogger().warn("Repository not found: $url")
                        throw Exception(GithubRepositoryExplorer.message("githubApi.error.repoNotFound"))
                    }

                    else -> {
                        thisLogger().error("GitHub API error: ${e.response.status.value} - ${e.message}")
                        throw Exception(
                            GithubRepositoryExplorer.message(
                                "githubApi.error.apiError",
                                e.response.status.value,
                                e.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle general exceptions
                thisLogger().error("Failed to fetch repository structure", e)
                throw Exception(GithubRepositoryExplorer.message("githubApi.error.fetchRepoFailed", e.message ?: ""))
            } finally {
                client.close()
            }
        }
    }

    private suspend fun fetchDirectoryContents(
        client: HttpClient,
        baseUrl: String,
        path: String,
        token: String
    ): List<FileTreeNode> {
        val url = if (path.isEmpty()) baseUrl else "$baseUrl$path"

        // Make the API request with authentication
        val response: HttpResponse = client.get(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
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

            // Recursively fetch contents of directories
            if (content.type == "dir") {
                node.children.addAll(fetchDirectoryContents(client, baseUrl, content.path, token))
            }

            result.add(node)
        }

        return result
    }

    /**
     * Validates a GitHub token by making a request to the GitHub API.
     *
     * @param token The GitHub token to validate
     * @return true if the token is valid, false otherwise
     */
    fun isTokenValid(token: String): Boolean {
        thisLogger().info("Validating GitHub token")
        return runBlocking {
            try {
                // Initialize the HTTP client and set up an API request
                val url = "https://api.github.com/user"
                val client = HttpClient(CIO)
                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }

                client.close()

                // Check if response status indicates success
                val isValid = response.status.value in 200..299
                if (isValid) {
                    thisLogger().info("GitHub token validation successful")
                } else {
                    thisLogger().warn("GitHub token validation failed: HTTP ${response.status.value}")
                }

                isValid
            } catch (e: Exception) {
                thisLogger().warn("GitHub token validation failed with exception", e)
                false
            }
        }
    }

    /**
     * Fetches file content from GitHub API using the provided download URL.
     *
     * @param token The GitHub personal access token to authenticate the request.
     * @param downloadUrl The URL to download the raw file content.
     * @return The content of the file as a string.
     * @throws Exception if the file is not found (HTTP 404) or other errors occur during the request.
     */
    fun fetchFileContent(token: String, downloadUrl: String): String {
        thisLogger().info("Fetching file content from GitHub API: $downloadUrl")
        return runBlocking {
            val client = HttpClient(CIO)

            try {
                // Make the API request with authentication
                val response: HttpResponse = client.get(downloadUrl) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/vnd.github.v3.raw")
                }

                val content = response.bodyAsText()
                thisLogger().info("Successfully fetched file content")
                content
            } catch (e: ClientRequestException) {
                // Handle specific HTTP error responses
                when (e.response.status.value) {
                    404 -> {
                        thisLogger().warn("File not found: $downloadUrl")
                        throw Exception(GithubRepositoryExplorer.message("githubApi.error.fileNotFound"))
                    }

                    else -> {
                        thisLogger().error("GitHub API error: ${e.response.status.value} - ${e.message}")
                        throw Exception(
                            GithubRepositoryExplorer.message(
                                "githubApi.error.apiError",
                                e.response.status.value,
                                e.message
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Handle general exceptions
                thisLogger().error("Failed to fetch file content", e)
                throw Exception(GithubRepositoryExplorer.message("githubApi.error.fetchFileFailed", e.message ?: ""))
            } finally {
                client.close()
            }
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
    val download_url: String? = null,
    val children: MutableList<FileTreeNode> = mutableListOf()
) {
    override fun toString(): String {
        return name
    }
}
