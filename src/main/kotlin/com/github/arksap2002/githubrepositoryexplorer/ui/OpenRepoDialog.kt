package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for entering GitHub repository information.
 */
class OpenRepoDialog(private val project: Project) : DialogWrapper(project) {
    private val ownerField = JBTextField(20)
    private val nameField = JBTextField(20)
    private var repoStructureJson: String? = null

    init {
        init()
        title = GithubRepositoryExplorer.message("repoDialog.title")
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel(GithubRepositoryExplorer.message("repoDialog.ownerLabel")),
                ownerField,
                1,
                true
            )
            .addLabeledComponent(
                JBLabel(GithubRepositoryExplorer.message("repoDialog.nameLabel")),
                nameField,
                1,
                true
            )
            .addComponentFillVertically(JPanel(), 0)

        return formBuilder.panel
    }

    /**
     * Returns the repository structure as a JSON string.
     * This is available only after a successful validation.
     */
    fun getRepoStructureJson(): String? = repoStructureJson

    private fun validateRepositoryWithProgress() {
        val owner = ownerField.text.trim()
        val name = nameField.text.trim()
        val token = UserDataService.service(project).token
        val url = "https://api.github.com/repos/$owner/$name/contents/"

        object : Task.Backgroundable(
            project,
            GithubRepositoryExplorer.message("repoDialog.validation.title"),
            false
        ) {
            private var isValid = false
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GithubRepositoryExplorer.message("repoDialog.validation.message")
                
                try {
                    val response = GitHubApiUtils.makeGetRequest(token, url)
                    repoStructureJson = response
                    isValid = true
                } catch (e: Exception) {
                    errorMessage = e.message
                    isValid = false
                }
            }

            override fun onSuccess() {
                if (isValid) {
                    super@OpenRepoDialog.doOKAction()
                } else {
                    Messages.showErrorDialog(
                        project,
                        GithubRepositoryExplorer.message("repoDialog.error.invalidRepo"),
                        GithubRepositoryExplorer.message("repoDialog.error.title")
                    )
                }
            }
        }.queue()
    }

    override fun doOKAction() {
        validateRepositoryWithProgress()
    }
}
