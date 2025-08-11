package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
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
    private val tokenField = JBTextField(40)

    init {
        init()

        title = GithubRepositoryExplorer.message("loginDialog.title")

        val existingToken = UserDataService.service(project).token
        if (existingToken.isNotEmpty()) {
            tokenField.text = existingToken
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

    /**
     * Validates the GitHub token by making a request to the GitHub API.
     *
     * @param token The GitHub token to validate
     * @return true if the token is valid, false otherwise
     */
    private fun isTokenValid(token: String): Boolean {
        return GitHubApiUtils.isTokenValid(token)
    }

    override fun doOKAction() {
        val token = tokenField.text.trim()
        project.getService(UserDataService::class.java).state.token = token
        close(OK_EXIT_CODE)
    }
}
