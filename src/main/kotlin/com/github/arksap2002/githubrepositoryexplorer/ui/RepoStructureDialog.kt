package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.FileTreeNode
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlinx.serialization.json.Json

/**
 * Window for displaying the GitHub repository structure as a tree.
 * When a file is selected, it is opened in the editor as read-only.
 */
class RepoStructureDialog(
    private val project: Project,
    private val repoStructureJson: String,
    private val repoName: String
) : JDialog() {
    private val token = UserDataService.service().token
    private val baseContentsUrl = "https://api.github.com/repos/${repoName}/contents/"

    private val emptyNode = DefaultMutableTreeNode("...")

    init {
        title = GithubRepositoryExplorer.message("repoStructureDialog.title", repoName)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        contentPane = createContentPanel()

        // Set the size and position
        val width = GithubRepositoryExplorer.message("ui.repoStructureDialog.width").toInt()
        val height = GithubRepositoryExplorer.message("ui.repoStructureDialog.height").toInt()
        setSize(width, height)
        setLocationRelativeTo(null)

        thisLogger().info("Repository structure dialog initialized for $repoName")
    }

    private fun createContentPanel(): JComponent {
        val json = Json { ignoreUnknownKeys = true }
        val fileTreeNodes = json.decodeFromString<List<FileTreeNode>>(repoStructureJson)

        // Build the tree structure by adding only the root level nodes (shallow)
        val rootNode =
            DefaultMutableTreeNode(GithubRepositoryExplorer.message("repoStructureDialog.rootNode", repoName))
        buildTreeNodes(rootNode, fileTreeNodes)

        // Create the tree with the root node and make it visible
        val tree = Tree(DefaultTreeModel(rootNode))
        tree.isRootVisible = true
        addTreeSelectionListener(tree)
        addTreeWillExpandLazyLoader(tree)

        // Create a panel to hold the scroll pane
        val panel = JPanel(BorderLayout())
        val scrollPane = JBScrollPane(tree)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    private fun buildTreeNodes(parentNode: DefaultMutableTreeNode, fileTreeNodes: List<FileTreeNode>) {
        if (parentNode.userObject is FileTreeNode) {
            (parentNode.userObject as FileTreeNode).isProcessed = true
        }
        parentNode.removeAllChildren()

        val sortedNodes = fileTreeNodes.sortedWith(
            compareBy<FileTreeNode> { it.type != "dir" }.thenBy { it.name.lowercase() }
        )

        for (node in sortedNodes) {
            val treeNode = DefaultMutableTreeNode(node)
            parentNode.add(treeNode)

            if (node.type == "dir") {
                treeNode.add(DefaultMutableTreeNode("..."))
            }
        }
    }

    private fun addTreeWillExpandLazyLoader(tree: Tree) {
        tree.addTreeWillExpandListener(object : TreeWillExpandListener {
            override fun treeWillExpand(event: TreeExpansionEvent) {
                val component = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val userObject = component.userObject
                if (userObject is FileTreeNode && userObject.type == "dir") {
                    val dirPath = userObject.path
                    if (!userObject.isProcessed) {
                        val model = tree.model as DefaultTreeModel
                        fetchDirectoryChildrenWithProgress(model, component, dirPath)
                    }
                }
            }

            override fun treeWillCollapse(event: TreeExpansionEvent) {}
        })
    }

    private fun fetchDirectoryChildrenWithProgress(
        model: DefaultTreeModel,
        dirNode: DefaultMutableTreeNode,
        dirPath: String
    ) {
        object : Task.Backgroundable(
            project,
            GithubRepositoryExplorer.message("repoStructureDialog.dir.progress.title"),
            false
        ) {
            private var children: List<FileTreeNode>? = null
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GithubRepositoryExplorer.message("repoStructureDialog.dir.progress.text")
                thisLogger().info("Fetching directory children from: $baseContentsUrl$dirPath")
                try {
                    children = GitHubApiUtils.listDirectory(token, baseContentsUrl, dirPath)
                    thisLogger().info("Directory children fetched successfully: $dirPath")
                } catch (e: Exception) {
                    errorMessage = e.message
                    thisLogger().warn("Failed to fetch directory children: ${e.message}")
                }
            }

            override fun onSuccess() {
                if (children != null) {
                    buildTreeNodes(dirNode, children!!)
                    model.nodeStructureChanged(dirNode)
                } else {
                    Messages.showErrorDialog(
                        project,
                        GithubRepositoryExplorer.message(
                            "repoStructureDialog.error.dirFetchFailed",
                            errorMessage ?: "Unknown error"
                        ),
                        GithubRepositoryExplorer.message("repoStructureDialog.error.title")
                    )
                }
            }
        }.queue()
    }

    private fun openFileInEditor(fileName: String, content: String) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        val virtualFile = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, content)
        virtualFile.isWritable = false
        fileEditorManager.openFile(virtualFile, true)

        thisLogger().info("Opened file in editor: $fileName")
    }

    private fun addTreeSelectionListener(tree: Tree) {
        tree.addTreeSelectionListener(object : TreeSelectionListener {
            override fun valueChanged(e: TreeSelectionEvent) {
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                val userObject = node.userObject

                if (userObject is FileTreeNode) {
                    // If it's a file, fetch content and open in editor
                    if (userObject.type == "file") {
                        val downloadUrl = userObject.download_url
                        if (downloadUrl != null) {
                            fetchFileContentWithProgress(downloadUrl, userObject.name)
                        } else {
                            Messages.showErrorDialog(
                                project,
                                GithubRepositoryExplorer.message("repoStructureDialog.error.noDownloadUrl"),
                                GithubRepositoryExplorer.message("repoStructureDialog.error.title")
                            )
                        }
                    }
                }
            }
        })
    }

    private fun fetchFileContentWithProgress(downloadUrl: String, fileName: String) {
        object : Task.Backgroundable(
            project,
            GithubRepositoryExplorer.message("repoStructureDialog.progress.title"),
            false
        ) {
            private var content: String? = null
            private var errorMessage: String? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = GithubRepositoryExplorer.message("repoStructureDialog.progress.text")
                thisLogger().info("Fetching file content from: $downloadUrl")

                try {
                    content = GitHubApiUtils.fetchFileContent(token, downloadUrl)
                    thisLogger().info("File content fetched successfully")
                } catch (e: Exception) {
                    errorMessage = e.message
                    thisLogger().warn("Failed to fetch file content: ${e.message}")
                }
            }

            override fun onSuccess() {
                if (content != null) {
                    // Open the file in the editor
                    openFileInEditor(fileName, content!!)
                } else {
                    // Show error message
                    Messages.showErrorDialog(
                        project,
                        GithubRepositoryExplorer.message(
                            "repoStructureDialog.error.fetchFailed",
                            errorMessage ?: "Unknown error"
                        ),
                        GithubRepositoryExplorer.message("repoStructureDialog.error.title")
                    )
                }
            }
        }.queue()
    }
}
