package com.agon.app.di

import android.content.Context
import androidx.room.Room
import com.agon.app.data.local.AppDatabase
import com.agon.app.data.local.dao.JournalDao
import com.agon.app.data.purityDataStore
import com.agon.app.data.repository.JournalRepository
import com.agon.app.data.repository.ProtectionRepository
import com.agon.app.data.security.JournalCrypto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProtectionRepository(
        @ApplicationContext context: Context,
    ): ProtectionRepository = ProtectionRepository(context.purityDataStore)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "guardsoul.db").build()

    @Provides
    fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()

    @Provides
    @Singleton
    fun provideJournalCrypto(
        @ApplicationContext context: Context,
    ): JournalCrypto = JournalCrypto(context)

    @Provides
    @Singleton
    fun provideJournalRepository(
        dao: JournalDao,
        @ApplicationContext context: Context,
        crypto: JournalCrypto,
    ): JournalRepository = JournalRepository(dao, context.purityDataStore, crypto)
}
