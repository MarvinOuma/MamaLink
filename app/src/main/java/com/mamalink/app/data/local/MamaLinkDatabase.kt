package com.mamalink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mamalink.app.data.local.dao.AppointmentDao
import com.mamalink.app.data.local.dao.CaregiverDao
import com.mamalink.app.data.local.dao.ChildDao
import com.mamalink.app.data.local.entity.AppointmentEntity
import com.mamalink.app.data.local.entity.CaregiverEntity
import com.mamalink.app.data.local.entity.ChildEntity

@Database(
    entities = [
        WorkspaceMetadata::class,
        CaregiverEntity::class,
        ChildEntity::class,
        AppointmentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DateTimeConverters::class)
abstract class MamaLinkDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun caregiverDao(): CaregiverDao
    abstract fun childDao(): ChildDao
    abstract fun appointmentDao(): AppointmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `caregivers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `full_name` TEXT NOT NULL,
                        `phone_number` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `children` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `caregiver_id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `date_of_birth` TEXT NOT NULL,
                        FOREIGN KEY(`caregiver_id`) REFERENCES `caregivers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_children_caregiver_id` ON `children` (`caregiver_id`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_children_id_caregiver_id` ON `children` (`id`, `caregiver_id`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `appointments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `caregiver_id` INTEGER NOT NULL,
                        `child_id` INTEGER,
                        `title` TEXT NOT NULL,
                        `appointment_date_time` INTEGER NOT NULL,
                        `location` TEXT,
                        FOREIGN KEY(`caregiver_id`) REFERENCES `caregivers`(`id`) ON UPDATE RESTRICT ON DELETE RESTRICT,
                        FOREIGN KEY(`child_id`, `caregiver_id`) REFERENCES `children`(`id`, `caregiver_id`) ON UPDATE RESTRICT ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_appointments_caregiver_id` ON `appointments` (`caregiver_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_appointments_child_id_caregiver_id` ON `appointments` (`child_id`, `caregiver_id`)",
                )
            }
        }

        fun create(context: Context): MamaLinkDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MamaLinkDatabase::class.java,
                "mamalink.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
