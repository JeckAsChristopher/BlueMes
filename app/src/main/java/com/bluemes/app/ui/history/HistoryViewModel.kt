package com.bluemes.app.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bluemes.app.BlueMesApplication
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(context: Context) : ViewModel() {

    private val db = BlueMesApplication.instance.database
    private val repo = ChatRepository(db.conversationDao(), db.messageDao())

    private val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = searchQuery
        .debounce(200)
        .flatMapLatest { query ->
            if (query.isBlank()) repo.getAllConversations()
            else repo.searchConversations(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            repo.deleteAllHistory()
        }
    }
}

class HistoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HistoryViewModel(context.applicationContext) as T
    }
}
