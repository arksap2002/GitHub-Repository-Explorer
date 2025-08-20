package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.ui.OpenRepoDialog
import com.github.arksap2002.githubrepositoryexplorer.ui.RepoStructureDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Action to open and validate a GitHub repository.
 */
class OpenRepoAction : AnAction() {
    init {
        templatePresentation.text = GithubRepositoryExplorer.message("openRepo")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = UserDataService.isUserLoggedIn()
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Open repository action triggered")

        val project = e.project ?: return

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // Show a dialog to enter repository details
        val dialog = OpenRepoDialog(project, scope)
        if (dialog.showAndGet()) {
            // Get repository information from the dialog
            val rootNodes = dialog.getRepoRootNodes()
            val repoOwner = dialog.getRepoOwner()
            val repoName = dialog.getRepoName()

            if (rootNodes != null) {
                // Create and show the repository structure dialog
                val structureDialog = RepoStructureDialog(project, rootNodes, repoOwner, repoName, scope)
                structureDialog.showAndGet()

                thisLogger().info("Repository structure dialog shown for: $repoOwner/$repoName")
            } else {
                thisLogger().warn("Repository structure JSON is null for: $repoOwner/$repoName")
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
