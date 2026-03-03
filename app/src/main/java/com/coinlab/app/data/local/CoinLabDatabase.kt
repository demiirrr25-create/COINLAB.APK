package com.coinlab.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.CoinMetadataDao
import com.coinlab.app.data.local.dao.PortfolioDao
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.dao.WatchlistDao
import com.coinlab.app.data.local.entity.CoinEntity
import com.coinlab.app.data.local.entity.CoinMetadataEntity
import com.coinlab.app.data.local.entity.PortfolioEntryEntity
import com.coinlab.app.data.local.entity.PriceAlertEntity
import com.coinlab.app.data.local.entity.WatchlistEntity

@Database(
    entities = [
        CoinEntity::class,
        CoinMetadataEntity::class,
        PortfolioEntryEntity::class,
        WatchlistEntity::class,
        PriceAlertEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class CoinLabDatabase : RoomDatabase() {
    abstract fun coinDao(): CoinDao
    abstract fun coinMetadataDao(): CoinMetadataDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun priceAlertDao(): PriceAlertDao

    companion object {
        @Volatile
        private var INSTANCE: CoinLabDatabase? = null

        fun getInstance(context: Context): CoinLabDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CoinLabDatabase::class.java,
                    "coinlab_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
