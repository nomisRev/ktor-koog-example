package org.jetbrains.demo.chat

import kotlinx.collections.immutable.PersistentList

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val isStreaming: Boolean = false
)

data class ChatState(
    val messages: PersistentList<ChatMessage>,
    val isLoading: Boolean
)