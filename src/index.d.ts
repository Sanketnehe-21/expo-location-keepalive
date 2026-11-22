/** true on Android when the native module is linked. */
export const isAvailable: boolean

/** Lower-case Build.MANUFACTURER, e.g. "xiaomi", "tecno". Empty on iOS. */
export const manufacturer: string

export interface GuardOptions {
  /** Title of the guard's notification (shown only after a background restore). */
  notificationTitle?: string
  /** Body of the guard's notification. */
  notificationBody?: string
  /** Android notification channel name shown in system settings. */
  channelName?: string
}

/** Start (or keep) the guard. Call right after Location.startLocationUpdatesAsync. Cheap to repeat. */
export function startGuard(options?: GuardOptions): void
/** Stop the guard. Call together with Location.stopLocationUpdatesAsync. */
export function stopGuard(): void
/** Remove the guard's notification once tracking is started from the app screen again. */
export function demoteGuard(): void
/** expo-task-manager workaround. Call just before start/stopLocationUpdatesAsync. */
export function fixTaskService(): void

/** Native POST (Android). Resolves the HTTP status; null if the module is unavailable; rejects on network error. */
export function httpPost(url: string, headers?: Record<string, string>, body?: unknown, timeoutMs?: number): Promise<number | null>
/** Native POST first, fetch fallback. Resolves the HTTP status. */
export function postJson(url: string, headers?: Record<string, string>, body?: unknown, timeoutMs?: number): Promise<number>

/** true when the app is exempt from battery optimisation (always true on iOS). */
export function isBatteryUnrestricted(): boolean
/** Opens Android's "allow background" dialog. */
export function requestBatteryUnrestricted(): boolean
/** Opens the brand auto-start screen; false if no known screen (app-info opened instead). */
export function openAutoStartSettings(): boolean

export interface BrandStep {
  brand: 'xiaomi' | 'oppo' | 'vivo' | 'tecno' | 'samsung'
  text: string
}
/** Extra setting this phone's brand needs, or null. */
export function brandStep(appName?: string): BrandStep | null

export interface KeyValueStorage {
  getItem(key: string): Promise<string | null>
  setItem(key: string, value: string): Promise<void>
  removeItem(key: string): Promise<void>
}

export interface OfflineQueueOptions {
  storage: KeyValueStorage
  /** Endpoint accepting POST { pings: [...] } */
  batchUrl: string
  getHeaders?: () => Record<string, string> | Promise<Record<string, string>>
  key?: string
  maxItems?: number
  maxAgeMs?: number
  chunkSize?: number
  timeoutMs?: number
}

export interface OfflineQueue {
  /** Save a ping before sending. Returns its id for ack(). */
  enqueue(ping: Record<string, unknown>): Promise<string | null>
  /** Server accepted the ping — remove it. */
  ack(id: string | null): Promise<void>
  /** Send all saved pings in chunks. Returns how many were delivered. */
  flush(): Promise<number>
  size(): Promise<number>
  /** Best-effort flush, then delete everything (logout). */
  clear(): Promise<void>
}

export function createOfflineQueue(options: OfflineQueueOptions): OfflineQueue
