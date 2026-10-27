import { describe, expect, it } from 'vitest'
import { addressesDiffer, validPower, validVlan } from './validation'

describe('台账输入校验', () => {
  it('禁止只差空格的相同地址', () => {
    expect(addressesDiffer(' A 机房 ', 'A 机房')).toBe(false)
    expect(addressesDiffer('A 机房', 'B 机房')).toBe(true)
  })
  it('功耗保持字符串精度且不能为负或超过两位小数', () => {
    expect(validPower('0.00')).toBe(true)
    expect(validPower('120.50')).toBe(true)
    expect(validPower('9999999999.99')).toBe(true)
    expect(validPower('10000000000')).toBe(false)
    expect(validPower('-1')).toBe(false)
    expect(validPower('1.001')).toBe(false)
  })
  it('VLAN 仅接受 1 至 4094 的整数', () => {
    expect(validVlan(null)).toBe(true)
    expect(validVlan(1)).toBe(true)
    expect(validVlan(4094)).toBe(true)
    expect(validVlan(0)).toBe(false)
    expect(validVlan(4095)).toBe(false)
    expect(validVlan(1.5)).toBe(false)
  })
})
