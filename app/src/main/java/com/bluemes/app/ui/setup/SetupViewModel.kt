package com.bluemes.app.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete

    fun saveUserName(context: Context, name: String) {
        viewModelScope.launch {
            val prefs = UserPreferences(context)
            prefs.setUserName(name.trim())
            prefs.setSetupDone(true)
            _setupComplete.value = true
        }
    }
}
