package com.example.spendwise.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendwise.data.database.entity.CounterpartyEntity
import com.example.spendwise.data.database.entity.GroupEntity
import com.example.spendwise.data.database.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

/** Groups + group_members (Rust migration 004). */
@Dao
interface GroupDao {

    @Query("SELECT * FROM `groups` ORDER BY created_at DESC")
    fun getAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    @Query("SELECT * FROM `groups` WHERE status = 'active' ORDER BY created_at DESC")
    fun getActive(): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Delete
    suspend fun delete(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun clearMembers(groupId: Long)

    @Query(
        """
        SELECT c.* FROM counterparties c
        JOIN group_members m ON m.counterparty_id = c.id
        WHERE m.group_id = :groupId
        ORDER BY c.display_name ASC
    """
    )
    suspend fun membersOf(groupId: Long): List<CounterpartyEntity>
}
