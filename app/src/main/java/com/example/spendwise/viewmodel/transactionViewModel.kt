package com.example.spendwise.viewmodel

import androidx.lifecycle.ViewModel
import com.example.spendwise.ui.screens.transaction.TransactionUiState

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TransactionViewModel @Inject constructor(
    // Inject your use cases or repositories here later
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState = _uiState.asStateFlow()

    // This single function handles all 10 inputs from the screen!
    fun updateState(transform: (TransactionUiState) -> TransactionUiState) {
        _uiState.update { current -> transform(current) }
    }
}
