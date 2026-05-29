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

class HistoryViewModel(ctx: Context) : ViewModel() {
    private val repo = ChatRepository(
        BlueMesApplication.instance.database.conversationDao(),
        BlueMesApplication.instance.database.messageDao()
    )
    private val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = query
        .debounce(200)
        .flatMapLatest { q -> if (q.isBlank()) repo.getAllConversations() else repo.searchConversations(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { query.value = q }
    fun clearAll() { viewModelScope.launch { repo.deleteAll() } }
}

class HistoryViewModelFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(c: Class<T>): T {
        @Suppress("UNCHECKED_CAST") return HistoryViewModel(ctx.applicationContext) as T
    }
}
