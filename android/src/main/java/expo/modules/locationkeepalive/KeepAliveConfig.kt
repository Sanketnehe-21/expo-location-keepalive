package expo.modules.locationkeepalive

import android.content.Context

// Settings the native side needs AFTER the app process was killed and restarted
// in the background — JS isn't running then, so they are stored on disk when JS
// calls startGuard().
internal object KeepAliveConfig {
  const val TAG = "LocKeepAlive"
  private const val PREFS = "expo_location_keepalive"

  private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun save(ctx: Context, title: String?, body: String?, channelName: String?) {
    prefs(ctx).edit()
      .putString("title", title)
      .putString("body", body)
      .putString("channelName", channelName)
      .apply()
  }

  fun title(ctx: Context): String = prefs(ctx).getString("title", null) ?: "Location tracking"
  fun body(ctx: Context): String = prefs(ctx).getString("body", null) ?: "Sharing your live location"
  fun channelName(ctx: Context): String = prefs(ctx).getString("channelName", null) ?: "Location tracking"
}
