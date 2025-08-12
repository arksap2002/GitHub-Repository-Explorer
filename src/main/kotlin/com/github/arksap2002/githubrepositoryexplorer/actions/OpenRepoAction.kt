package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.ui.OpenRepoDialog
import com.github.arksap2002.githubrepositoryexplorer.ui.RepoStructureDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger

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

        val dialog = OpenRepoDialog(project)
        if (dialog.showAndGet()) {
            val repoStructureJson = dialog.getRepoStructureJson()
            val repoOwner = dialog.getRepoOwner()
            val repoName = dialog.getRepoName()
            
            if (repoStructureJson != null) {
                val repoFullName = "$repoOwner/$repoName"
                val structureDialog = RepoStructureDialog(project, repoStructureJson, repoFullName)
                structureDialog.show()
                
                thisLogger().info("Repository structure dialog shown for: $repoFullName")
            } else {
                thisLogger().warn("Repository structure JSON is null for: $repoOwner/$repoName")
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
