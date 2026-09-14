package com.example.spendwise.data.database


import com.example.spendwise.data.database.dao.AccountDao
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.data.mapper.AppMetadata
import com.example.spendwise.data.repository.AppMetadataRepository
import com.example.spendwise.ledger.service.LedgerService
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val accountDao: AccountDao,
    private val appMetadataRepository: AppMetadataRepository,
) {

    suspend fun seed() {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")


        if (accountDao.count() == 0) {
            // Default category accounts (categories ARE accounts now).
            listOf("Food", "Transport", "Shopping").forEach { name ->
                accountDao.insert(
                    AccountEntity(
                        name = name,
                        accountClass = LedgerService.CLASS_EXPENSE,
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }

        if (appMetadataRepository.count() == 0) {
            appMetadataRepository.insert(
                AppMetadata(
                    id = 1,
                    databaseId = "1",
                    revision = 1,
                    trackingStartDate = LocalDate
                        .parse("01-05-2026", formatter)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                    lastSmsSync = LocalDate
                        .parse("01-05-2026", formatter)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                    lastBackupTime = 123
                )
            )
        }
    }

}