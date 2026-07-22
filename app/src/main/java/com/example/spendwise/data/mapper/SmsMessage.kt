package com.example.spendwise.data.mapper

data class SmsMessage(
    val id: Long,
    val address: String?,
    val body: String?,
    val date: Long
)


data class SmsReadRequest(
    val from: Long,
    val to: Long = System.currentTimeMillis()
)