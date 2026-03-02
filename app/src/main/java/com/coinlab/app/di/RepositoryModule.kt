package com.coinlab.app.di

import com.coinlab.app.data.repository.CoinRepositoryImpl
import com.coinlab.app.data.repository.NewsRepositoryImpl
import com.coinlab.app.data.repository.PortfolioRepositoryImpl
import com.coinlab.app.domain.repository.CoinRepository
import com.coinlab.app.domain.repository.NewsRepository
import com.coinlab.app.domain.repository.PortfolioRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCoinRepository(impl: CoinRepositoryImpl): CoinRepository

    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: PortfolioRepositoryImpl): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository
}
