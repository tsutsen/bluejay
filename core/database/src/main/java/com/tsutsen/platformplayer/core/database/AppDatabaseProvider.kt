package com.tsutsen.platformplayer.core.database

import android.content.Context
import androidx.room.Room
import java.util.concurrent.atomic.AtomicReference

/**
 * Single construction site for [AppDatabase]. Used by the Hilt module and
 * by app-level singleton states that predate Hilt injection.
 *
 * The built instance is cached process-wide: callers pass different contexts
 * (the Hilt module passes @ApplicationContext, legacy states pass whatever
 * StateApp holds — which MainActivity replaces with the *activity* context),
 * and separate Room instances on the same file silently diverge into two
 * copies of the data (the backup export saw 0 saved videos while the Library
 * UI, via the Hilt DAO, saw hundreds). One process, one database instance.
 */
object AppDatabaseProvider {
    private val _instance = AtomicReference<AppDatabase?>(null)

    fun get(context: Context): AppDatabase =
        _instance.get() ?: build(context).let { built ->
            _instance.compareAndSet(null, built)
            // Always return the winner so a lost race still shares one instance.
            _instance.get() ?: built
        }

    private fun build(context: Context): AppDatabase =
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
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
            )
            // Safety net only: MIGRATION_1_2 is registered above, so this
            // triggers just for an unregistered future version (same as before).
            .fallbackToDestructiveMigration()
            .build()
}
