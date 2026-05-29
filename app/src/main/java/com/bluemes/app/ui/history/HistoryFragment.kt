package com.bluemes.app.ui.history

import android.os.Bundle
import android.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluemes.app.R
import com.bluemes.app.databinding.FragmentHistoryBinding
import com.bluemes.app.ui.history.adapters.ConversationAdapter
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private var _b: FragmentHistoryBinding? = null
    private val b get() = _b!!
    private val vm: HistoryViewModel by viewModels { HistoryViewModelFactory(requireContext()) }
    private lateinit var adapter: ConversationAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentHistoryBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        adapter = ConversationAdapter { conv ->
            findNavController().navigate(R.id.action_history_to_chat, Bundle().apply {
                putString("deviceAddress", conv.deviceAddress)
                putString("userName", conv.userName)
            })
        }
        b.recyclerHistory.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerHistory.adapter = adapter
        b.searchInput.doAfterTextChanged { vm.setQuery(it?.toString() ?: "") }

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_nearby   -> { findNavController().navigate(R.id.action_history_to_nearby); true }
                R.id.nav_history  -> true
                R.id.nav_settings -> { findNavController().navigate(R.id.action_history_to_settings); true }
                else -> false
            }
        }
        b.bottomNav.selectedItemId = R.id.nav_history

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.conversations.collect { list ->
                    adapter.submitList(list)
                    b.emptyState.visibility    = if (list.isEmpty()) View.VISIBLE else View.GONE
                    b.recyclerHistory.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
