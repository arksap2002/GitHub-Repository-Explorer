package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.ui.OpenRepoDialog
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
        val project = e.project
        if (project != null) {
            val token = UserDataService.service(project).token
            e.presentation.isVisible = token.isNotEmpty()
        } else {
            e.presentation.isVisible = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Open repository action triggered")

        val project = e.project ?: return

        val dialog = OpenRepoDialog(project)
        if (dialog.showAndGet()) {
            val repoStructureJson = dialog.getRepoStructureJson()

            println(repoStructureJson)

            thisLogger().info("Repository structure retrieved: $repoStructureJson")
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
