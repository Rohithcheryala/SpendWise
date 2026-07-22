package com.example.spendwise.data.repository



import com.example.spendwise.data.database.dao.AppMetadataDao
import com.example.spendwise.data.mapper.AppMetadata
import com.example.spendwise.data.mapper.toEntity
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.collections.map

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
}