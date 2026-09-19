package com.example.spendwise.ledger

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.spendwise.data.database.AppDatabase
import com.example.spendwise.data.database.entity.AccountEntity
import com.example.spendwise.ledger.service.LedgerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The flattened category picker (payment overlay, transaction editor, filter
 * sheet) is fed by [com.example.spendwise.data.database.dao.AccountDao.getCategoryAccounts].
 * Its contract: every sub-category sits directly after its parent root, so
 * "Food" and "Food › Eating Out" are never separated — regardless of what the
 * alphabetical order of the child names would do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryAccountOrderTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insert(
        name: String,
        parentId: Long? = null,
        sortOrder: Int = 0,
    ): Long = db.AccountDao().insert(
        AccountEntity(
            name = name,
            accountClass = LedgerService.CLASS_EXPENSE,
            parentId = parentId,
            sortOrder = sortOrder,
            createdAt = 0L,
        )
    )

    @Test
    fun `sub-categories sit directly after their parent root`() = runTest {
        // Insert scrambled on purpose. Alphabetical child order would split
        // "Eating Out" from Food; root order follows insertion (the seeder's
        // curated sequence), NOT the alphabet — Bills was inserted last.
        val food = insert("Food")
        insert("Eating Out", parentId = food)
        val transport = insert("Transport")
        insert("Fuel", parentId = transport)
        insert("Bills")

        val names = db.AccountDao().getCategoryAccounts().first().map { it.name }

        assertEquals(listOf("Food", "Eating Out", "Transport", "Fuel", "Bills"), names)
    }

    @Test
    fun `sort_order breaks ties between siblings within a group`() = runTest {
        val alpha = insert("Alpha")
        val mid = insert("Mid")
        // Two children under Mid, inserted reverse-sorted: explicit
        // sort_order must beat both name and insertion order.
        insert("Second", parentId = mid, sortOrder = 2)
        insert("First", parentId = mid, sortOrder = 1)
        val zeta = insert("Zeta")
        // Child of a LATER root must not jump ahead of Mid's children.
        insert("LaterChild", parentId = zeta, sortOrder = 0)
        assertEquals(setOf(alpha, mid, zeta), setOf(1L, 2L, 5L))

        val names = db.AccountDao().getCategoryAccounts().first().map { it.name }

        assertEquals(
            listOf("Alpha", "Mid", "First", "Second", "Zeta", "LaterChild"),
            names,
        )
    }
}