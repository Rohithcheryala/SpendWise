package com.example.spendwise.core.messages

import android.net.Uri


data class Message(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri?
)