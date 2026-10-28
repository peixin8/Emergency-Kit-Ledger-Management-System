// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import LedgerDetail from './LedgerDetail.vue'
import MapCanvas from './MapCanvas.vue'
import type { Ledger } from '../types/ledger'

const ledger: Ledger = {
  id: '1', organizationId: '1', version: '1', ledgerCode: 'dsfg',
  originalAddress: '原址', destinationAddress: '厦门市思明区软件园二期 A 座', direction: 'IN',
  applicationMethod: 'ONLINE', applicantContact: '张三', applicantPhone: '13800000000',
  applicationDate: '2026-10-28', deviceCondition: 'GOOD', accessRoom: '机房 A',
  fiberCore: '1', networkStandard: '5G', bbuModel: 'BBU-A', rruModel: 'RRU-A',
  bbuRruPowerWatts: '100', managementIp: '10.0.0.1', managementVlan: 101,
  businessIp: '10.1.0.1', businessVlan: 201, deviceSerialNo: 'SN-001',
  assetOwner: '厦门', remarks: '备注', createdAt: '2026-10-28T08:00:00Z',
  updatedAt: '2026-10-28T08:00:00Z', latitude: '24.4798345', longitude: '118.0894251'
}

it('详情页顶部展示精确点位和可导航的高德链接，目标地址安全编码', () => {
  const record = { ...ledger, destinationAddress: '厦门 A 座&测试' }
  const wrapper = mount(LedgerDetail, { props: { ledger: record, showLocation: true }, global: { stubs: { MapCanvas: true, 'el-tag': true } } })
  const map = wrapper.findComponent(MapCanvas)
  expect(map.exists()).toBe(true)
  expect(map.props('selected')).toEqual({ latitude: record.latitude, longitude: record.longitude })
  const link = wrapper.find('a[href^="https://uri.amap.com/navigation"]')
  expect(link.exists()).toBe(true)
  const url = new URL(link.attributes('href')!)
  expect(url.searchParams.get('to')?.endsWith(',厦门 A 座&测试')).toBe(true)
  const [longitude, latitude] = url.searchParams.get('to')!.split(',').map(Number)
  expect(longitude).toBeGreaterThan(118.0894251)
  expect(latitude).toBeLessThan(24.4798345)
  expect(link.attributes('target')).toBe('_blank')
  expect(link.attributes('rel')).toContain('noopener')
  expect(wrapper.text()).toContain('网管 IP')
  wrapper.unmount()
})

it('没有坐标或坐标非法时提示补录，不展示地图和导航', () => {
  for (const coordinates of [{ latitude: null, longitude: null }, { latitude: '99', longitude: '181' }]) {
    const wrapper = mount(LedgerDetail, { props: { ledger: { ...ledger, ...coordinates }, showLocation: true }, global: { stubs: { MapCanvas: true, 'el-tag': true } } })
    expect(wrapper.text()).toContain('尚未定位，请编辑选择目标地址点位')
    expect(wrapper.findComponent(MapCanvas).exists()).toBe(false)
    expect(wrapper.find('a[href^="https://uri.amap.com/navigation"]').exists()).toBe(false)
    wrapper.unmount()
  }
})

it('历史快照默认不创建地图和导航入口', () => {
  const wrapper = mount(LedgerDetail, { props: { ledger }, global: { stubs: { MapCanvas: true, 'el-tag': true } } })
  expect(wrapper.findComponent(MapCanvas).exists()).toBe(false)
  expect(wrapper.find('a[href^="https://uri.amap.com/navigation"]').exists()).toBe(false)
  wrapper.unmount()
})
