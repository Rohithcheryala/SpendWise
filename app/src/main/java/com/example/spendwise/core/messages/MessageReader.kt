package com.example.spendwise.core.messages

import android.content.ContentResolver
import android.provider.Telephony
import java.time.Instant
import javax.inject.Inject

class MessageReader @Inject constructor(
    private val contentResolver: ContentResolver
) {

    fun read(
        from: Long,
        to: Long = Instant.now().toEpochMilli()
    ): List<Message> {

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        val selection =
            "${Telephony.Sms.DATE} BETWEEN ? AND ?"

        val args = arrayOf(
            from.toString(),
            to.toString()
        )

        val messages = mutableListOf<Message>()

        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            selection,
            args,
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->

            val idColumn =
                cursor.getColumnIndexOrThrow(Telephony.Sms._ID)

            val addressColumn =
                cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)

            val bodyColumn =
                cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)

            val dateColumn =
                cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {

                messages += Message(
                    id = cursor.getLong(idColumn),
                    address = cursor.getString(addressColumn).orEmpty(),
                    body = cursor.getString(bodyColumn).orEmpty(),
                    date = cursor.getLong(dateColumn)
                )
            }
        }

        return messages
    }

    fun readSince(
        from: Long
    ): List<Message> {

        return this.read(from)
    }
}

