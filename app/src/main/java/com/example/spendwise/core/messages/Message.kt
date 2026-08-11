package com.example.spendwise.core.messages


data class Message(
    val id: Long,
    val address: String?,
    val body: String?,
    val date: Long
)