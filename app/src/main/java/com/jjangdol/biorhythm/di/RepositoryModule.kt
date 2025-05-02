package com.jjangdol.biorhythm.di

import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.data.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        db: FirebaseFirestore
    ): SettingsRepository = SettingsRepository(db)
}
