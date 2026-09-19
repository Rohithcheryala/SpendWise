package com.example.spendwise.core.extensions

import com.example.spendwise.data.database.entity.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Picker-label rules for flattened category trees (see [categoryLabel]). */
class CategoryLabelsTest {

    private fun account(
        id: Long,
        name: String,
        parentId: Long? = null,
        accountClass: String = "expense",
        isSystem: Boolean = false,
        isArchived: Boolean = false,
    ) = AccountEntity(
        id = id,
        name = name,
        accountClass = accountClass,
        parentId = parentId,
        isSystem = isSystem,
        isArchived = isArchived,
        createdAt = 0L,
    )

    @Test
    fun `root category renders as plain name`() {
        val food = account(1, "Food")
        assertEquals("Food", food.categoryLabel(emptyMap()))
    }

    @Test
    fun `sub-category is prefixed with its parent`() {
        val food = account(1, "Food")
        val eatingOut = account(2, "Eating Out", parentId = 1)
        val byId = mapOf(1L to food, 2L to eatingOut)
        assertEquals("Food › Eating Out", eatingOut.categoryLabel(byId))
    }

    @Test
    fun `orphaned sub-category falls back to plain name`() {
        // Parent archived out of the picker list — degrade, don't crash.
        val eatingOut = account(2, "Eating Out", parentId = 1)
        assertEquals("Eating Out", eatingOut.categoryLabel(emptyMap()))
    }

    @Test
    fun `lookup variant resolves parent by id`() {
        val eatingOut = account(2, "Eating Out", parentId = 1)
        var label: String? = null
        kotlinx.coroutines.test.runTest {
            label = eatingOut.categoryLabel { id -> if (id == 1L) account(1, "Food") else null }
        }
        assertEquals("Food › Eating Out", label)
    }
}
