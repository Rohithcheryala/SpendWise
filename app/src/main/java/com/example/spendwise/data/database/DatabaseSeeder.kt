package com.example.spendwise.data.database


import com.example.spendwise.data.mapper.AppMetadata
import com.example.spendwise.data.mapper.Category
import com.example.spendwise.data.repository.AppMetadataRepository
import com.example.spendwise.data.repository.CategoryRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val appMetadataRepository: AppMetadataRepository,
) {

    suspend fun seed() {
        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")


        if (categoryRepository.count() == 0) {
            categoryRepository.insert(Category(
                name = "Food",
            ))
            categoryRepository.insert(Category(name = "Transport"))
            categoryRepository.insert(Category(name = "Shopping"))
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