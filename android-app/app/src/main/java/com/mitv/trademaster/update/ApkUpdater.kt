package com.mitv.trademaster.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads the update APK via the system DownloadManager (shows in the
 * notification shade with progress, survives app backgrounding) and then
 * triggers the standard Android package-install prompt.
 *
 * IMPORTANT: installing an APK with the SAME applicationId and a matching
 * signing key over an existing install upgrades it in place — the user's
 * login session, local settings, and app data are preserved. This is NOT
 * an uninstall/reinstall; Android treats it as a normal app update. It
 * only fails if the new APK is signed with a different key than the one
 * currently installed, which is why the GitHub Actions workflow must keep
 * using the SAME debug keystore for every release (see the persisted
 * keystore step in the workflow).
 */
object ApkUpdater {

    private const val APK_FILE_NAME = "mi-trade-master-update.apk"

    fun startDownload(context: Context, apkUrl: String, onProgress: (Int) -> Unit, onComplete: (File?) -> Unit) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (destinationFile.exists()) destinationFile.delete()

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("MI Trade Master Update")
            .setDescription("Downloading the latest version...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destinationFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    if (destinationFile.exists() && destinationFile.length() > 0) {
                        onComplete(destinationFile)
                    } else {
                        onComplete(null)
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        // Lightweight progress polling — DownloadManager doesn't push
        // progress via broadcast, so we poll its own query API.
        Thread {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (bytesTotal > 0) {
                        onProgress(((bytesDownloaded * 100L) / bytesTotal).toInt())
                    }
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        downloading = false
                    }
                } else {
                    downloading = false
                }
                cursor.close()
                if (downloading) Thread.sleep(300)
            }
        }.start()
    }

    /** Launches the system install prompt for the downloaded APK file. */
    fun installApk(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
