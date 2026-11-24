package com.mamalink.app.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseMigrationTest {

    @Test fun `migration from v1 to v2 preserves workspace metadata and creates new tables`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }

        // 1. Create database in version 1 schema manually
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `workspace_metadata` (`id` INTEGER NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val v1Db = openHelper.writableDatabase

        // Insert metadata in v1
        val values = ContentValues().apply {
            put("id", 1)
            put("label", "V1 Workspace Label")
        }
        v1Db.insert("workspace_metadata", SQLiteDatabase.CONFLICT_REPLACE, values)
        v1Db.close()

        // 2. Run explicit migration 1 -> 2
        val migratedDb = Room.databaseBuilder(context, MamaLinkDatabase::class.java, "test_migration.db")
            .addMigrations(MamaLinkDatabase.MIGRATION_1_2)
            .build()

        // 3. Verify workspace_metadata was preserved
        val metadata = migratedDb.workspaceDao().getMetadata()
        assertNotNull(metadata)
        assertEquals("V1 Workspace Label", metadata?.label)

        // 4. Verify new tables are ready for operations
        val caregiverId = migratedDb.caregiverDao().insertCaregiver(
            com.mamalink.app.data.local.entity.CaregiverEntity(
                fullName = "Migrated Caregiver",
                phoneNumber = "+15550000",
                email = "migrated@example.com",
                address = "123 Migration Way",
            )
        )
        val caregiver = migratedDb.caregiverDao().getCaregiverById(caregiverId)
        assertNotNull(caregiver)
        assertEquals("Migrated Caregiver", caregiver?.fullName)

        migratedDb.close()
    }
}
