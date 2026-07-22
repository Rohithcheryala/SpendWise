package com.example.spendwise.data.repository






import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.mapper.SmsMessage
import com.example.spendwise.data.mapper.SmsReadRequest
import javax.inject.Inject

class InboxRepository @Inject constructor(
    private val smsReader: SmsReader,
    private val appMetadataDao: AppMetadataDao
) {

    suspend fun getMessages(): List<SmsMessage> {

        val metadata = appMetadataDao.get()

        return smsReader.read(
            SmsReadRequest(
                from = metadata!!.lastSmsSync
            )
        )
    }
}