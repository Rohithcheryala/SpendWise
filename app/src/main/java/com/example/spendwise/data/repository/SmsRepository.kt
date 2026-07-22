package com.example.spendwise.data.repository




import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.data.mapper.SmsReadRequest
import javax.inject.Inject

class SmsRepository @Inject constructor(
    private val reader: SmsReader
) {

    suspend fun readSince(
        from: Long
    ): List<SmsMessage> {

        return reader.read(
            SmsReadRequest(from)
        )
    }
}

