package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.FileTreeNode
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.launch
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/**
 * Dialog for entering GitHub repository information.
 */
class OpenRepoDialog(private val project: Project, private val scope: CoroutineScope) : DialogWrapper(project) {
    private val ownerField = JBTextField(GithubRepositoryExplorer.message("ui.textField.repoFieldSize").toInt())
    private val nameField = JBTextField(GithubRepositoryExplorer.message("ui.textField.repoFieldSize").toInt())
    private var rootNodes: List<FileTreeNode>? = null

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

        scope.launch {
            var isValid = false
            var errorMessage: String? = null

            // Create a background task for repository validation
            withBackgroundProgress(project, GithubRepositoryExplorer.message("repoDialog.validation.title")) {
                try {
                    thisLogger().info("Validating repository: $owner/$name")
                    val ownerValid = GitHubApiUtils.isOwnerValid(token, owner)
                    if (!ownerValid) {
                        isValid = false
                        errorMessage = GithubRepositoryExplorer.message("repoDialog.error.invalidOwner", owner)
                    } else {
                        val result = GitHubApiUtils.listDirectory(token, owner, name, "")
                        isValid = result.success
                        rootNodes = result.data
                        if (!isValid) {
                            errorMessage =
                                GithubRepositoryExplorer.message("repoDialog.error.invalidRepoName", owner, name)
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                isOKActionEnabled = true
                if (isValid) {
                    // Handle successful validation
                    thisLogger().info("Repository dialog completed successfully")
                    super@OpenRepoDialog.doOKAction()
                } else {
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
    }

    override fun doOKAction() {
        isOKActionEnabled = false
        validateRepositoryWithProgress()
    }

    override fun doCancelAction() {
        thisLogger().info("Open repository dialog canceled by user; canceling background tasks")
        scope.cancel("Open repository dialog canceled")
        super.doCancelAction()
    }
}
