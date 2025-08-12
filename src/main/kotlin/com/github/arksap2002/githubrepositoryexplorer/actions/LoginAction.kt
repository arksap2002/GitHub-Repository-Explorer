package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.ui.LoginDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Action that provides login functionality for GitHub Repository Explorer.
 */
class LoginAction : AnAction() {
    init {
        templatePresentation.text = GithubRepositoryExplorer.message("login")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = !UserDataService.isUserLoggedIn()
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Login action triggered")

        val project = e.project ?: return

        if (UserDataService.isUserLoggedIn()) {
            thisLogger().info("User is already logged in, showing notification")
            Messages.showInfoMessage(
                project,
                GithubRepositoryExplorer.message("login.alreadyLoggedIn.message"),
                GithubRepositoryExplorer.message("login.alreadyLoggedIn.title")
            )
            return
        }

        showLoginDialog(project)
    }

    private fun showLoginDialog(project: Project) {
        val dialog = LoginDialog(project)
        if (dialog.showAndGet()) {
            thisLogger().info("GitHub token saved successfully")
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
