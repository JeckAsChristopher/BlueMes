package com.bluemes.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.data.repository.ChatRepository
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(ctx: Context) : ViewModel() {
    private val prefs = UserPreferences(ctx)
    private val repo  = ChatRepository(
        BlueMesApplication.instance.database.conversationDao(),
        BlueMesApplication.instance.database.messageDao()
    )

    val userName: StateFlow<String?> = prefs.userName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val isDark:   StateFlow<Boolean> = prefs.isDarkMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setName(n: String)        { viewModelScope.launch { prefs.setUserName(n) } }
    fun setDark(on: Boolean)      { viewModelScope.launch { prefs.setDarkMode(on) } }
    fun clearHistory()            { viewModelScope.launch { repo.deleteAll() } }
}

class SettingsViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return SettingsViewModel(ctx.applicationContext) as T
    }
}
