package com.bluemes.app.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluemes.app.utils.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {
    private val _done = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _done

    fun save(context: Context, name: String) {
        viewModelScope.launch {
            val p = UserPreferences(context)
            p.setUserName(name.trim())
            p.setSetupDone(true)
            _done.value = true
        }
    }
}
