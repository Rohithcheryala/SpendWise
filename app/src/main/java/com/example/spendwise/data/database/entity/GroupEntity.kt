package com.example.spendwise.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A shared-expense group (Rust migration 004) — e.g. a trip with friends.
 * A split transaction references its group; each member's share posts onto
 * that member's receivable child account.
 */
@Entity(tableName = "groups")
data class GroupEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** 'active' | 'settled' */
    val status: String = STATUS_ACTIVE,

    @ColumnInfo(name = "settled_at")
    val settledAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_SETTLED = "settled"
    }
}

/** Membership of one counterparty (person) in a [GroupEntity]. */
@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "counterparty_id"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_id"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index("counterparty_id")]
)
data class GroupMemberEntity(

    @ColumnInfo(name = "group_id")
    val groupId: Long,

    @ColumnInfo(name = "counterparty_id")
    val counterpartyId: Long,
)
