package com.example.spendwise.data.repository


import com.example.spendwise.core.parser_pw.bank.BankParserFactory
import com.example.spendwise.data.database.dao.TransactionDao
import com.example.spendwise.data.database.entity.TransactionEntity
import java.util.Date
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val smsRepository: SmsRepository,
    private val bankParserFactory: BankParserFactory
) {

    val transactions = transactionDao.getAll()

    suspend fun insert(transaction: TransactionEntity) {
        transactionDao.insert(transaction)
    }

    suspend fun syncFromSms(fromDate: Date) {
        val messages = smsRepository.readSince(fromDate.time)

        messages.mapNotNull { sms ->
            val parser = BankParserFactory.getParser(sms.address ?: return@mapNotNull null)

            parser?.parse(
                smsBody = sms.body ?: return@mapNotNull null,
                sender = sms.address,
                timestamp = sms.date
            )
        }

//        parsedTransactions.forEach { transaction ->
//
//            val exists = this.existsByUniqueId(
//                transaction.uniqueId
//            )
//
//            if (!exists) {
//                this.insert(
//                    transaction.toEntity(
//                        status = TransactionStatus.BUFFER
//                    )
//                )
//            }
//        }
//    }
    }
}