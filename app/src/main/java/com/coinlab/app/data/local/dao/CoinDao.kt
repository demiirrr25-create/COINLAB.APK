package com.coinlab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.coinlab.app.data.local.entity.CoinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoinDao {

    @Query("SELECT * FROM coins ORDER BY marketCapRank ASC")
    fun getAllCoins(): Flow<List<CoinEntity>>

    @Query("SELECT * FROM coins WHERE id = :coinId")
    suspend fun getCoinById(coinId: String): CoinEntity?

    @Query("SELECT * FROM coins WHERE name LIKE '%' || :query || '%' OR symbol LIKE '%' || :query || '%'")
    fun searchCoins(query: String): Flow<List<CoinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coins: List<CoinEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coin: CoinEntity)

    @Upsert
    suspend fun upsertAll(coins: List<CoinEntity>)

    @Query("DELETE FROM coins")
    suspend fun deleteAll()

    @Query("DELETE FROM coins WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM coins")
    suspend fun getCount(): Int

    @Query("SELECT cachedAt FROM coins LIMIT 1")
    suspend fun getLastCachedTime(): Long?

    @Query("SELECT * FROM coins ORDER BY marketCapRank ASC LIMIT :limit")
    fun getTopCoinsSync(limit: Int): List<CoinEntity>

    @Query("SELECT * FROM coins ORDER BY marketCapRank ASC LIMIT :limit OFFSET :offset")
    suspend fun getCoinsPaged(limit: Int, offset: Int): List<CoinEntity>

    @Transaction
    suspend fun replaceAllCoins(coins: List<CoinEntity>) {
        deleteAll()
        insertAll(coins)
    }
}
