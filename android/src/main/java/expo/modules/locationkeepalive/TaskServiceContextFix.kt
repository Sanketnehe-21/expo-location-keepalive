package expo.modules.locationkeepalive

import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference

// Works around an expo-task-manager bug that background restarts expose.
//
// TaskManagerPackage keeps ONE TaskService per process, holding a WeakReference
// to whichever React context created it first. When Android starts the app in
// the background (no screen), that is the headless context; once it is
// discarded the reference is cleared, and every later start/stop of location
// updates fails with "SharedPreferences.getAll() on a null object" — tracking
// can't be (re)started even after the user opens the app. All other TaskService
// state is static, so pointing it at the application context is safe.
internal object TaskServiceContextFix {
  fun apply(context: Context) {
    try {
      val appContext = context.applicationContext
      val holder = Class.forName("expo.modules.taskManager.TaskManagerPackage").getDeclaredField("mTaskService")
      holder.isAccessible = true
      val service = holder.get(null) ?: return

      val ref = service.javaClass.getDeclaredField("mContextRef")
      ref.isAccessible = true
      val current = (ref.get(service) as? WeakReference<*>)?.get()
      if (current !== appContext) {
        ref.set(service, WeakReference<Context>(appContext))
        Log.i(KeepAliveConfig.TAG, "taskservice-context-fixed wasNull=${current == null}")
      }
    } catch (e: Exception) {
      Log.w(KeepAliveConfig.TAG, "taskservice-fix-failed: ${e.message}")
    }
  }
}
