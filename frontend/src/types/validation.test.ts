import { describe, expect, it } from 'vitest'
import { addressesDiffer, validCoordinates, validPower, validVlan } from './validation'

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
  it('设备坐标必须成对、符合 WGS84 范围和七位小数精度', () => {
    expect(validCoordinates(null, null)).toBe(true)
    expect(validCoordinates('24.4798345', '118.0894251')).toBe(true)
    expect(validCoordinates('24.4798345', null)).toBe(false)
    expect(validCoordinates(null, '118.0894251')).toBe(false)
    expect(validCoordinates('91', '118')).toBe(false)
    expect(validCoordinates('24', '181')).toBe(false)
    expect(validCoordinates('24.12345678', '118')).toBe(false)
    expect(validCoordinates('NaN', '118')).toBe(false)
  })
})
