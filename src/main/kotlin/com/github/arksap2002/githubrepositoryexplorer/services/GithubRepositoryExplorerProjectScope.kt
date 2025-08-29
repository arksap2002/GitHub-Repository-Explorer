package com.github.arksap2002.githubrepositoryexplorer.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level service that provides access to the IntelliJ-managed [CoroutineScope]
 * and utility to create child scopes tied to the project lifetime.
 *
 * See: https://plugins.jetbrains.com/docs/intellij/launching-coroutines.html#launching-coroutine-from-service-scope
 */
@Service(Service.Level.PROJECT)
class GithubRepositoryExplorerProjectScope(private val project: Project, private val scope: CoroutineScope) {
    /**
     * Creates a new child coroutine scope.
     */
    fun childScope(
        name: String, context: CoroutineContext = EmptyCoroutineContext, supervisor: Boolean = true
    ): CoroutineScope = scope.childScope(name, context, supervisor)
}
