package com.example.spendwise.data.repository


import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.mapper.AppMetadata
import com.example.spendwise.data.mapper.toEntity
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppMetadataRepository @Inject constructor(
    private val dao: AppMetadataDao
) {

    fun observeCategories() =
        dao.observeAll().map { list ->
            list.map { it }
        }

    suspend fun get() =
        dao.get()

    suspend fun insert(metadata: AppMetadata) =
        dao.insert(metadata.toEntity())

    suspend fun update(metadata: AppMetadata) =
        dao.update(metadata.toEntity())

    suspend fun delete(metadata: AppMetadata) =
        dao.delete(metadata.toEntity())

    suspend fun count() =
        dao.count()

    /** Advance the SMS sync watermark; null-safe so first runs don't crash. */
    suspend fun updateLastSmsSync(time: Long) {
        dao.get()?.let { dao.update(it.copy(lastSmsSync = time)) }
    }

    /**
     * Record "day zero" of the ledger — the date the user picked when the
     * onboarding SMS scan ran. Initial balances are meaningful as of this
     * date, and later scans treat it as the earliest SMS worth reading.
     * Null-safe: a missing metadata row (seeder hasn't run yet) is ignored.
     */
    suspend fun setTrackingStartDate(time: Long) {
        dao.get()?.let { dao.update(it.copy(trackingStartDate = time)) }
    }
}
