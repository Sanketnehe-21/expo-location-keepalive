import { Native } from './native'

/**
 * POST a JSON body natively. Resolves to the HTTP status code, rejects on a
 * network error. Resolves null when the native module isn't available.
 */
export async function httpPost(url, headers = {}, body = {}, timeoutMs = 15000) {
  if (!Native?.httpPost) return null
  return Native.httpPost(url, headers || {}, typeof body === 'string' ? body : JSON.stringify(body), timeoutMs)
}

/**
 * POST JSON, native first, fetch as fallback. Use this inside your
 * TaskManager.defineTask location task. Resolves to the HTTP status.
 */
export async function postJson(url, headers = {}, body = {}, timeoutMs = 15000) {
  const payload = typeof body === 'string' ? body : JSON.stringify(body)
  const nativeStatus = await httpPost(url, headers, payload, timeoutMs)
  if (nativeStatus !== null) return nativeStatus
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: payload,
  })
  return res.status
}
