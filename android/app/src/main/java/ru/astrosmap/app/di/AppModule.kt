package ru.astrosmap.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.astrosmap.app.astro.AstroEngine
import ru.astrosmap.app.astro.Ephe
import ru.astrosmap.app.data.ChartDao
import ru.astrosmap.app.data.ChartDb
import ru.astrosmap.app.data.CityStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun chartDb(@ApplicationContext context: Context): ChartDb =
        Room.databaseBuilder(context, ChartDb::class.java, "charts.db")
            .addMigrations(ChartDb.MIGRATION_1_2, ChartDb.MIGRATION_2_3)
            .build()

    @Provides
    fun chartDao(db: ChartDb): ChartDao = db.chartDao()

    @Provides
    @Singleton
    fun journalDb(@ApplicationContext context: Context): ru.astrosmap.app.data.JournalDb =
        Room.databaseBuilder(context, ru.astrosmap.app.data.JournalDb::class.java, "journal_v2.db").build()

    @Provides
    fun journalDao(db: ru.astrosmap.app.data.JournalDb): ru.astrosmap.app.data.JournalDao = db.journalDao()

    @Provides
    @Singleton
    fun cityStore(@ApplicationContext context: Context): CityStore = CityStore(context)

    @Provides
    @Singleton
    fun astroEngine(@ApplicationContext context: Context): AstroEngine =
        AstroEngine(Ephe.path(context))
}
