package com.github.arksap2002.githubrepositoryexplorer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileTreeNodeTest {
    @Test
    fun `toString returns name`() {
        val node = FileTreeNode(name = "README.md", path = "README.md", type = "file", download_url = "http://example")
        assertEquals("README.md", node.toString())
    }

    @Test
    fun `isProcessed is false by default`() {
        val node = FileTreeNode(name = "src", path = "src", type = "dir")
        assertFalse(node.isProcessed)
    }
}
