package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.ui.Messages

/**
 * Action that provides logout functionality for GitHub Repository Explorer.
 */
class LogoutAction : AnAction() {
    init {
        templatePresentation.text = GithubRepositoryExplorer.message("logout")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = UserDataService.isUserLoggedIn()
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Logout action triggered")

        val project = e.project ?: return

        // Check if a user is not logged in
        if (!UserDataService.isUserLoggedIn()) {
            thisLogger().info("User is not logged in, nothing to do")
            return
        }

        // Clear the GitHub token
        UserDataService.service().token = ""
        thisLogger().info("GitHub token cleared successfully")

        // Show a success message to the user
        Messages.showInfoMessage(
            project,
            GithubRepositoryExplorer.message("logout.success.message"),
            GithubRepositoryExplorer.message("logout.success.title")
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
