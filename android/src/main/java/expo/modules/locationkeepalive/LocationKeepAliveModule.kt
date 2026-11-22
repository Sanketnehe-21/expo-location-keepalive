package expo.modules.locationkeepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.HttpURLConnection
import java.net.URL

class LocationKeepAliveModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("No React context")

  override fun definition() = ModuleDefinition {
    Name("LocationKeepAlive")

    // Runs for every React context (app screen and background/headless alike),
    // before JS can call into expo-location.
    OnCreate { TaskServiceContextFix.apply(context) }

    OnActivityEntersForeground { KeepAliveGuardService.appVisible = true }
    OnActivityEntersBackground { KeepAliveGuardService.appVisible = false }

    Constant("manufacturer") { Build.MANUFACTURER.lowercase() }

    // Call right after Location.startLocationUpdatesAsync (and cheaply on every
    // fix) so the guard is in place to catch a swipe-away / OEM kill.
    Function("startGuard") { title: String?, body: String?, channelName: String? ->
      KeepAliveConfig.save(context, title, body, channelName)
      KeepAliveGuardService.ensureRunning(context)
    }

    Function("stopGuard") { KeepAliveGuardService.stop(context) }

    // App screen started tracking again (expo's notification is back)
    Function("demoteGuard") { KeepAliveGuardService.demote() }

    // Call just before start/stopLocationUpdatesAsync
    Function("fixTaskService") { TaskServiceContextFix.apply(context) }

    // HTTP POST done natively. When Android restarts the app in the background
    // (no screen), React Native's fetch sends the request but its response never
    // reaches JS: the task never finishes, and after a few minutes Android stops
    // running it. Promises from native modules still resolve there.
    // Returns the HTTP status; throws on a network error. Runs off the main thread.
    AsyncFunction("httpPost") { url: String, headers: Map<String, String>, body: String, timeoutMs: Int ->
      val conn = URL(url).openConnection() as HttpURLConnection
      try {
        conn.requestMethod = "POST"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        // Drain the body so the connection can be reused
        (if (status >= 400) conn.errorStream else conn.inputStream)?.use { it.readBytes() }
        status
      } finally {
        conn.disconnect()
      }
    }

    Function("isIgnoringBatteryOptimizations") {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    // Android's own "Let app always run in background?" dialog; falls back to
    // the battery list if a vendor build blocks the dialog.
    Function("requestIgnoreBatteryOptimizations") {
      val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
      launch(direct) || launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    // Brand auto-start / "never sleeping" screen. Returns false when none of the
    // known screens exist; the app-info page is opened instead.
    Function("openAutoStartSettings") {
      AUTOSTART_SCREENS.any { (pkg, cls) -> launch(Intent().setComponent(ComponentName(pkg, cls))) } ||
        !launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
  }

  private fun launch(intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
  } catch (e: Exception) {
    false
  }

  companion object {
    // Tried in order; the first one present on the phone opens.
    private val AUTOSTART_SCREENS = listOf(
      // Xiaomi / Redmi / POCO
      "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
      // Oppo / Realme / OnePlus (ColorOS)
      "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
      "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
      "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
      "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
      // Vivo / iQOO
      "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
      "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
      // TECNO / Infinix / itel (Transsion "Phone Master")
      "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
      "com.transsion.phonemanager" to "com.itel.autobootmanager.activity.AutoBootMgrActivity",
      // Samsung — battery page holding "Never sleeping apps"
      "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
      "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )
  }
}
