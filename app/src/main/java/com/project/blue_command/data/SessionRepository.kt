package com.project.blue_command.data

import com.project.blue_command.model.CombatGroup
import com.project.blue_command.model.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data object SessionRepository {
    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()
    private val _activeGroup = MutableStateFlow<CombatGroup?>(null)
    val activeGroup: StateFlow<CombatGroup?> = _activeGroup.asStateFlow()

    fun setUser(user: UserAccount?) {
        _currentUser.value = user
    }

    fun setActiveGroup(group: CombatGroup?) {
        _activeGroup.value = group
    }

    fun clearSession() {
        _currentUser.value = null
        _activeGroup.value = null
    }
}