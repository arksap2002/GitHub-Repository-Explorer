package com.github.arksap2002.githubrepositoryexplorer.ui

import com.github.arksap2002.githubrepositoryexplorer.GithubRepositoryExplorer
import com.github.arksap2002.githubrepositoryexplorer.utils.FileTreeNode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import kotlinx.serialization.json.Json
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Dialog for displaying the GitHub repository structure as a tree.
 */
class RepoStructureDialog(
    project: Project,
    private val repoStructureJson: String,
    private val repoName: String
) : DialogWrapper(project) {

    init {
        init()
        title = GithubRepositoryExplorer.message("repoStructureDialog.title", repoName)
        thisLogger().info("Repository structure dialog initialized for $repoName")
    }

    override fun createCenterPanel(): JComponent {
        // Parse the JSON string into a list of FileTreeNode objects
        val json = Json { ignoreUnknownKeys = true }
        val fileTreeNodes = json.decodeFromString<List<FileTreeNode>>(repoStructureJson)

        // Build the tree structure by recursively adding nodes
        val rootNode = DefaultMutableTreeNode("$repoName Repository")
        buildTreeNodes(rootNode, fileTreeNodes)

        // Create the tree with the root node and make it visible
        val tree = Tree(DefaultTreeModel(rootNode))
        tree.isRootVisible = true

        // Create a scroll pane for the tree
        val scrollPane = JBScrollPane(tree)
        val width = GithubRepositoryExplorer.message("ui.repoStructureDialog.width").toInt()
        val height = GithubRepositoryExplorer.message("ui.repoStructureDialog.height").toInt()
        scrollPane.preferredSize = Dimension(width, height)

        return scrollPane
    }

    private fun buildTreeNodes(parentNode: DefaultMutableTreeNode, fileTreeNodes: List<FileTreeNode>) {
        val sortedNodes = fileTreeNodes.sortedWith(
            compareBy<FileTreeNode> { it.type != "dir" }.thenBy { it.name.lowercase() }
        )
        for (node in sortedNodes) {
            val treeNode = DefaultMutableTreeNode(node.name)
            parentNode.add(treeNode)

            // If this is a directory with children, add its children recursively
            if (node.type == "dir" && node.children.isNotEmpty()) {
                buildTreeNodes(treeNode, node.children)
            }
        }
    }
}
