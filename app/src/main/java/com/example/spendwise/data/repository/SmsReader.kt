package com.example.spendwise.data.repository


import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.data.mapper.SmsReadRequest
import javax.inject.Inject

class SmsReader @Inject constructor(
    private val source: SmsContentResolverSource
) {

    suspend fun read(
        request: SmsReadRequest
    ): List<SmsMessage> {

        return source.read(
            from = request.from,
            to = request.to
        )
    }
}