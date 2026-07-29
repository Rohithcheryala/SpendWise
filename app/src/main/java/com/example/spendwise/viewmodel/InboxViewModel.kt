package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.data.repository.InboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: InboxRepository
) : ViewModel() {

    data class InboxUiState(
        val isLoading: Boolean = true,
        val messages: List<SmsMessage> = emptyList(),
        val error: String? = null
    )

    var uiState by mutableStateOf(InboxUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null
            )

            runCatching {

                repository.getMessages()

            }.onSuccess { messages ->

                uiState = uiState.copy(
                    isLoading = false,
                    messages = messages
                )

            }.onFailure {

                uiState = uiState.copy(
                    isLoading = false,
                    error = it.message
                )
            }
        }
    }
}

