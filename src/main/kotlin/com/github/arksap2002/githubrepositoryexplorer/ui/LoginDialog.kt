package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope

/**
 * Dialog for entering GitHub token.
 */

class LoginDialog(private val project: Project, private val scope: CoroutineScope) : DialogWrapper(project) {
    private val tokenField = JBTextField(GithubRepositoryExplorer.message("ui.textField.tokenSize").toInt())

    init {
        init()

        // Set the dialog title
        title = GithubRepositoryExplorer.message("loginDialog.title")
        thisLogger().info("Login dialog initialized")

        // Load existing token if available
        val existingToken = UserDataService.service().token
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
        if (token.isEmpty()) {
            return ValidationInfo(GithubRepositoryExplorer.message("loginDialog.error.invalidToken"), tokenField)
        }
        return null
    }

    override fun doOKAction() {
        val token = tokenField.text.trim()

        object : Task.Backgroundable(
            project,
            "Validating token",
            false
        ) {
            private var isValid: Boolean = false
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Validating token..."
                thisLogger().info("Validating GitHub token in background")
                try {
                    isValid = GitHubApiUtils.isTokenValid(scope, token)
                    if (isValid) thisLogger().info("Token validation successful") else thisLogger().warn("Token validation failed")
                } catch (e: Exception) {
                    errorMessage = e.message
                    thisLogger().warn("Token validation failed with exception: ${e.message}")
                }
            }

            override fun onSuccess() {
                if (isValid) {
                    UserDataService.service().token = token
                    thisLogger().info("GitHub token saved successfully")
                    close(OK_EXIT_CODE)
                } else {
                    Messages.showErrorDialog(
                        project,
                        GithubRepositoryExplorer.message("loginDialog.error.invalidToken"),
                        GithubRepositoryExplorer.message("loginDialog.title")
                    )
                }
            }
        }.queue()
    }

