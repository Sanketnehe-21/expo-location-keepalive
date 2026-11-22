package expo.modules.locationkeepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Target of the backup restart alarm armed when the app is swiped away.
class KeepAliveWakeReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    KeepAliveWake.kick(context, "swipe-alarm")
  }
}
