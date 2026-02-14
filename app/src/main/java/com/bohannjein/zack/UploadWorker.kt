package com.bohannjein.zack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.io.ArrayByteChunkProvider
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString("file_path") ?: return@withContext Result.failure()
        val serverId = inputData.getLong("server_id", -1)
        if (serverId == -1L) return@withContext Result.failure()

        val context = applicationContext
        val db = DatabaseInstance.get(context)
        val server = db.serverDao().getServerByIdSync(serverId) ?: return@withContext Result.failure()
        val password = SecureStorage(context).getPassword(serverId) ?: ""

        val file = File(filePath)
        val fileName = file.name

        val historyId = db.historyDao().insert(UploadEntry(fileName = fileName, serverName = server.displayName, status = "Uploading"))

        // FIX: 'withMultiProtocolNegotiation' entfernt, SMBJ macht das automatisch
        val config = SmbConfig.builder()
            .withDfsEnabled(true)
            .withTimeout(60, TimeUnit.SECONDS)
            .build()

        val client = SMBClient(config)
        try {
            val parts = server.shareName.trim('/').split('/', limit = 2)
            val shareName = parts[0]
            val subPath = if (parts.size > 1) "${parts[1].trim('/')}/" else ""

            client.connect(server.hostIp).use { connection ->
                val domain = server.domain.ifBlank { "WORKGROUP" }
                val auth = AuthenticationContext(server.username.ifBlank { "guest" }, password.toCharArray(), domain)
                val session = connection.authenticate(auth)

                session.connectShare(shareName).use { share ->
                    val diskShare = share as DiskShare
                    FileInputStream(file).use { input ->
                        val remotePath = subPath + fileName
                        val remoteFile = diskShare.openFile(
                            remotePath, setOf(AccessMask.GENERIC_WRITE), null,
                            SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null
                        )
                        remoteFile.write(ArrayByteChunkProvider(input.readBytes(), 0))
                        remoteFile.close()
                    }
                }
            }
            db.historyDao().updateStatus(historyId, "Success")
            sendNotification(context, "Upload successful", fileName)
            file.delete()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            db.historyDao().updateStatus(historyId, "Failed")
            sendNotification(context, "Upload failed", fileName)
            Result.failure()
        } finally {
            client.close()
        }
    }

    private fun sendNotification(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val channelId = "zack_upload_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Upload Status", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}