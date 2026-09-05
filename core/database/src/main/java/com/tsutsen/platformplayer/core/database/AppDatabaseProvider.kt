package com.tsutsen.platformplayer.core.database

import android.content.Context
import androidx.room.Room

/**
 * Single construction site for [AppDatabase]. Used by the Hilt module and
 * by app-level singleton states that predate Hilt injection.
 */
object AppDatabaseProvider {
    fun get(context: Context): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "grayjay_database",
            ).addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
            )
            // Safety net only: MIGRATION_1_2 is registered above, so this
            // triggers just for an unregistered future version (same as before).
            .fallbackToDestructiveMigration()
            .build()
}
