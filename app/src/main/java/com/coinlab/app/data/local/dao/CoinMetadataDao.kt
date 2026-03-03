package com.coinlab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.coinlab.app.data.local.entity.CoinMetadataEntity

@Dao
interface CoinMetadataDao {

    @Query("SELECT * FROM coin_metadata ORDER BY marketCapRank ASC")
    suspend fun getAllMetadata(): List<CoinMetadataEntity>

    @Query("SELECT * FROM coin_metadata WHERE coinId = :coinId")
    suspend fun getMetadataById(coinId: String): CoinMetadataEntity?

    @Query("SELECT * FROM coin_metadata WHERE binanceSymbol = :binanceSymbol")
    suspend fun getMetadataByBinanceSymbol(binanceSymbol: String): CoinMetadataEntity?

    @Query("SELECT * FROM coin_metadata WHERE symbol = :symbol")
    suspend fun getMetadataBySymbol(symbol: String): CoinMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metadata: List<CoinMetadataEntity>)

    @Query("DELETE FROM coin_metadata")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(metadata: List<CoinMetadataEntity>) {
        deleteAll()
        insertAll(metadata)
    }

    @Query("SELECT COUNT(*) FROM coin_metadata")
    suspend fun getCount(): Int

    @Query("SELECT cachedAt FROM coin_metadata LIMIT 1")
    suspend fun getLastCachedTime(): Long?
}
