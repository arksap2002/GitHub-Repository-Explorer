package com.github.arksap2002.githubrepositoryexplorer.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Service responsible for managing user-specific data, such as a GitHub token, at the application level.
 */
@Service(Service.Level.APP)
@State(name = "UserData", storages = [Storage("UserData.xml")])
class UserDataService : PersistentStateComponent<UserDataState> {
    private var userDataState: UserDataState = UserDataState()

    override fun getState(): UserDataState = userDataState

    override fun loadState(state: UserDataState) {
        userDataState = state
    }

    companion object {
        fun service() = com.intellij.openapi.application.ApplicationManager.getApplication()
            .getService(UserDataService::class.java).state

        /**
         * Checks if a user is currently logged in by verifying if the token is not empty.
         * @return true if the user is logged in, false otherwise
         */
        fun isUserLoggedIn(): Boolean {
            val token = service().token
            return token.isNotEmpty()
        }
    }
}

data class UserDataState(var token: String = "")
