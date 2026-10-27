import { ApiError, request, requestBlob } from './http'
import type { Ledger, LedgerHistoryPage, LedgerInput, LedgerMapPoints, LedgerPage, PageQuery } from '../types/ledger'

function filterParams(query: Pick<PageQuery, 'keyword' | 'status' | 'direction'>) {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.direction) params.set('direction', query.direction)
  return params
}

export function listLedgers(query: PageQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  for (const [key, value] of filterParams(query)) params.set(key, value)
  return request<LedgerPage>(`/ledgers?${params}`, {}, signal)
}
export function getLedger(id: string) { return request<Ledger>(`/ledgers/${encodeURIComponent(id)}`) }
export function listLedgerMapPoints() { return request<LedgerMapPoints>('/ledgers/map-points') }
export function createLedger(input: LedgerInput) { return request<Ledger>('/ledgers', { method: 'POST', body: JSON.stringify(input) }) }
export function updateLedger(id: string, input: LedgerInput, version: string) { return request<Ledger>(`/ledgers/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify({ ...input, version }) }) }
export function deleteLedger(id: string) { return request<unknown>(`/ledgers/${encodeURIComponent(id)}`, { method: 'DELETE' }) }
export function listLedgerHistory(id: string, page = 1, pageSize = 20) {
  return request<LedgerHistoryPage>(`/ledgers/${encodeURIComponent(id)}/history?page=${page}&pageSize=${pageSize}`)
}
export function exportLedgers(query: Pick<PageQuery, 'keyword' | 'status' | 'direction'>) {
  const params = filterParams(query)
  return requestBlob(`/ledgers/export${params.size ? `?${params}` : ''}`)
}
export async function importLedgers(file: File) {
  const form = new FormData()
  form.append('file', file)
  try {
    return await request<{ importedCount: number }>('/ledgers/import', { method: 'POST', body: form }, undefined, 120_000)
  } catch (error) {
    if (error instanceof ApiError && (error.code === 'TIMEOUT' || error.code === 'NETWORK_ERROR')) {
      throw new ApiError(0, 'IMPORT_STATUS_UNKNOWN', '未收到导入结果，导入结果可能已提交。请先刷新并按台账编号核对，再重试。')
    }
    throw error
  }
}
