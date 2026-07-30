package com.example.spendwise.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.core.contacts.ContactProvider
import com.example.spendwise.ui.screens.friends.FriendUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val contactProvider: ContactProvider
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val friends: List<FriendUi> = emptyList(),
        val error: String? = null
    )

    var uiState by mutableStateOf(UiState())
        private set

    init {
        load()
    }

    private fun load() = viewModelScope
        .launch {

        uiState = uiState.copy(isLoading = true)

        try {

            val contacts = contactProvider.getContacts()

            uiState = uiState.copy(
                isLoading = false,
                friends = contacts.map {
                    FriendUi(
                        id = it.id,
                        name = it.name,
                        amountGiven = 0.0,
                        amountReceived = 0.0
                    )
                }
            )

        } catch (e: Exception) {

            uiState = uiState.copy(
                isLoading = false,
                error = e.message
            )
        }
    }
}