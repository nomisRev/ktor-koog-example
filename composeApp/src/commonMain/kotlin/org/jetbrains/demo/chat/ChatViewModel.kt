package org.jetbrains.demo.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.demo.chat.repository.ChatRepository

class ChatViewModel(
    private val chatRepository: ChatRepository,
    base: co.touchlab.kermit.Logger
) : ViewModel() {
    private val logger = base.withTag("ChatViewModel")

    private val _state = MutableStateFlow(ChatState(persistentListOf(), false))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun sendMessage(message: String) {
        if (message.isBlank() || _state.value.isLoading) return

        logger.d("ChatViewModel: Sending message")

        // Add user message to the list
        val userMessage = ChatMessage(message, true)
        updateMessages(_state.value.messages.add(userMessage))

        // Set loading state
        _state.value = _state.value.copy(isLoading = true)

        viewModelScope.launch {
            try {
                // Add a placeholder message for the AI response
                val aiMessageIndex = _state.value.messages.size
                updateMessages(_state.value.messages.add(ChatMessage("", false, isStreaming = true)))

                var aiResponse = ""
                chatRepository.sendMessage(message).collect { token ->
                    aiResponse += token
                    // Update the AI message with accumulated response
                    val updatedMessages = _state.value.messages.builder().apply {
                        set(aiMessageIndex, ChatMessage(aiResponse, false, isStreaming = true))
                    }.build()
                    updateMessages(updatedMessages)
                }

                // Mark streaming as complete
                val finalMessages = _state.value.messages.builder().apply {
                    add(aiMessageIndex, ChatMessage(aiResponse, false, isStreaming = false))
                }.build()
                updateMessages(finalMessages)
            } catch (e: Exception) {
                val errorText = e.message ?: e.cause?.message ?: "Unknown error"
                logger.e("Error during chat: $errorText", e)
                updateMessages(_state.value.messages.add(ChatMessage("Error: $errorText", false)))
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun updateMessages(messages: PersistentList<ChatMessage>) {
        _state.value = _state.value.copy(messages = messages)
    }
}
