package com.coinlab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.coinlab.app.data.local.entity.PriceAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {

    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isActive = 1 AND isTriggered = 0")
    suspend fun getActiveAlerts(): List<PriceAlertEntity>

    @Query("SELECT * FROM price_alerts WHERE coinId = :coinId")
    fun getAlertsByCoin(coinId: String): Flow<List<PriceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceAlertEntity): Long

    @Update
    suspend fun update(alert: PriceAlertEntity)

    @Query("DELETE FROM price_alerts WHERE id = :alertId")
    suspend fun deleteById(alertId: Long)

    @Query("UPDATE price_alerts SET isTriggered = 1 WHERE id = :alertId")
    suspend fun markTriggered(alertId: Long)
}
