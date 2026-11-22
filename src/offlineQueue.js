import { postJson } from './http'

/**
 * Write-ahead queue for location pings.
 *
 * Each ping is saved BEFORE it is sent and removed only after the server
 * accepted it, so no ping is lost when the network is down, suspended by the
 * OS, or the app is killed mid-request. Saved pings are sent later in chunks,
 * with their original timestamps, so the server can rebuild the true route.
 *
 * @param {object} opts
 * @param {object} opts.storage      AsyncStorage-compatible { getItem, setItem, removeItem }
 * @param {string} opts.batchUrl     endpoint accepting POST { pings: [...] }
 * @param {() => Promise<object>|object} [opts.getHeaders]  e.g. Authorization
 * @param {string} [opts.key]        storage key
 * @param {number} [opts.maxItems]   oldest dropped beyond this (default 1500 ≈ 2 h at 5 s)
 * @param {number} [opts.maxAgeMs]   pings older than this are dropped (default 24 h)
 * @param {number} [opts.chunkSize]  pings per batch request (default 200)
 * @param {number} [opts.timeoutMs]  per request (default 20000)
 */
export function createOfflineQueue({
  storage,
  batchUrl,
  getHeaders = () => ({}),
  key = 'location_keepalive_queue',
  maxItems = 1500,
  maxAgeMs = 24 * 60 * 60 * 1000,
  chunkSize = 200,
  timeoutMs = 20000,
}) {
  let seq = 0

  const read = async () => {
    const raw = await storage.getItem(key)
    return raw ? JSON.parse(raw) : []
  }
  // An empty queue is removed outright — nothing is left on the phone once
  // every ping has been delivered.
  const write = async (q) => {
    if (q.length) await storage.setItem(key, JSON.stringify(q))
    else await storage.removeItem(key)
  }

  return {
    /** Save a ping before sending it. Returns its id (pass to ack). */
    async enqueue(ping) {
      try {
        const id = `${Date.now()}-${seq++}`
        const cutoff = Date.now() - maxAgeMs
        let q = (await read()).filter(p => !p.ts || new Date(p.ts).getTime() >= cutoff)
        q.push({ ...ping, __id: id })
        if (q.length > maxItems) q = q.slice(q.length - maxItems)
        await write(q)
        return id
      } catch {
        return null
      }
    },

    /** The server accepted this ping — drop it. */
    async ack(id) {
      if (!id) return
      try { await write((await read()).filter(p => p.__id !== id)) } catch {}
    },

    /** Send everything saved, in chunks. Returns how many were delivered. */
    async flush() {
      let total = 0
      try {
        const headers = await getHeaders()
        for (;;) {
          const chunk = (await read()).slice(0, chunkSize)
          if (!chunk.length) return total
          const status = await postJson(batchUrl, headers, { pings: chunk.map(({ __id, ...p }) => p) }, timeoutMs)
          // A rejected batch stays queued — dropping it would lose the pings
          if (status < 200 || status >= 300) return total
          const sent = new Set(chunk.map(p => p.__id))
          await write((await read()).filter(p => !sent.has(p.__id)))
          total += chunk.length
        }
      } catch {
        return total
      }
    },

    async size() {
      try { return (await read()).length } catch { return 0 }
    },

    /** Logout: best-effort send, then delete everything. */
    async clear() {
      await this.flush().catch(() => {})
      await storage.removeItem(key).catch(() => {})
    },
  }
}
