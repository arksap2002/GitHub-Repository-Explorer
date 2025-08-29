package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

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
        isOKActionEnabled = false

        val token = tokenField.text.trim()

        var isValid = false
        var errorMessage: String? = null

        scope.launch {
            // Create a background task for log-in validation
            withBackgroundProgress(project, GithubRepositoryExplorer.message("loginDialog.validation.title")) {
                try {
                    isValid = GitHubApiUtils.isTokenValid(token)
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                isOKActionEnabled = true
                if (isValid) {
                    // Handle successful validation
                    thisLogger().info("GitHub token validation successful")
                    UserDataService.service().token = token
                    close(OK_EXIT_CODE)
                } else {
                    // Show error message for failed validation
                    thisLogger().info("GitHub token validation failed")
                    val message = errorMessage ?: GithubRepositoryExplorer.message("loginDialog.error.invalidToken")
                    Messages.showErrorDialog(
                        project,
                        message,
                        GithubRepositoryExplorer.message("loginDialog.title")
                    )
                }
            }
        }
    }

    override fun doCancelAction() {
        thisLogger().info("Login dialog canceled by user; canceling background tasks")
        scope.cancel("Login dialog canceled")
        super.doCancelAction()
    }
}
