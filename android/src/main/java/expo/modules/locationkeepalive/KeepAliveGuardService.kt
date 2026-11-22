package expo.modules.locationkeepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

// Runs while location tracking is on.
//  - Plain service while the app is open: expo-location's own foreground
//    service keeps the app alive and this one just watches.
//  - onTaskRemoved: the user swiped the app away → arm a backup restart alarm.
//  - Recreated by Android after an OEM kill (START_STICKY) → restore tracking
//    straight from onCreate (OEM power managers often hold back alarms).
//  - After a background restore it becomes the "location" foreground service
//    itself: expo-location refuses to start one without a visible screen, and
//    without one Android throttles location to a few fixes an hour.
class KeepAliveGuardService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    running = true
    instance = this
    Log.i(KeepAliveConfig.TAG, "guard-created")
    // Harmless when the app is already tracking — the restore is a no-op then.
    KeepAliveWake.kick(this, "guard-created")
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val foreground = intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true
    // intent == null means Android restarted us after killing the process
    Log.i(KeepAliveConfig.TAG, "guard-start sticky=${intent == null} fg=$foreground")
    if (foreground) goForeground()
    if (intent == null) KeepAliveWake.kick(this, "sticky-restart")
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    Log.i(KeepAliveConfig.TAG, "task-removed")
    // Backup for phones that kill the process a moment later
    KeepAliveWake.scheduleRestart(this, 3_000)
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    Log.i(KeepAliveConfig.TAG, "guard-destroyed")
    running = false
    if (instance === this) instance = null
    super.onDestroy()
  }

  private fun goForeground() {
    try {
      val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      if (nm.getNotificationChannel(CHANNEL_ID) == null) {
        nm.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, KeepAliveConfig.channelName(this), NotificationManager.IMPORTANCE_LOW)
        )
      }
      val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
      }
      val notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle(KeepAliveConfig.title(this))
        .setContentText(KeepAliveConfig.body(this))
        .setSmallIcon(applicationInfo.icon)
        .setOngoing(true)
        .setContentIntent(open)
        .setCategory(Notification.CATEGORY_SERVICE)
        .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      Log.i(KeepAliveConfig.TAG, "guard-foreground")
    } catch (e: Exception) {
      // Started with startForegroundService(): if startForeground() can't be
      // called, Android crashes the app 30 s later. Stop instead.
      Log.w(KeepAliveConfig.TAG, "guard-foreground-failed: ${e.message}")
      stopSelf()
    }
  }

  companion object {
    private const val CHANNEL_ID = "expo-location-keepalive"
    private const val NOTIFICATION_ID = 481757
    private const val EXTRA_FOREGROUND = "foreground"

    @Volatile var running = false
    @Volatile private var instance: KeepAliveGuardService? = null

    // Set by LocationKeepAliveModule from the activity lifecycle. A process
    // Android restored in the background never shows the screen, so this stays
    // false. (Process "importance" can't be used: Android reports foreground
    // while it is still starting this service.)
    @Volatile var appVisible = false

    fun ensureRunning(context: Context) {
      if (running) return
      try {
        context.startService(Intent(context, KeepAliveGuardService::class.java))
      } catch (e: Exception) {
        // Background start refused — the next JS start/fix call will retry
        Log.w(KeepAliveConfig.TAG, "guard-start-refused: ${e.message}")
      }
    }

    // True when the screen isn't showing (restored in the background) and
    // Android will let us start a foreground service: battery "unrestricted"
    // is what exempts the app from the background-start ban.
    fun needsForeground(context: Context): Boolean {
      if (appVisible) return false
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun startForeground(context: Context) {
      try {
        context.startForegroundService(
          Intent(context, KeepAliveGuardService::class.java).putExtra(EXTRA_FOREGROUND, true)
        )
      } catch (e: Exception) {
        Log.w(KeepAliveConfig.TAG, "guard-foreground-refused: ${e.message}")
      }
    }

    // The app screen started tracking again (expo's notification is back) —
    // drop ours so the user doesn't see two.
    fun demote() {
      instance?.let {
        it.stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(KeepAliveConfig.TAG, "guard-demoted")
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, KeepAliveGuardService::class.java))
    }
  }
}
