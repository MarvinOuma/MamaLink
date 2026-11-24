package com.mamalink.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mamalink.app.data.local.entity.AppointmentEntity
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DatabaseMigrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun seedVersionOne(name: String) {
        context.deleteDatabase(name)
        // Use the historical exported schema, including its Room identity hash.
        val schema = context.assets.open("com.mamalink.app.data.local.MamaLinkDatabase/1.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            db.execSQL(entity.getString("createSql").replace(
                                "$" + "{TABLE_NAME}", entity.getString("tableName"),
                            ))
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        db.execSQL("INSERT INTO workspace_metadata (id, label) VALUES (1, 'Demo v1 workspace')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        error("The v1 fixture must not upgrade")
                    }
                }).build(),
        )
        helper.use { it.writableDatabase }
    }

    @Test fun `production factory migrates exported v1 and matches every fresh v2 table`() = runTest {
        val name = "mamalink.db"
        seedVersionOne(name)
        try {
            MamaLinkDatabase.create(context).use { migrated ->
                assertEquals("Demo v1 workspace", migrated.workspaceDao().getMetadata()?.label)
                val sql = migrated.openHelper.writableDatabase
                assertEquals(2, sql.version)
                Room.inMemoryDatabaseBuilder(context, MamaLinkDatabase::class.java).build().use { fresh ->
                    val freshSql = fresh.openHelper.writableDatabase
                    for (table in listOf("workspace_metadata", "caregivers", "children", "appointments")) {
                        assertEquals("Table mismatch: $table", TableInfo.read(freshSql, table), TableInfo.read(sql, table))
                    }
                }
                val owner = migrated.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Demo"))
                val child = migrated.childDao().insertChild(ChildEntity(caregiverId = owner, name = "Demo child", dateOfBirth = LocalDate.of(2024, 2, 29)))
                val appointment = migrated.appointmentDao().insertAppointment(AppointmentEntity(caregiverId = owner, childId = child, title = "Demo visit", scheduledAt = Instant.EPOCH))
                assertNull(migrated.caregiverDao().getCaregiverById(owner)?.phoneNumber)
                assertEquals(LocalDate.of(2024, 2, 29), migrated.childDao().getChildById(child)?.dateOfBirth)
                assertEquals(Instant.EPOCH, migrated.appointmentDao().getAppointmentById(appointment)?.scheduledAt)
                val other = migrated.caregiverDao().insertCaregiver(CaregiverEntity(fullName = "Other"))
                try {
                    migrated.appointmentDao().insertAppointment(AppointmentEntity(caregiverId = other, childId = child, title = "Invalid", scheduledAt = Instant.EPOCH))
                    fail("Migrated schema must reject foreign children")
                } catch (_: android.database.sqlite.SQLiteConstraintException) { }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            }
            // Reopening verifies that Room's v2 identity is persisted, not just accepted once.
            MamaLinkDatabase.create(context).use { reopened ->
                assertEquals("Demo v1 workspace", reopened.workspaceDao().getMetadata()?.label)
                assertEquals(1, reopened.childDao().getChildrenByCaregiverId(1).size)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun `migration is required and missing paths never silently erase v1 data`() {
        val name = "missing-migration.db"
        seedVersionOne(name)
        try {
            Room.databaseBuilder(context, MamaLinkDatabase::class.java, name).build().use { db ->
                try {
                    db.openHelper.writableDatabase
                    fail("An explicit migration is required")
                } catch (expected: IllegalStateException) {
                    assertTrue(expected.message.orEmpty().contains("migration", ignoreCase = true))
                }
            }
            Room.databaseBuilder(context, MamaLinkDatabase::class.java, name)
                .addMigrations(MamaLinkDatabase.MIGRATION_1_2).build().use { db ->
                    assertEquals("Demo v1 workspace", db.openHelper.writableDatabase.query(
                        "SELECT label FROM workspace_metadata WHERE id = 1",
                    ).use { it.moveToFirst(); it.getString(0) })
                }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
