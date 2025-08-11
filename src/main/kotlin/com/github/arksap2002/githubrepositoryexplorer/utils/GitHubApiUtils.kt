package com.github.arksap2002.githubrepositoryexplorer.utils

import com.intellij.openapi.diagnostic.Logger
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
    private val logger = Logger.getInstance(GitHubApiUtils::class.java)

    /**
     * Makes a GET request to the given URL using the provided token and returns the fetched repository structure
     * as a JSON string. Throws exceptions for specific HTTP response errors or failures.
     *
     * @param token The GitHub personal access token to authenticate the request.
     * @param url The URL for the GitHub API endpoint to fetch the repository contents.
     * @return A JSON string representing the directory structure of the repository.
     * @throws Exception if the repository is not found (HTTP 404) or other errors occur during the request.
     */
    fun makeGetRequest(token: String, url: String): String {
        return runBlocking {
            val client = HttpClient(CIO)
            try {
                val fileTree = fetchDirectoryContents(client, url, "", token)
                Json.encodeToString(fileTree)
            } catch (e: ClientRequestException) {
                when (e.response.status.value) {
                    404 -> throw Exception("Repository not found")
                    else -> throw Exception("GitHub API error: ${e.response.status.value} - ${e.message}")
                }
            } catch (e: Exception) {
                throw Exception("Failed to fetch repository structure: ${e.message}")
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

        val response: HttpResponse = client.get(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
        }

        val responseText = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }

        val contents = json.decodeFromString<List<GitHubContent>>(responseText)
        val result = mutableListOf<FileTreeNode>()

        for (content in contents) {
            val node = FileTreeNode(
                name = content.name,
                path = content.path,
                type = content.type
            )

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
        return runBlocking {
            try {
                val url = "https://api.github.com/user"
                val client = HttpClient(CIO)

                val response: HttpResponse = client.get(url) {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/json")
                }

                client.close()

                response.status.value in 200..299
            } catch (e: Exception) {
                logger.error("Error validating GitHub token", e)
                false
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
    val children: MutableList<FileTreeNode> = mutableListOf()
)
