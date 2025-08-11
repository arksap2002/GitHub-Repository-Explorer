package com.github.arksap2002.githubrepositoryexplorer.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "UserData", storages = [Storage("UserData.xml")])
class UserDataService : PersistentStateComponent<UserDataState> {
    private var userDataState: UserDataState = UserDataState()

    override fun getState(): UserDataState = userDataState

    override fun loadState(state: UserDataState) {
        userDataState = state
    }

    companion object {
        fun service(project: Project) = project.getService(UserDataService::class.java).state
    }
}

data class UserDataState(var token: String = "")
