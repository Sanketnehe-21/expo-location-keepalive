package expo.modules.locationkeepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

// Brings background tracking back after the app process was killed.
//
// Tracking itself is expo-location's: its task is persisted by
// expo-task-manager, and a fresh process restores it (location updates) as soon
// as a TaskService is created. TaskBroadcastReceiver creates one on ANY
// broadcast, so "restart tracking" is just waking that receiver. If JS stopped
// tracking (stopLocationUpdatesAsync), nothing is persisted and this is a no-op.
internal object KeepAliveWake {
  const val ACTION_WAKE = "expo.modules.locationkeepalive.WAKE"
  private const val EXPO_TASK_RECEIVER = "expo.modules.taskManager.TaskBroadcastReceiver"

  fun kick(context: Context, reason: String) {
    val ctx = context.applicationContext
    Log.i(KeepAliveConfig.TAG, "wake reason=$reason")
    TaskServiceContextFix.apply(ctx)
    try {
      ctx.sendBroadcast(Intent(ACTION_WAKE).setClassName(ctx.packageName, EXPO_TASK_RECEIVER))
    } catch (e: Exception) {
      Log.w(KeepAliveConfig.TAG, "wake failed: ${e.message}")
    }
    // Restored in the background (no screen): expo-location refuses to start
    // its own foreground service there. The guard becomes the location
    // foreground service instead, so fixes keep arriving at full rate.
    if (KeepAliveGuardService.needsForeground(ctx)) KeepAliveGuardService.startForeground(ctx)
    else if (reason != "guard-created") KeepAliveGuardService.ensureRunning(ctx)
  }

  // Inexact on purpose: needs no exact-alarm permission. Only a backup to the
  // guard restart — OEM power managers (e.g. TECNO "proxy alarm") may delay it.
  fun scheduleRestart(context: Context, delayMs: Long) {
    val ctx = context.applicationContext
    val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pi = PendingIntent.getBroadcast(
      ctx, 0,
      Intent(ctx, KeepAliveWakeReceiver::class.java).setAction(ACTION_WAKE),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMs, pi)
    Log.i(KeepAliveConfig.TAG, "restart-armed inMs=$delayMs")
  }
}
