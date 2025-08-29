package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.FileTreeNode
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel

/**
 * Dialog for entering GitHub repository information.
 */
class OpenRepoDialog(private val project: Project, private val scope: CoroutineScope) : DialogWrapper(project) {
    private val ownerField = JBTextField(GithubRepositoryExplorer.message("ui.textField.repoFieldSize").toInt())
    private val nameField = JBTextField(GithubRepositoryExplorer.message("ui.textField.repoFieldSize").toInt())
    private var rootNodes: List<FileTreeNode>? = null
    private var canceled: Boolean = false

    init {
        init()
        title = GithubRepositoryExplorer.message("repoDialog.title")
        thisLogger().info("Repository dialog initialized")
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel(GithubRepositoryExplorer.message("repoDialog.ownerLabel")),
                ownerField,
                1,
                true
            )
            .addLabeledComponent(
                JBLabel(GithubRepositoryExplorer.message("repoDialog.nameLabel")),
                nameField,
                1,
                true
            )
            .addComponentFillVertically(JPanel(), 0)

        return formBuilder.panel
    }

    /**
     * Returns the repository structure.
     */
    fun getRepoRootNodes(): List<FileTreeNode>? {
        return rootNodes
    }

    /**
     * Returns the repository owner.
     */
    fun getRepoOwner(): String {
        return ownerField.text.trim()
    }

    /**
     * Returns the repository name.
     */
    fun getRepoName(): String {
        return nameField.text.trim()
    }

    private fun validateRepositoryWithProgress() {
        // Get repository information and token
        val owner = ownerField.text.trim()
        val name = nameField.text.trim()
        val token = UserDataService.service().token

        // Create a background task for repository validation
        object : Task.Backgroundable(
            project,
            GithubRepositoryExplorer.message("repoDialog.validation.title"),
            false
        ) {
            private var isValid = false
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                // Set up a progress indicator
                indicator.isIndeterminate = true
                indicator.text = GithubRepositoryExplorer.message("repoDialog.validation.message")
                thisLogger().info("Validating repository: $owner/$name")

                try {
                    // First, validate the owner
                    val ownerValid = GitHubApiUtils.isOwnerValid(scope, token, owner)
                    if (!ownerValid) {
                        isValid = false
                        errorMessage = GithubRepositoryExplorer.message("repoDialog.error.invalidOwner", owner)
                        thisLogger().warn("Owner validation failed for: $owner")
                    } else {
                        val result = GitHubApiUtils.listDirectory(scope, token, owner, name, "")
                        isValid = result.success
                        rootNodes = result.data
                        if (isValid) {
                            thisLogger().info("Repository validation successful: $owner/$name")
                        } else {
                            errorMessage =
                                GithubRepositoryExplorer.message("repoDialog.error.invalidRepoName", owner, name)
                            thisLogger().warn("Repository validation failed: $owner/$name")
                        }
                    }
                } catch (_: CancellationException) {
                    thisLogger().info("Repository validation was canceled")
                } catch (e: Exception) {
                    errorMessage = e.message
                    thisLogger().warn("Repository validation failed with exception: ${e.message}")
                }
            }

            override fun onSuccess() {
                // Re-enable OK action before attempting to close the dialog
                isOKActionEnabled = true
                when {
                    isValid -> {
                        // Handle successful validation
                        thisLogger().info("Repository dialog completed successfully")
                        super@OpenRepoDialog.doOKAction()
                    }

                    !canceled -> {
                        // Show error message for failed validation
                        val msg = errorMessage ?: GithubRepositoryExplorer.message("repoDialog.error.invalidRepo")
                        thisLogger().warn("Repository dialog failed: $msg")
                        Messages.showErrorDialog(
                            project,
                            msg,
                            GithubRepositoryExplorer.message("repoDialog.error.title")
                        )
                    }
                }
            }
        }.queue()
    }

    override fun doOKAction() {
        isOKActionEnabled = false
        validateRepositoryWithProgress()
    }

    override fun doCancelAction() {
        thisLogger().info("Open repository dialog canceled by user; canceling background tasks")
        scope.cancel("Open repository dialog canceled")
        canceled = true
        super.doCancelAction()
    }
}
