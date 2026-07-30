package com.example.spendwise.core.contacts


import android.net.Uri

data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri?
)