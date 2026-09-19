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
            // Default category accounts (categories ARE accounts now). Names are
            // chosen to hit BudgetViewModel.iconFor's keyword map (food/groceries/
            // shopping → restaurant, rent → home, transport → car) so every
            // seeded category renders with a fitting icon out of the box.
            // A few sub-categories ship too (same row type, parentId set) —
            // pickers show them as "Parent › Child" via categoryLabel().
            val rootsWithSubs = listOf(
                "Food" to listOf("Eating Out"),
                "Groceries" to emptyList(),
                "Transport" to listOf("Fuel"),
                "Shopping" to emptyList(),
                "Bills" to emptyList(),
                "Rent" to emptyList(),
                "Health" to emptyList(),
                "Entertainment" to listOf("Streaming"),
                "Travel" to emptyList(),
                "Education" to emptyList(),
                "Personal Care" to emptyList(),
            )
            rootsWithSubs.forEach { (name, subcategories) ->
                val parentId = accountDao.insert(
                    AccountEntity(
                        name = name,
                        accountClass = LedgerService.CLASS_EXPENSE,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                subcategories.forEach { sub ->
                    accountDao.insert(
                        AccountEntity(
                            name = sub,
                            accountClass = LedgerService.CLASS_EXPENSE,
                            parentId = parentId,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
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