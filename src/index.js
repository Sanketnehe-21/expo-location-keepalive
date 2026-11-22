// Android-only. On iOS (or a build without the native module) every call is a
// safe no-op and httpPost resolves null so callers fall back to fetch.
import { Native } from './native'

export const isAvailable = !!Native
export const manufacturer = Native?.manufacturer || ''

/**
 * Start (or keep) the guard. Call right after Location.startLocationUpdatesAsync,
 * and it is cheap to call again on every location fix.
 * The texts are used for the guard's own notification, shown only while the app
 * was restored in the background with no screen.
 */
export function startGuard({ notificationTitle, notificationBody, channelName } = {}) {
  try { Native?.startGuard(notificationTitle ?? null, notificationBody ?? null, channelName ?? null) } catch {}
}

/** Stop the guard — call together with Location.stopLocationUpdatesAsync (e.g. logout). */
export function stopGuard() {
  try { Native?.stopGuard() } catch {}
}

/** Remove the guard's notification once tracking was (re)started from the app screen. */
export function demoteGuard() {
  try { Native?.demoteGuard() } catch {}
}

/** Call just before start/stopLocationUpdatesAsync (expo-task-manager workaround). */
export function fixTaskService() {
  try { Native?.fixTaskService() } catch {}
}

/** true when Android won't put the app to sleep in the background (true on iOS). */
export function isBatteryUnrestricted() {
  try { return Native ? Native.isIgnoringBatteryOptimizations() : true } catch { return true }
}

/** Opens Android's "allow background" dialog (or the battery list). */
export function requestBatteryUnrestricted() {
  try { return !!Native?.requestIgnoreBatteryOptimizations() } catch { return false }
}

/** Opens the brand's auto-start screen; false (app-info opened instead) if unknown. */
export function openAutoStartSettings() {
  try { return !!Native?.openAutoStartSettings() } catch { return false }
}

const BRAND_STEPS = [
  { brand: 'xiaomi',  match: ['xiaomi', 'redmi', 'poco'],    text: app => `Turn ON "Autostart" for ${app}, then open Recent apps, long-press ${app} and tap the lock` },
  { brand: 'oppo',    match: ['oppo', 'realme', 'oneplus'],  text: app => `Turn ON "Allow auto launch" (Auto startup) for ${app}` },
  { brand: 'vivo',    match: ['vivo', 'iqoo'],               text: app => `Turn ON "Autostart" and "High background power usage" for ${app}` },
  { brand: 'tecno',   match: ['tecno', 'infinix', 'itel'],   text: app => `Phone Master → turn ON "Auto-start" for ${app}` },
  { brand: 'samsung', match: ['samsung'],                    text: app => `Background usage limits → "Never sleeping apps" → add ${app}` },
]

/**
 * The extra step this phone's brand needs (null when none).
 * @param {string} appName shown in the instruction text
 */
export function brandStep(appName = 'the app') {
  const b = BRAND_STEPS.find(s => s.match.some(m => manufacturer.includes(m)))
  return b ? { brand: b.brand, text: b.text(appName) } : null
}

export { httpPost, postJson } from './http'
export { createOfflineQueue } from './offlineQueue'
