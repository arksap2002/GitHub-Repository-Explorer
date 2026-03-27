package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.services.UserDataService
import com.github.arksap2002.githubrepositoryexplorer.utils.FileTreeNode
import com.github.arksap2002.githubrepositoryexplorer.utils.GitHubApiUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.launch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.Messages
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.BinaryLightVirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.ContainerUtil
import java.awt.BorderLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

/**
 * Window for displaying the GitHub repository structure as a tree.
 * When a file is selected, it is opened in the editor as read-only.
 */
class RepoStructureDialog(
    private val project: Project,
    private val rootNodes: List<FileTreeNode>,
    private val owner: String,
    private val name: String,
    private val scope: CoroutineScope,
) : JDialog() {
    private val token = UserDataService.service().token
    private val repoFullName = "$owner/$name"
    private val openedFiles = mutableSetOf<VirtualFile>()

    private val inFlightDirFetches: MutableSet<String> = ContainerUtil.newConcurrentSet()
    private val inFlightFileFetches: MutableSet<String> = ContainerUtil.newConcurrentSet()

    init {
        title = GithubRepositoryExplorer.message("repoStructureDialog.title", repoFullName)
        defaultCloseOperation = DISPOSE_ON_CLOSE

        contentPane = createContentPanel()

        // Set the size and position
        val width = GithubRepositoryExplorer.message("ui.repoStructureDialog.width").toInt()
        val height = GithubRepositoryExplorer.message("ui.repoStructureDialog.height").toInt()
        setSize(width, height)
        setLocationRelativeTo(null)

        thisLogger().info("Repository structure dialog initialized for $repoFullName")

        addCloseWindowListener()
    }

    private fun addCloseWindowListener() {
        addWindowListener(object : WindowAdapter() {
            private fun processWindowClosing() {
                thisLogger().info("Repository structure dialog closed; canceling background tasks and closing files")
                scope.cancel("RepoStructureDialog closed")

                val fem = FileEditorManager.getInstance(project)
                val filesToClose = openedFiles.toList()
                filesToClose.forEach { file ->
                    if (fem.isFileOpen(file)) {
                        fem.closeFile(file)
                    }
                }
                openedFiles.clear()
            }

            override fun windowClosed(e: WindowEvent?) {
                processWindowClosing()
            }

            override fun windowClosing(e: WindowEvent?) {
                processWindowClosing()
            }
        })
    }

    private fun createContentPanel(): JComponent {
        // Build the tree structure by adding only the root level nodes
        val rootNode =
            DefaultMutableTreeNode(GithubRepositoryExplorer.message("repoStructureDialog.rootNode", repoFullName))
        buildTreeNodes(rootNode, rootNodes)

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
        // Ensure only one in-flight task per directory path
        if (!inFlightDirFetches.add(dirPath)) {
            thisLogger().info("Skip duplicate directory fetch for $dirPath")
            return
        }
        scope.launch {
            var children: List<FileTreeNode>? = null
            var success = false
            var errorMessage: String? = null
            withBackgroundProgress(
                project,
                GithubRepositoryExplorer.message("repoStructureDialog.dir.progress.title")
            ) {
                try {
                    val result = GitHubApiUtils.listDirectory(token, owner, name, dirPath)
                    success = result.success
                    children = result.data
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    inFlightDirFetches.remove(dirPath)
                }
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                when {
                    success -> {
                        buildTreeNodes(dirNode, children ?: emptyList())
                        model.nodeStructureChanged(dirNode)
                    }

                    else -> {
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
            }
        }
    }

    private fun openTextFileInEditor(fileName: String, content: String) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        val virtualFile = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, content)
        virtualFile.isWritable = false
        openedFiles.add(virtualFile)
        fileEditorManager.openFile(virtualFile, true)

        thisLogger().info("Opened text file in editor: $fileName")
    }

    private fun openImageInEditor(fileName: String, bytes: ByteArray) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        val virtualFile = BinaryLightVirtualFile(fileName, bytes)
        virtualFile.isWritable = false
        openedFiles.add(virtualFile)
        fileEditorManager.openFile(virtualFile, true)

        thisLogger().info("Opened image file in editor: $fileName")
    }

    private fun isImageFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
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
        // Ensure only one in-flight task per file download URL
        if (!inFlightFileFetches.add(downloadUrl)) {
            return
        }
        scope.launch {
            var textContent: String? = null
            var imageBytes: ByteArray? = null
            var errorMessage: String? = null
            withBackgroundProgress(
                project,
                GithubRepositoryExplorer.message("repoStructureDialog.progress.title")
            ) {
                try {
                    thisLogger().info("Fetching file content from: $downloadUrl")
                    if (isImageFile(fileName)) {
                        val resultBytes = GitHubApiUtils.fetchFileBytes(token, downloadUrl)
                        if (resultBytes.success) {
                            imageBytes = resultBytes.data
                        } else {
                            errorMessage =
                                GithubRepositoryExplorer.message("repoStructureDialog.error.fetchFailed", "")
                        }
                    } else {
                        val resultText = GitHubApiUtils.fetchFileContent(token, downloadUrl)
                        if (resultText.success) {
                            textContent = resultText.data
                        } else {
                            errorMessage =
                                GithubRepositoryExplorer.message("repoStructureDialog.error.fetchFailed", "")
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    inFlightFileFetches.remove(downloadUrl)
                }
            }

            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                when {
                    imageBytes != null -> openImageInEditor(fileName, imageBytes)
                    textContent != null -> openTextFileInEditor(fileName, textContent)
                    else -> {
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
            }
        }
    }
}
