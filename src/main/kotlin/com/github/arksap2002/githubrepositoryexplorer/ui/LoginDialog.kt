package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for entering GitHub token.
 */
class LoginDialog(private val project: Project) : DialogWrapper(project) {
    private val tokenField = JBTextField(GithubRepositoryExplorer.message("ui.textField.tokenSize").toInt())

    init {
        init()

        title = GithubRepositoryExplorer.message("loginDialog.title")
        thisLogger().info("Login dialog initialized")

        val existingToken = UserDataService.service(project).token
        if (existingToken.isNotEmpty()) {
            tokenField.text = existingToken
            thisLogger().info("Existing token loaded")
        }
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel(GithubRepositoryExplorer.message("loginDialog.tokenLabel")),
                tokenField,
                1,
                true
            )
            .addComponentFillVertically(JPanel(), 0)

        return formBuilder.panel
    }

    override fun doValidate(): ValidationInfo? {
        val token = tokenField.text.trim()

        if (!isTokenValid(token)) {
            return ValidationInfo(GithubRepositoryExplorer.message("loginDialog.error.invalidToken"), tokenField)
        }

        return null
    }

    private fun isTokenValid(token: String): Boolean {
        val isValid = GitHubApiUtils.isTokenValid(token)
        if (isValid) {
            thisLogger().info("Token validation successful")
        } else {
            thisLogger().warn("Token validation failed")
        }
        return isValid
    }

    override fun doOKAction() {
        val token = tokenField.text.trim()
        project.getService(UserDataService::class.java).state.token = token
        thisLogger().info("GitHub token saved successfully")
        close(OK_EXIT_CODE)
    }
}
