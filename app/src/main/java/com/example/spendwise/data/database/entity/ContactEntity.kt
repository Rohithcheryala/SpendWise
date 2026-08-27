package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved phone contact the user opted to sync - a CACHE of the device's
 * address book, fully replaceable on every sync. Counterparties never link to
 * this table by foreign key; they link SOFTLY via phone-prefixed UPI handles
 * stored in counterparty_aliases, because device contacts churn - renamed,
 * merged, deleted - and the stable join key is the number itself.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "phone_last10")
    val phoneLast10: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
)
