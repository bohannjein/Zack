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
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString("file_path") ?: return@withContext Result.failure()
        val serverId = inputData.getLong("server_id", -1)
        val workTag = inputData.getString("work_tag") ?: ""
        if (serverId == -1L) return@withContext Result.failure()

        val context = applicationContext
        val db = DatabaseInstance.get(context)
        val server = db.serverDao().getServerByIdSync(serverId) ?: return@withContext Result.failure()
        val password = com.bohannjein.zack.utils.SecureStorage(context).getPassword(serverId) ?: ""

        val file = File(filePath)
        if (!file.exists()) return@withContext Result.failure()

        val fileName = file.name
        val fileSize = file.length()

        val historyId = db.historyDao().insert(
            UploadEntry(fileName = fileName, serverName = server.displayName, status = "Uploading", fileSize = fileSize, workTag = workTag)
        )

        val notifId = (PROGRESS_NOTIF_BASE + historyId).toInt()
        ensureNotificationChannel(context)
        postProgressNotification(context, notifId, fileName, 0, fileSize)

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
                    val remotePath = subPath + fileName
                    val remoteFile = diskShare.openFile(
                        remotePath, setOf(AccessMask.GENERIC_WRITE), null,
                        SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null
                    )

                    FileInputStream(file).use { input ->
                        val buffer = ByteArray(8192)
                        var offset = 0L
                        var bytesRead: Int
                        var lastReportedPercent = -1

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            remoteFile.write(java.nio.ByteBuffer.wrap(buffer, 0, bytesRead), offset)
                            offset += bytesRead

                            if (fileSize > 0) {
                                val percent = (offset * 100 / fileSize).toInt()
                                if (percent != lastReportedPercent && percent % 5 == 0) {
                                    postProgressNotification(context, notifId, fileName, offset, fileSize)
                                    lastReportedPercent = percent
                                }
                            }
                        }
                    }

                    remoteFile.close()
                }
            }

            cancelProgressNotification(context, notifId)
            db.historyDao().updateStatus(historyId, "Success")
            sendFinalNotification(context, context.getString(R.string.notification_upload_successful), fileName)
            file.delete()
            Result.success()

        } catch (e: IOException) {
            cancelProgressNotification(context, notifId)
            val errMsg = friendlyNetworkError(e)
            android.util.Log.e(TAG, "Network error uploading $fileName to ${server.displayName}", e)
            db.historyDao().updateStatusAndError(historyId, "Failed", errMsg)
            sendFinalNotification(context, context.getString(R.string.notification_upload_failed), errMsg)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

        } catch (e: Exception) {
            cancelProgressNotification(context, notifId)
            val errMsg = friendlyGenericError(e)
            android.util.Log.e(TAG, "Upload failed for $fileName to ${server.displayName}", e)
            db.historyDao().updateStatusAndError(historyId, "Failed", errMsg)
            sendFinalNotification(context, context.getString(R.string.notification_upload_failed), errMsg)
            Result.failure()

        } finally {
            client.close()
        }
    }

    private fun friendlyNetworkError(e: IOException): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            "connection refused" in msg -> "Server not reachable – is it online?"
            "timed out" in msg || "timeout" in msg -> "Connection timed out – check network"
            "no route to host" in msg -> "No route to server – check IP address"
            "authentication" in msg || "logon" in msg -> "Wrong username or password"
            else -> "Network error – check connection"
        }
    }

    private fun friendlyGenericError(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            "authentication" in msg || "logon" in msg || "access denied" in msg -> "Wrong username or password"
            "share" in msg || "path" in msg -> "Share path not found – check remote path"
            else -> "Upload failed – try again"
        }
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun postProgressNotification(context: Context, notifId: Int, fileName: String, bytesWritten: Long, totalBytes: Long) {
        val prefs = context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val percent = if (totalBytes > 0) (bytesWritten * 100 / totalBytes).toInt() else 0
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(context.getString(R.string.notification_upload_progress, fileName))
            .setProgress(100, percent, totalBytes == 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(notifId, notification)
    }

    private fun cancelProgressNotification(context: Context, notifId: Int) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId)
    }

    private fun sendFinalNotification(context: Context, title: String, message: String) {
        val prefs = context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TAG = "UploadWorker"
        private const val CHANNEL_ID = "zack_upload_channel"
        private const val PROGRESS_NOTIF_BASE = 10_000L
        private const val MAX_RETRIES = 3
    }
}
