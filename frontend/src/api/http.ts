export interface Envelope<T> { code: string; message: string; data: T; traceId?: string }
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}
const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
export function apiUrl(path: string) { return `${API_BASE}/api/v1${path}` }

function requestHeaders(options: RequestInit) {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  return headers
}

export async function request<T>(path: string, options: RequestInit = {}, signal?: AbortSignal, timeoutMs = 15000): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  try {
    const response = await fetch(apiUrl(path), {
      ...options,
      headers: requestHeaders(options),
      signal: controller.signal
    })
    const body: unknown = await response.json().catch(() => null)
    const envelope = isEnvelope<T>(body) ? body : null
    if (!response.ok || !envelope || envelope.code !== 'OK') {
      throw new ApiError(response.status, envelope?.code ?? 'HTTP_ERROR', envelope?.message ?? `请求失败（${response.status}）`)
    }
    return envelope.data
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(0, 'TIMEOUT', '请求超时或已取消')
    if (error instanceof ApiError) throw error
    throw new ApiError(0, 'NETWORK_ERROR', '网络连接失败，请稍后重试')
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', abort)
  }
}
export async function requestBlob(path: string, signal?: AbortSignal): Promise<Blob> {
  try {
    const response = await fetch(apiUrl(path), { signal })
    if (!response.ok) {
      const body: unknown = await response.json().catch(() => null)
      const envelope = isEnvelope<unknown>(body) ? body : null
      throw new ApiError(response.status, envelope?.code ?? 'HTTP_ERROR', envelope?.message ?? `下载失败（${response.status}）`)
    }
    return await response.blob()
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error instanceof DOMException && error.name === 'AbortError') throw new ApiError(0, 'CANCELLED', '下载已取消')
    throw new ApiError(0, 'NETWORK_ERROR', '网络连接失败，请稍后重试')
  }
}
function isEnvelope<T>(value: unknown): value is Envelope<T> {
  return typeof value === 'object' && value !== null && 'code' in value && typeof value.code === 'string' && 'message' in value && typeof value.message === 'string' && 'data' in value
}
