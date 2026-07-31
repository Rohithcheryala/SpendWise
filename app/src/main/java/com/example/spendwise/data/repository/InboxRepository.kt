package com.example.spendwise.data.repository


import android.content.ContentResolver
import android.provider.Telephony
import com.example.spendwise.core.messages.MessageReader
import com.example.spendwise.data.mapper.SmsMessage
import javax.inject.Inject

class InboxRepository @Inject constructor(
    private val messageReader: MessageReader,
    private val appMetadataRepository: AppMetadataRepository,
    private val transactionRepository: TransactionRepository,
    private val contentResolver: ContentResolver
) {

    suspend fun getMessages(): List<SmsMessage> {

        val metadata = appMetadataRepository.get()

        val lastSyncTime = metadata!!.lastSmsSync

        return readFake(
                from = lastSyncTime

        )
    }

    private fun readReal(
        from: Long,
        to: Long = System.currentTimeMillis()
    ): List<SmsMessage> {

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

        val messages = mutableListOf<SmsMessage>()

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

                messages += SmsMessage(
                    id = cursor.getLong(idColumn),
                    address = cursor.getString(addressColumn).orEmpty(),
                    body = cursor.getString(bodyColumn).orEmpty(),
                    date = cursor.getLong(dateColumn)
                )
            }
        }


        return messages
    }

    private fun readFake(
        from: Long,
        to: Long = System.currentTimeMillis()
    ): List<SmsMessage> {
        val messages2 = listOf(

            // SBI
            SmsMessage(
                id = 1,
                address = "SBIINB",
                body = "Rs.1,250.00 debited from A/c XX4821 on 26-Jul-26 by UPI/PAYTM. Avl Bal Rs.48,921.30",
                date = System.currentTimeMillis() - 1000L * 60 * 5
            ),

            SmsMessage(
                id = 2,
                address = "SBIINB",
                body = "Rs.5,499.00 spent on your SBI Debit Card ending 4821 at AMAZON PAY INDIA on 25-Jul-26. Avl Bal Rs.50,171.30",
                date = System.currentTimeMillis() - 1000L * 60 * 60
            ),

            SmsMessage(
                id = 3,
                address = "SBIINB",
                body = "Rs.35,000.00 credited to A/c XX4821 via NEFT from INFOSYS LTD. Avl Bal Rs.55,670.30",
                date = System.currentTimeMillis() - 1000L * 60 * 60 * 8
            ),

            // HDFC
            SmsMessage(
                id = 4,
                address = "HDFCBK",
                body = "Rs.235.50 debited from A/c XX9021 on 26-07-26 towards UPI to SWIGGY. Avl Bal Rs.12,842.65",
                date = System.currentTimeMillis() - 1000L * 60 * 20
            ),

            SmsMessage(
                id = 5,
                address = "HDFCBK",
                body = "INR 2,499.00 spent on HDFC Bank Credit Card ending 7712 at FLIPKART on 25-07-26.",
                date = System.currentTimeMillis() - 1000L * 60 * 60 * 3
            ),

            SmsMessage(
                id = 6,
                address = "HDFCBK",
                body = "Rs.15,000.00 credited to A/c XX9021 by IMPS from JOHN K. Avl Bal Rs.28,078.15",
                date = System.currentTimeMillis() - 1000L * 60 * 60 * 10
            ),

            // Axis
            SmsMessage(
                id = 7,
                address = "AXISBK",
                body = "INR 899.00 spent using Axis Bank Debit Card XX1345 at ZOMATO on 26-Jul-26. Avl Bal INR 19,881.42",
                date = System.currentTimeMillis() - 1000L * 60 * 15
            ),

            SmsMessage(
                id = 8,
                address = "AXISBK",
                body = "Rs.18,750.00 credited to A/c XX1345 via UPI from ACME TECHNOLOGIES. Avl Bal Rs.20,780.42",
                date = System.currentTimeMillis() - 1000L * 60 * 60 * 6
            ),

            SmsMessage(
                id = 9,
                address = "AXISBK",
                body = "Rs.420.00 debited from A/c XX1345 towards FASTag recharge. Avl Bal Rs.20,360.42",
                date = System.currentTimeMillis() - 1000L * 60 * 60 * 12
            ),

            // Another variation
            SmsMessage(
                id = 10,
                address = "HDFCBK",
                body = "UPI transaction of Rs.1,899.00 to BIGBASKET successful from A/c XX9021 on 26-Jul-26. Avl Bal Rs.10,943.65",
                date = System.currentTimeMillis()
            )
        )

        return messages2
    }
}