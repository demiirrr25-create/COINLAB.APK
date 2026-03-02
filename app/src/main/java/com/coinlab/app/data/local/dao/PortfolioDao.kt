package com.coinlab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coinlab.app.data.local.entity.PortfolioEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {

    @Query("SELECT * FROM portfolio_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<PortfolioEntryEntity>>

    @Query("SELECT * FROM portfolio_entries WHERE coinId = :coinId ORDER BY timestamp DESC")
    fun getEntriesByCoin(coinId: String): Flow<List<PortfolioEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PortfolioEntryEntity): Long

    @Update
    suspend fun update(entry: PortfolioEntryEntity)

    @Query("DELETE FROM portfolio_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)

    @Query("SELECT DISTINCT coinId FROM portfolio_entries")
    suspend fun getDistinctCoinIds(): List<String>

    @Query("SELECT SUM(CASE WHEN type = 'BUY' THEN amount ELSE -amount END) FROM portfolio_entries WHERE coinId = :coinId")
    suspend fun getNetAmount(coinId: String): Double?
}
