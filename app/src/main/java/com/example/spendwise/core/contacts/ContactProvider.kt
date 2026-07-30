package com.example.spendwise.core.contacts


interface ContactProvider {
    suspend fun getContacts(): List<Contact>
}