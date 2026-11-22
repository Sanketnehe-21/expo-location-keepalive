// Complete integration example. Copy into your app and adjust API_BASE / auth.
//
// Rules that matter (each one was a real bug — see README "Why it breaks"):
//  1. defineTask at module top level (imported from your app entry).
//  2. Read everything the task needs from storage — it may run with no screen.
//  3. Send with postJson (native HTTP), never plain fetch, inside the task.
//  4. Never stop tracking in a component unmount / effect cleanup — only on logout.

import * as Location from 'expo-location'
import * as TaskManager from 'expo-task-manager'
import AsyncStorage from '@react-native-async-storage/async-storage'
import {
  startGuard, stopGuard, demoteGuard, fixTaskService,
  postJson, createOfflineQueue,
} from 'expo-location-keepalive'

const API_BASE = 'https://your-server.example.com/api'
export const LOCATION_TASK = 'BG_LOCATION'

const getHeaders = async () => {
  const token = await AsyncStorage.getItem('auth_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export const queue = createOfflineQueue({
  storage: AsyncStorage,
  batchUrl: `${API_BASE}/locations/batch`,
  getHeaders,
})

let lastFlush = 0

// 1. Top level — runs for every fix, including after a background restart
TaskManager.defineTask(LOCATION_TASK, async ({ data, error }) => {
  if (error || !data?.locations?.length) return
  startGuard()                                   // cheap; re-arms after a native restart

  const fix = data.locations[data.locations.length - 1]
  const ping = {
    lat: fix.coords.latitude,
    lng: fix.coords.longitude,
    accuracy: fix.coords.accuracy,
    ts: new Date(fix.timestamp).toISOString(),
  }

  const id = await queue.enqueue(ping)          // save first — never lost
  try {
    const status = await postJson(`${API_BASE}/locations`, await getHeaders(), ping)
    if (status >= 200 && status < 300) {
      await queue.ack(id)
      // Network is back → send what was saved while offline (max once a minute)
      if (Date.now() - lastFlush > 60_000) { lastFlush = Date.now(); await queue.flush() }
    }
  } catch {
    // stays queued; sent by a later flush
  }
})

// Call from the app screen (e.g. after login / when a job starts)
export async function startTracking() {
  fixTaskService()
  const running = await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK).catch(() => false)
  if (running) await Location.stopLocationUpdatesAsync(LOCATION_TASK)

  await Location.startLocationUpdatesAsync(LOCATION_TASK, {
    accuracy: Location.Accuracy.High,
    timeInterval: 5_000,
    distanceInterval: 0,                         // timer only: a stationary user still pings
    pausesUpdatesAutomatically: false,
    foregroundService: {
      notificationTitle: 'Location tracking',
      notificationBody: 'Sharing your live location',
    },
  })
  startGuard({ notificationTitle: 'Location tracking', notificationBody: 'Sharing your live location' })
  demoteGuard()                                  // screen is open → expo's notification is enough
}

// Only on logout — NOT in a useEffect cleanup
export async function stopTracking() {
  fixTaskService()
  const running = await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK).catch(() => false)
  if (running) await Location.stopLocationUpdatesAsync(LOCATION_TASK)
  stopGuard()
  await queue.clear()
}
