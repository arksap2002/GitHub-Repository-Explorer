package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.ui.OpenRepoDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

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
        val project = e.project ?: return

        val dialog = OpenRepoDialog(project)
        if (dialog.showAndGet()) {
            val repoStructureJson = dialog.getRepoStructureJson()

            println(repoStructureJson)
        }
    }
}
