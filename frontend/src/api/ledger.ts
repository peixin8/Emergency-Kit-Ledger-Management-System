import { ApiError, request, requestBlob } from './http'
import type { DeletedLedgerPage, DeletedQuery, ImportResult, ImportPreviewResult, LedgerFilters, Ledger, LedgerHistoryPage, LedgerInput, LedgerMapPoints, LedgerPage, PageQuery } from '../types/ledger'

function filterParams(query: LedgerFilters) {
  const params = new URLSearchParams()
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.status) params.set('status', query.status)
  if (query.direction) params.set('direction', query.direction)
  if (query.locationStatus) params.set('locationStatus', query.locationStatus)
  return params
}

export function listLedgers(query: PageQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  for (const [key, value] of filterParams(query)) params.set(key, value)
  return request<LedgerPage>(`/ledgers?${params}`, {}, signal)
}
export function getLedger(id: string) { return request<Ledger>(`/ledgers/${encodeURIComponent(id)}`) }
export function listLedgerMapPoints(query: LedgerFilters = {}) { const params = filterParams(query); return request<LedgerMapPoints>(`/ledgers/map-points${params.size ? `?${params}` : ''}`) }
export interface AddressCandidate { displayName: string; latitude: string; longitude: string }
export function searchAddress(address: string) {
  return request<{ results: AddressCandidate[] }>(`/geocoding/search?q=${encodeURIComponent(address)}`)
}
export function createLedger(input: LedgerInput) { return request<Ledger>('/ledgers', { method: 'POST', body: JSON.stringify(input) }) }
export function updateLedger(id: string, input: LedgerInput, version: string) { return request<Ledger>(`/ledgers/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify({ ...input, version }) }) }
export function deleteLedger(id: string, version: string) { return request<unknown>(`/ledgers/${encodeURIComponent(id)}?version=${encodeURIComponent(version)}`, { method: 'DELETE' }) }
export function listLedgerHistory(id: string, page = 1, pageSize = 20) {
  return request<LedgerHistoryPage>(`/ledgers/${encodeURIComponent(id)}/history?page=${page}&pageSize=${pageSize}`)
}
export function exportLedgers(query: LedgerFilters) {
  const params = filterParams(query)
  return requestBlob(`/ledgers/export${params.size ? `?${params}` : ''}`)
}
export async function importLedgers(file: File) {
  const form = new FormData()
  form.append('file', file)
  try {
    return await request<{ importedCount: number; createdCount: number; updatedCount: number; unchangedCount: number }>('/ledgers/import', { method: 'POST', body: form }, undefined, 120_000)
  } catch (error) {
    if (error instanceof ApiError && (error.code === 'TIMEOUT' || error.code === 'NETWORK_ERROR')) {
      throw new ApiError(0, 'IMPORT_STATUS_UNKNOWN', '未收到导入结果，导入结果可能已提交。请先刷新并按台账编号核对，再重试。')
    }
    throw error
  }
}

export function exportLedgerBatches(query: LedgerFilters) {
  const params = filterParams(query)
  return requestBlob(`/ledgers/export-batches${params.size ? `?${params}` : ''}`)
}
export function listDeletedLedgers(query: DeletedQuery, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(query.page), pageSize: String(query.pageSize) })
  if (query.keyword) params.set('keyword', query.keyword)
  if (query.deletedFrom) params.set('deletedFrom', query.deletedFrom)
  if (query.deletedTo) params.set('deletedTo', query.deletedTo)
  return request<DeletedLedgerPage>(`/ledgers/deleted?${params}`, {}, signal)
}
export function previewLedgerImport(file: File) {
  const form = new FormData(); form.append('file', file)
  return request<ImportPreviewResult>('/ledgers/import/preview', { method: 'POST', body: form }, undefined, 120_000)
}
export async function confirmLedgerImport(file: File, previewFingerprint: string) {
  const form = new FormData(); form.append('file', file); form.append('previewFingerprint', previewFingerprint)
  try { return await request<ImportResult>('/ledgers/import/confirm', { method: 'POST', body: form }, undefined, 120_000) }
  catch (error) {
    if (error instanceof ApiError && (error.code === 'TIMEOUT' || error.code === 'NETWORK_ERROR')) throw new ApiError(0, 'IMPORT_STATUS_UNKNOWN', '未收到导入结果，导入结果可能已提交。请先刷新并按台账编号核对，再重新预览。')
    throw error
  }
}
