package com.bluemes.app.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluemes.app.databinding.FragmentChatBinding
import com.bluemes.app.ui.chat.adapters.MessageAdapter
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels {
        val address = arguments?.getString("deviceAddress") ?: ""
        val name = arguments?.getString("userName") ?: ""
        ChatViewModelFactory(requireContext(), address, name)
    }

    private lateinit var messageAdapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userName = arguments?.getString("userName") ?: "Chat"
        binding.toolbar.title = userName
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val llm = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        messageAdapter = MessageAdapter()
        binding.recyclerMessages.layoutManager = llm
        binding.recyclerMessages.adapter = messageAdapter

        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.sendMessage(text)
                binding.messageInput.text?.clear()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { messages ->
                    messageAdapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        binding.recyclerMessages.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.connectionState.collect { state ->
                    binding.connectionStatus.text = state
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { typing ->
                    binding.typingIndicator.visibility = if (typing) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
