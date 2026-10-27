import { describe, expect, it } from 'vitest'
import { formatLedgerTimestamp } from './date'

describe('台账时间显示', () => {
  it('带东八区偏移与微秒的时间显示为本地秒', () => {
    expect(formatLedgerTimestamp('2026-10-26T17:41:00.062138+08:00')).toBe('2026-10-26 17:41:00')
  })
  it('UTC 时间正确换算为上海时间，不依赖浏览器所在时区', () => {
    expect(formatLedgerTimestamp('2026-10-26T09:41:00Z')).toBe('2026-10-26 17:41:00')
  })
  it('空值与非法值不显示原始数据', () => {
    expect(formatLedgerTimestamp(null)).toBe('—')
    expect(formatLedgerTimestamp('invalid')).toBe('—')
  })
})
