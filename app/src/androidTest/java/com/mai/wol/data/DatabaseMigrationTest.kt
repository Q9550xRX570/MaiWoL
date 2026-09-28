package com.mai.wol.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val dbName = "migration-test.db"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun createVersion6Database() {
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL("CREATE TABLE IF NOT EXISTS `devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `macAddress` TEXT NOT NULL, `ipAddress` TEXT NOT NULL, `localIp` TEXT NOT NULL, `port` INTEGER NOT NULL, `secureOnPassword` TEXT, `groupName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `shutdownType` TEXT NOT NULL, `shutdownPort` INTEGER NOT NULL, `shutdownUsername` TEXT NOT NULL, `shutdownPassword` TEXT NOT NULL, `shutdownCommand` TEXT NOT NULL, `shutdownHttpUrl` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deviceId` INTEGER NOT NULL, `hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `daysOfWeek` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `isOneTime` INTEGER NOT NULL, `targetDateMillis` INTEGER, FOREIGN KEY(`deviceId`) REFERENCES `devices`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedules_deviceId` ON `schedules` (`deviceId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'b99acf803ae77f37eb88a075f44dace6')")
        db.execSQL(
            "INSERT INTO devices (id, name, macAddress, ipAddress, localIp, port, secureOnPassword, groupName, createdAt, shutdownType, shutdownPort, shutdownUsername, shutdownPassword, shutdownCommand, shutdownHttpUrl) " +
                "VALUES (1, 'Masaüstü', 'AA:BB:CC:DD:EE:FF', 'home.example', '192.168.1.20', 9, NULL, 'Ev', 1700000000000, 'SSH', 22, 'erdem', 'gizli', 'shutdown /s /f /t 0', '')"
        )
        db.execSQL("INSERT INTO schedules (id, deviceId, hour, minute, daysOfWeek, isEnabled, isOneTime, targetDateMillis) VALUES (1, 1, 7, 30, '1,2,3,4,5', 1, 0, NULL)")
        db.version = 6
        db.close()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun upgradeFromVersion6KeepsDevicesAndSchedules() = runBlocking {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .build()

        val devices = database.deviceDao().getAllDevices().first()
        val schedules = database.scheduleDao().getAllSchedules().first()
        database.close()

        assertEquals(1, devices.size)
        val device = devices.single()
        assertEquals("Masaüstü", device.name)
        assertEquals("erdem", device.shutdownUsername)
        assertEquals("gizli", device.shutdownPassword)
        assertEquals("PASSWORD", device.sshAuthType)
        assertEquals("", device.sshPrivateKey)
        assertEquals("", device.sshKeyPassphrase)
        assertEquals(1, schedules.size)
        assertEquals(1L, schedules.single().deviceId)
    }
}
