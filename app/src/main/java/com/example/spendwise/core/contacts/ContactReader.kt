package com.example.spendwise.core.contacts

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject


class ContactReader @Inject constructor(
    private val contentResolver: ContentResolver,
) {

     suspend fun getContacts(): List<Contact> =
        withContext(Dispatchers.IO) {

            val contacts = mutableListOf<Contact>()

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )

                val nameColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )

                val numberColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                val photoColumn = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                )

                while (cursor.moveToNext()) {

                    contacts += Contact(
                        id = cursor.getLong(idColumn),
                        name = cursor.getString(nameColumn).orEmpty(),
                        phoneNumber = cursor.getString(numberColumn).orEmpty(),
                        photoUri = cursor.getString(photoColumn)?.let(Uri::parse)
                    )
                }
            }

            contacts
        }
}