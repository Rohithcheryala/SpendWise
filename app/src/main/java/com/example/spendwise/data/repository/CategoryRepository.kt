package com.example.spendwise.data.repository


import com.example.spendwise.data.database.dao.CategoryDao
import com.example.spendwise.data.mapper.Category
import com.example.spendwise.data.mapper.toDomain
import com.example.spendwise.data.mapper.toEntity
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val dao: CategoryDao
) {

//    fun observeCategories() =
//        dao.observeAll().map { list ->
//            list.map { it.toDomain() }
//        }

    suspend fun getCategory(id: Long) =
        dao.getById(id)?.toDomain()

    suspend fun insert(category: Category) =
        dao.insert(category.toEntity())

    suspend fun update(category: Category) =
        dao.update(category.toEntity())

    suspend fun delete(category: Category) =
        dao.delete(category.toEntity())

    suspend fun count() =
        dao.count()
}