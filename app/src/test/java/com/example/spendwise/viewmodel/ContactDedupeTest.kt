package com.example.spendwise.viewmodel

import com.example.spendwise.core.contacts.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for the Add-Friend contact picker dedupe: the
 * ContactsProvider returns one row per raw-contact phone entry, so a person
 * merged across device + Google + WhatsApp accounts shows up several times
 * with the same number in different formats.
 */
class ContactDedupeTest {

    private fun contact(id: Long, name: String, phone: String) =
        Contact(id = id, name = name, phoneNumber = phone, photoUri = null)

    private fun List<Contact>.dedupe() = distinctByPhone()

    @Test
    fun `same number in different formats collapses to one row`() {
        val rows = listOf(
            contact(1, "Aasim @iitdh", "+91 91217 95607"),
            contact(1, "Aasim @iitdh", "+919121795607"),
        )
        assertEquals(1, rows.dedupe().size)
    }

    @Test
    fun `first-seen formatting is kept`() {
        val rows = listOf(
            contact(1, "Aasim @iitdh", "+91 91217 95607"),
            contact(1, "Aasim @iitdh", "+919121795607"),
        )
        assertEquals("+91 91217 95607", rows.dedupe().single().phoneNumber)
    }

    @Test
    fun `genuinely different numbers of one person stay separate`() {
        val rows = listOf(
            contact(1, "Aasim @iitdh", "+91 91217 95607"),
            contact(1, "Aasim @iitdh", "011 2345 6789"),
        )
        assertEquals(2, rows.dedupe().size)
    }

    @Test
    fun `different people sharing a number stay separate`() {
        val rows = listOf(
            contact(1, "Mom", "+91 11223 34455"),
            contact(2, "Dad", "+911122334455"),
        )
        assertEquals(2, rows.dedupe().size)
    }

    @Test
    fun `first-seen order is preserved`() {
        val rows = listOf(
            contact(3, "Zara", "+91 90000 90000"),
            contact(1, "Aasim @iitdh", "+91 91217 95607"),
            contact(1, "Aasim @iitdh", "+919121795607"),
            contact(2, "Bilal", "+91 80000 80000"),
        )
        assertEquals(
            listOf("Zara", "Aasim @iitdh", "Bilal"),
            rows.dedupe().map { it.name },
        )
    }

    @Test
    fun `rows with unnormalizable phones are dropped rather than mis-keyed`() {
        val rows = listOf(
            contact(1, "Aasim @iitdh", "+91 91217 95607"),
            contact(2, "Support", "1800"),
        )
        assertEquals(listOf("Aasim @iitdh"), rows.dedupe().map { it.name })
    }
}