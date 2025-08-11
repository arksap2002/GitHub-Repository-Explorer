package com.github.arksap2002.githubrepositoryexplorer.actions

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Action that provides login functionality for GitHub Repository Explorer.
 * This action is registered in the Tools menu.
 */
class LoginAction : AnAction() {
    
    init {
        templatePresentation.text = GithubRepositoryExplorer.message("login")
        templatePresentation.description = "Log in to GitHub"
    }

    override fun actionPerformed(e: AnActionEvent) {
        thisLogger().info("Login action triggered")
        println("Login action triggered from Tools menu")
    }
}
