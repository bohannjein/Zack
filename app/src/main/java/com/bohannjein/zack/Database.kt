package com.bohannjein.zack

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow

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
}

@Database(entities = [NetworkServer::class, UploadEntry::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun historyDao(): HistoryDao
}

object DatabaseInstance {
    @Volatile private var INSTANCE: AppDatabase? = null
    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "zack-db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

class SecureStorage(c: Context) {
    private val k = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val p = EncryptedSharedPreferences.create(c, "secure_prefs", k, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun savePassword(s: Long, pw: String) { p.edit().putString("pwd_$s", pw).apply() }
    fun getPassword(s: Long): String? { return p.getString("pwd_$s", null) }
}

data class ZackItem(
    val id: Long,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val isError: Boolean = false,
    val isSpecial: Boolean = false,
    val isUploading: Boolean = false
)
fun UploadEntry.toZackItem() = ZackItem(
    id = id,
    icon = if (status == "Failed") Icons.Default.Error else if (status == "Uploading") Icons.Default.CloudUpload else Icons.Default.CloudDone,
    title = fileName,
    subtitle = "$serverName • $status",
    isError = status == "Failed",
    isSpecial = false,
    isUploading = status == "Uploading"
)

fun NetworkServer.toZackItem() = ZackItem(
    id = id,
    icon = Icons.Default.Dns,
    title = displayName,
    subtitle = "$protocol • $hostIp",
    isError = false,
    isSpecial = isDefault,
    isUploading = false
)