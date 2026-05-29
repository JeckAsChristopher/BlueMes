package com.bluemes.app.ui.chat

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
import com.bluemes.app.databinding.FragmentChatBinding
import com.bluemes.app.ui.chat.adapters.MessageAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {
    private var _b: FragmentChatBinding? = null
    private val b get() = _b!!

    private val vm: ChatViewModel by viewModels {
        ChatViewModelFactory(
            requireContext(),
            arguments?.getString("deviceAddress") ?: "",
            arguments?.getString("userName") ?: "User"
        )
    }

    private lateinit var msgAdapter: MessageAdapter
    private var typingJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentChatBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.toolbar.title = arguments?.getString("userName") ?: "Chat"
        b.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val llm = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        msgAdapter = MessageAdapter()
        b.recyclerMessages.layoutManager = llm
        b.recyclerMessages.adapter = msgAdapter

        // Send on button tap
        b.sendButton.setOnClickListener {
            val txt = b.messageInput.text.toString().trim()
            if (txt.isNotEmpty()) {
                vm.sendMessage(txt)
                b.messageInput.text?.clear()
            }
        }

        // Typing indicator: debounce 1.5 s after last keystroke
        b.messageInput.doAfterTextChanged { text ->
            typingJob?.cancel()
            if (!text.isNullOrBlank()) {
                vm.sendTyping(true)
                typingJob = viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(1500)
                    vm.sendTyping(false)
                }
            } else {
                vm.sendTyping(false)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.messages.collect { msgs ->
                    msgAdapter.submitList(msgs)
                    if (msgs.isNotEmpty())
                        b.recyclerMessages.smoothScrollToPosition(msgs.size - 1)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.connectionState.collect { state -> b.connectionStatus.text = state }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.isTyping.collect { typing ->
                    b.typingIndicator.visibility = if (typing) View.VISIBLE else View.GONE
                }
            }
        }

        // Show snackbar when remote user denies the connection
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.denied.collect { name ->
                    if (name != null) {
                        Snackbar.make(b.root, "$name refused to chat with you.", Snackbar.LENGTH_LONG).show()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
