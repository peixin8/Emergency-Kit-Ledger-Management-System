import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './http'
import { exportLedgers, importLedgers, listLedgerHistory, listLedgerMapPoints, listLedgers, updateLedger } from './ledger'
import { emptyLedger } from '../types/ledger'

const success = (data: unknown) => new Response(JSON.stringify({ code: 'OK', message: '成功', data }), { status: 200, headers: { 'Content-Type': 'application/json' } })
beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks() })

describe('台账请求', () => {
  it('分页和筛选按契约传递，未传的筛选不会出现在 URL', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({ records: [], total: 0, page: 2, pageSize: 20 }))
    await listLedgers({ page: 2, pageSize: 20, keyword: '机房', direction: 'IN' })
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('page=2&pageSize=20&keyword=%E6%9C%BA%E6%88%BF&direction=IN')
    expect(String(fetcher.mock.calls[0]?.[0])).not.toContain('status=')
  })
  it('修改请求保留字符串 ID 和版本号', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({}))
    await updateLedger('9007199254740993', emptyLedger(), '9007199254740995')
    const [url, options] = fetcher.mock.calls[0]!
    expect(url).toContain('/ledgers/9007199254740993')
    expect(JSON.parse(String(options?.body)).version).toBe('9007199254740995')
  })
  it('修改导入台账时传回 PTN、设备状况说明及未填写方向', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({}))
    await updateLedger('42', { ...emptyLedger(), direction: 'UNSPECIFIED', ptnPort: '网元 A+槽口 2', deviceConditionDetail: '开关电源故障', originalAddress: '同一地址', destinationAddress: '同一地址' }, '3')
    const body = JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))
    expect(body).toMatchObject({ direction: 'UNSPECIFIED', ptnPort: '网元 A+槽口 2', deviceConditionDetail: '开关电源故障', originalAddress: '同一地址', destinationAddress: '同一地址' })
  })
  it('后端版本冲突以可识别错误返回', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ code: 'CONFLICT', message: '记录已更新', data: null }), { status: 409 }))
    await expect(updateLedger('1', emptyLedger(), '0')).rejects.toMatchObject({ status: 409, code: 'CONFLICT' } satisfies Partial<ApiError>)
  })
  it('历史查询保留字符串 ID 和分页参数', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({ records: [], total: 0, page: 2, pageSize: 10 }))
    await listLedgerHistory('9007199254740993', 2, 10)
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('/ledgers/9007199254740993/history?page=2&pageSize=10')
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false)
  })
  it('地图点位从全部有效台账专用接口读取，保留未定位数量', async () => {
    const page = { records: [{ id: '9007199254740993', ledgerCode: 'XM-001', destinationAddress: '厦门市思明区', deviceCondition: 'GOOD', latitude: '24.4798345', longitude: '118.0894251' }], unlocatedCount: 2 }
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success(page))
    expect(await listLedgerMapPoints()).toEqual(page)
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('/api/v1/ledgers/map-points')
  })
  it('遗留登录令牌不会随共享请求发送', async () => {
    sessionStorage.setItem('emergency-ledger-token', 'old-token')
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({ records: [], total: 0, page: 1, pageSize: 20 }))
    await listLedgers({ page: 1, pageSize: 20 })
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false)
  })
  it('Excel 导入使用 multipart 而非 JSON', async () => {
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(success({ importedCount: 1, createdCount: 0, updatedCount: 0, unchangedCount: 1 }))
    const result = await importLedgers(new File(['xlsx'], 'ledger.xlsx'))
    expect(result).toEqual({ importedCount: 1, createdCount: 0, updatedCount: 0, unchangedCount: 1 })
    expect(fetcher.mock.calls[0]?.[1]?.body).toBeInstanceOf(FormData)
    expect(new Headers(fetcher.mock.calls[0]?.[1]?.headers).has('Content-Type')).toBe(false)
  })
  it('Excel 导入在 15 秒后继续等待，120 秒超时提示先核对结果', async () => {
    vi.useFakeTimers()
    try {
      vi.spyOn(globalThis, 'fetch').mockImplementation((_url, options) => new Promise((_resolve, reject) => {
        options?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
      }))
      const pending = importLedgers(new File(['xlsx'], 'ledger.xlsx'))
      let settled = false
      const outcome = pending.then(value => { settled = true; return value }, error => { settled = true; return error as ApiError })
      await vi.advanceTimersByTimeAsync(15_000)
      expect(settled).toBe(false)
      await vi.advanceTimersByTimeAsync(105_000)
      expect(await outcome).toMatchObject({ code: 'IMPORT_STATUS_UNKNOWN', message: expect.stringContaining('请先刷新并按台账编号核对') })
    } finally { vi.useRealTimers() }
  })
  it('Excel 导出返回二进制文件并传递筛选', async () => {
    const bytes = new Uint8Array([80, 75, 3, 4])
    const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(bytes, { status: 200 }))
    const result = await exportLedgers({ keyword: '机房', direction: 'IN' })
    expect(result.size).toBe(4)
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('keyword=%E6%9C%BA%E6%88%BF&direction=IN')
  })
})
