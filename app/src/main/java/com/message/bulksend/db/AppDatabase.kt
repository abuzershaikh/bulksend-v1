package com.message.bulksend.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Campaign::class, Setting::class, ContactGroup::class], version = 8, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun campaignDao(): CampaignDao
    abstract fun settingDao(): SettingDao
    abstract fun contactGroupDao(): ContactGroupDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN isRunning INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `contact_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `contacts` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contact_groups ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN campaignType TEXT NOT NULL DEFAULT 'BULKSEND'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE campaigns_new (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `groupId` TEXT NOT NULL,
                        `campaignName` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `totalContacts` INTEGER NOT NULL,
                        `contactStatuses` TEXT NOT NULL,
                        `isStopped` INTEGER NOT NULL,
                        `isRunning` INTEGER NOT NULL,
                        `campaignType` TEXT NOT NULL DEFAULT 'BULKSEND'
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO campaigns_new (id, groupId, campaignName, message, timestamp, totalContacts, contactStatuses, isStopped, isRunning, campaignType)
                    SELECT id, groupId, campaignName, message, timestamp, totalContacts, contactStatuses, isStopped, isRunning, campaignType FROM campaigns
                """.trimIndent())
                db.execSQL("DROP TABLE campaigns")
                db.execSQL("ALTER TABLE campaigns_new RENAME TO campaigns")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN sheetFileName TEXT")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN countryCode TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN sheetDataJson TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bulksend_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

