package com.coinlab.app.di

import android.content.Context
import androidx.room.Room
import com.coinlab.app.data.local.CoinLabDatabase
import com.coinlab.app.data.local.dao.CoinDao
import com.coinlab.app.data.local.dao.CoinMetadataDao
import com.coinlab.app.data.local.dao.PortfolioDao
import com.coinlab.app.data.local.dao.PriceAlertDao
import com.coinlab.app.data.local.dao.WatchlistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CoinLabDatabase {
        return Room.databaseBuilder(
            context,
            CoinLabDatabase::class.java,
            "coinlab_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCoinDao(database: CoinLabDatabase): CoinDao = database.coinDao()

    @Provides
    fun provideCoinMetadataDao(database: CoinLabDatabase): CoinMetadataDao = database.coinMetadataDao()

    @Provides
    fun providePortfolioDao(database: CoinLabDatabase): PortfolioDao = database.portfolioDao()

    @Provides
    fun provideWatchlistDao(database: CoinLabDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    fun providePriceAlertDao(database: CoinLabDatabase): PriceAlertDao = database.priceAlertDao()
}
