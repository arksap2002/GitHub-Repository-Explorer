package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.ui.LoginDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project

/**
 * Action that provides login functionality for GitHub Repository Explorer.
 */
class LoginAction : AnAction() {
    init {
        templatePresentation.text = GithubRepositoryExplorer.message("login")
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Login action triggered")
        
        val project = e.project ?: return
        showLoginDialog(project)
    }
    
    private fun showLoginDialog(project: Project) {
        val dialog = LoginDialog(project)
        if (dialog.showAndGet()) {
            thisLogger().info("GitHub token saved successfully")
        }
    }
}
