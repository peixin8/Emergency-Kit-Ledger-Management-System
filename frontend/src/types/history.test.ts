import { describe, expect, it } from 'vitest'
import { emptyLedger, type Ledger } from './ledger'
import { changedFields } from './history'

const snapshot = (patch: Partial<Ledger>): Ledger => ({
  ...emptyLedger(), id: '1', organizationId: '1', version: '0', createdAt: '', updatedAt: '', ...patch
})

describe('修改历史', () => {
  it('只列出业务字段差异，不把更新时间和版本号当成修改内容', () => {
    const before = snapshot({ originalAddress: 'A', version: '0', updatedAt: '2026-10-26T00:00:00Z' })
    const after = snapshot({ originalAddress: 'B', version: '1', updatedAt: '2026-10-27T00:00:00Z' })
    expect(changedFields(before, after)).toEqual([{ label: '原始地址', before: 'A', after: 'B' }])
  })
})

it('历史摘要涵盖 PTN、状况说明和坐标，空值形式不制造差异', () => {
  const before = snapshot({ ptnPort: null, deviceConditionDetail: null, latitude: null, longitude: null, remarks: '' })
  const after = snapshot({ ptnPort: '网元1', deviceConditionDetail: '故障', latitude: '24.1', longitude: '118.1', remarks: null as unknown as string })
  expect(changedFields(before, after).map(field => field.label)).toEqual(['设备状况说明', 'PTN 端口', '纬度', '经度'])
})
