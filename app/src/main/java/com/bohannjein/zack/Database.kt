package com.bohannjein.zack

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Entity(tableName = "servers")
data class NetworkServer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocol: String,
    val displayName: String,
    val hostIp: String,
    val port: String,
    val shareName: String,
    val domain: String = "WORKGROUP",
    val username: String = "",
    val isDefault: Boolean = false
)

@Entity(tableName = "history")
data class UploadEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val serverName: String,
    val status: String,
    val errorMessage: String = "",
    val fileSize: Long = 0,
    val workTag: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ServerDao {
    @Insert suspend fun insertServer(s: NetworkServer): Long
    @Update suspend fun updateServer(s: NetworkServer)
    @Query("SELECT * FROM servers ORDER BY isDefault DESC, id DESC") fun getAllServers(): Flow<List<NetworkServer>>
    @Query("SELECT count(*) FROM servers") suspend fun getServerCount(): Int
    @Query("SELECT * FROM servers LIMIT 1") suspend fun getFirstServer(): NetworkServer?
    @Query("SELECT * FROM servers WHERE isDefault = 1") suspend fun getDefaultServers(): List<NetworkServer>
    @Query("SELECT * FROM servers WHERE isDefault = 1 LIMIT 1") suspend fun getDefaultServer(): NetworkServer?
    @Query("SELECT * FROM servers WHERE id = :serverId LIMIT 1") suspend fun getServerByIdSync(serverId: Long): NetworkServer?
    @Query("DELETE FROM servers WHERE id IN (:ids)") suspend fun deleteServers(ids: List<Long>)
    @Query("UPDATE servers SET isDefault = :isDefault WHERE id = :id") suspend fun toggleDefault(id: Long, isDefault: Boolean)
}

@Dao
interface HistoryDao {
    @Insert suspend fun insert(e: UploadEntry): Long
    @Query("SELECT * FROM history ORDER BY timestamp DESC") fun getAllHistory(): Flow<List<UploadEntry>>
    @Query("DELETE FROM history WHERE id IN (:ids)") suspend fun deleteEntries(ids: List<Long>)
    @Query("UPDATE history SET status = :status WHERE id = :id") suspend fun updateStatus(id: Long, status: String)
    @Query("UPDATE history SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatusAndError(id: Long, status: String, errorMessage: String)
    @Query("DELETE FROM history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE history ADD COLUMN errorMessage TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE history ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE history ADD COLUMN workTag TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [NetworkServer::class, UploadEntry::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun historyDao(): HistoryDao
}

object DatabaseInstance {
    @Volatile private var INSTANCE: AppDatabase? = null
    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zack-db")
                .addMigrations(MIGRATION_9_10)
                .build().also { INSTANCE = it }
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        diff < 172_800_000L -> "Yesterday"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "${bytes / 1_024} KB"
    bytes < 1_073_741_824 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    else -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
}

data class ZackItem(
    val id: Long,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val isError: Boolean = false,
    val isSpecial: Boolean = false,
    val isUploading: Boolean = false,
    val workTag: String = ""
)

fun UploadEntry.toZackItem(): ZackItem {
    val sizeStr = if (fileSize > 0) " · ${formatFileSize(fileSize)}" else ""
    val timeStr = " · ${formatRelativeTime(timestamp)}"
    val subtitle = when (status) {
        "Failed" -> "$serverName · ${errorMessage.ifBlank { "Failed" }.take(40)}$timeStr"
        "Uploading" -> "$serverName · Uploading…"
        else -> "$serverName$sizeStr$timeStr"
    }
    return ZackItem(
        id = id,
        icon = if (status == "Failed") Icons.Default.Error
               else if (status == "Uploading") Icons.Default.CloudUpload
               else Icons.Default.CloudDone,
        title = fileName,
        subtitle = subtitle,
        isError = status == "Failed",
        isSpecial = false,
        isUploading = status == "Uploading",
        workTag = workTag
    )
}

fun NetworkServer.toZackItem() = ZackItem(
    id = id,
    icon = Icons.Default.Dns,
    title = displayName,
    subtitle = "$protocol • $hostIp",
    isError = false,
    isSpecial = isDefault,
    isUploading = false
)
