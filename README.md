# expo-location-keepalive

Keeps **expo-location** background tracking alive on Android when the user swipes the app away, or when an OEM battery manager (Xiaomi, Oppo, Vivo, TECNO, Samsung…) kills it.

Without it, a typical Expo tracking app on these phones:

- stops sending location as soon as it is swiped from recent apps, or
- comes back but sends **one** ping and then nothing, or
- "works" in the background but every request hangs until the app is reopened, or
- stops after 1–3 minutes with no error at all.

Tested on a TECNO KN8 (HiOS, one of the most aggressive battery managers): after a swipe the app was back in **0.26 s** and delivered **121 pings in 10 minutes** (every 5 s, max gap 8 s), with **0 crashes** and the app never reopened.

> Android only. On iOS every function is a safe no-op.

---

## Install

```bash
npx expo install expo-location expo-task-manager @react-native-async-storage/async-storage
npm install expo-location-keepalive
npx expo prebuild   # or just build with EAS — the module is autolinked
```

`app.json` (expo-location plugin):

```json
["expo-location", {
  "isAndroidBackgroundLocationEnabled": true,
  "isAndroidForegroundServiceEnabled": true
}]
```

Not usable in Expo Go (native code) — use a development build or EAS build.

---

## Use

```js
import * as Location from 'expo-location'
import * as TaskManager from 'expo-task-manager'
import AsyncStorage from '@react-native-async-storage/async-storage'
import { startGuard, stopGuard, demoteGuard, fixTaskService, postJson, createOfflineQueue } from 'expo-location-keepalive'

const queue = createOfflineQueue({ storage: AsyncStorage, batchUrl: `${API}/locations/batch`, getHeaders })

TaskManager.defineTask('BG_LOCATION', async ({ data }) => {
  startGuard()
  const fix = data.locations.at(-1)
  const ping = { lat: fix.coords.latitude, lng: fix.coords.longitude, ts: new Date(fix.timestamp).toISOString() }
  const id = await queue.enqueue(ping)
  try {
    const status = await postJson(`${API}/locations`, await getHeaders(), ping)   // NOT fetch
    if (status < 300) { await queue.ack(id); await queue.flush() }
  } catch {}
})

export async function startTracking() {
  fixTaskService()
  await Location.startLocationUpdatesAsync('BG_LOCATION', {
    accuracy: Location.Accuracy.High, timeInterval: 5000, distanceInterval: 0,
    foregroundService: { notificationTitle: 'Tracking', notificationBody: 'Sharing location' },
  })
  startGuard({ notificationTitle: 'Tracking', notificationBody: 'Sharing location' })
  demoteGuard()
}

export async function stopTracking() {          // logout only
  fixTaskService()
  await Location.stopLocationUpdatesAsync('BG_LOCATION')
  stopGuard()
  await queue.clear()
}
```

Full example: [`example/tracking.js`](example/tracking.js) · setup gate: [`example/SetupScreen.js`](example/SetupScreen.js).

### Must-follow rules

1. **`defineTask` at module top level**, imported from your app entry.
2. **The task reads everything from storage** (token, ids) — it may run with no screen and no React state.
3. **Use `postJson` inside the task, not `fetch`.**
4. **Never stop tracking in a component unmount / `useEffect` cleanup.** Swiping the app destroys the activity and unmounts your components while the user is still working. Stop only on logout.
5. **Battery must be "Unrestricted"** for the app. Use the setup gate before starting tracking.

---

## API

| Function | Description |
|---|---|
| `startGuard(opts?)` | Start/keep the guard. Call after `startLocationUpdatesAsync`; cheap to call on every fix. `opts`: `notificationTitle`, `notificationBody`, `channelName` (used for the guard's notification after a background restore). |
| `stopGuard()` | Stop the guard (logout). |
| `demoteGuard()` | Remove the guard's notification when tracking is started from the app screen. |
| `fixTaskService()` | expo-task-manager workaround; call before start/stop location updates. |
| `postJson(url, headers, body, timeoutMs?)` | Native POST, fetch fallback. Resolves HTTP status. |
| `httpPost(...)` | Native POST only; `null` if module missing. |
| `createOfflineQueue(opts)` | Write-ahead ping queue: `enqueue`, `ack`, `flush`, `size`, `clear`. |
| `isBatteryUnrestricted()` | `true` if exempt from battery optimisation. |
| `requestBatteryUnrestricted()` | Opens Android's "allow background" dialog. |
| `openAutoStartSettings()` | Opens brand auto-start screen (Xiaomi, Oppo/Realme/OnePlus, Vivo/iQOO, TECNO/Infinix/itel, Samsung). |
| `brandStep(appName?)` | `{ brand, text }` instruction for this phone, or `null`. |
| `manufacturer`, `isAvailable` | Constants. |

---

## Why it breaks (and what this package does)

Seven separate problems were found with device logs. Every one has to be handled or tracking still dies.

| # | Problem | Fix in this package |
|---|---|---|
| 1 | App code stops tracking in an effect cleanup; swipe → activity destroyed → cleanup runs → **the app kills its own tracking**. | Rule 4 (app side). |
| 2 | OEM manager SIGKILLs the process on swipe even with a foreground service. | `KeepAliveGuardService` is START_STICKY; Android recreates it (~0.3 s on TECNO) and it restores tracking from `onCreate`. |
| 3 | OEM managers hold back alarms (TECNO "proxy alarm"). | Restore runs from the service restart, not an alarm (alarm kept only as backup). |
| 4 | After a background restart, RN `fetch` sends the request but the response never reaches JS → task never finishes → after ~2.5 min Android stops running it. | `postJson` / `httpPost` in Kotlin (`HttpURLConnection`). |
| 5 | expo-task-manager keeps one `TaskService` with a WeakReference to the first React context; after a headless start it is cleared → `SharedPreferences.getAll() on a null object` → every start/stop is rejected. | `TaskServiceContextFix` re-points it to the application context. |
| 6 | expo-location refuses to start its foreground service with no screen ("Foreground location task cannot be started while the app is in the background!") → Android throttles location to a few fixes/hour. | Guard becomes the `location` foreground service itself (only when battery-unrestricted, where Android allows it). |
| 7 | Battery-optimised apps get their background network/starts blocked. | Setup gate: `requestBatteryUnrestricted`, `openAutoStartSettings`. |

**Do not** flip expo-location's internal `AppForegroundedSingleton` to force its own service — restored task options contain `JSONObject.NULL` and it crashes in a loop (`Could not put a value of class org.json.JSONObject$1 to bundle`).

---

## Sockets

Socket.IO / WebSocket connections die when the app is backgrounded or killed — there is no reliable way to keep them open. Send location over HTTP (`postJson`) from the task, and reconnect the socket when `AppState` becomes `active`. Use the socket only for live UI while the screen is open.

---

## Limits

- **Force stop** (Settings → App → Force stop) stops everything until the app is opened. Only an MDM (company phones) can block that.
- Auto-start can't be read back on most brands — the user confirms it.
- Relies on expo-task-manager / expo-location internals (class and field names). Tested with Expo SDK 54, expo-location 19.0.x, expo-task-manager 14.0.x, React Native 0.81. Re-test after upgrading.

## Google Play

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs a declaration in Play Console (permitted for apps whose core function is continuous location tracking). Background location also needs the location permission declaration and a prominent disclosure in the app.

## Debugging

All native lines are logged with tag `LocKeepAlive`:

```bash
adb logcat | grep -E "LocKeepAlive|has died|removeTask"
```

See [docs/TESTING.md](docs/TESTING.md).

## License

MIT
