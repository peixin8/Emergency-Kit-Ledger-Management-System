// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import MapCanvas from './MapCanvas.vue'
import type { LedgerMapPoint } from '../types/ledger'

const state = vi.hoisted(() => ({
  coordinates: [] as number[][],
  markerClicks: [] as (() => void)[],
  tooltips: [] as (string | HTMLElement)[],
  clearCount: 0,
  activeMarkerCount: 0,
  views: [] as { coordinates: number[]; zoom: number }[],
  invalidated: 0,
  mapClick: null as ((event: { latlng: { lat: number; lng: number } }) => void) | null
}))
vi.mock('leaflet', () => ({
  map: () => ({
    setView(coordinates: number[], zoom: number) { state.views.push({ coordinates, zoom }); return this }, on(_name: string, callback: (event: { latlng: { lat: number; lng: number } }) => void) { state.mapClick = callback; return this },
    invalidateSize() { state.invalidated++ }, panTo() {}, remove() {}
  }),
  tileLayer: () => ({ on() { return this }, addTo() { return this } }),
  layerGroup: () => ({ addTo() { return this }, clearLayers() { state.clearCount++; state.activeMarkerCount = 0 } }),
  circleMarker: (coordinates: number[]) => {
    state.coordinates.push(coordinates)
    return { bindTooltip(content: string | HTMLElement) { state.tooltips.push(content); return this }, on(_name: string, callback: () => void) { state.markerClicks.push(callback); return this }, addTo() { state.activeMarkerCount++; return this } }
  }
}))

beforeEach(() => { state.coordinates.length = 0; state.markerClicks.length = 0; state.tooltips.length = 0; state.views.length = 0; state.invalidated = 0; state.clearCount = 0; state.activeMarkerCount = 0; state.mapClick = null })

it('每台已定位设备生成一个精确标记，标记点击传出原始字符串 ID', async () => {
  const points: LedgerMapPoint[] = [
    { id: '9007199254740993', ledgerCode: 'XM-001', destinationAddress: '厦门市思明区', deviceCondition: 'GOOD', latitude: '24.4798345', longitude: '118.0894251' },
    { id: '2', ledgerCode: 'XM-002', destinationAddress: '厦门市湖里区', deviceCondition: 'FAULT', latitude: '24.5150000', longitude: '118.1470000' }
  ]
  const wrapper = mount(MapCanvas, { props: { points } })
  expect(state.coordinates).toEqual([[24.4798345, 118.0894251], [24.515, 118.147]])
  expect(state.activeMarkerCount).toBe(2)
  state.markerClicks[0]!()
  state.markerClicks[1]!()
  expect(wrapper.emitted('open')?.[0]).toEqual(['9007199254740993'])
  expect(wrapper.emitted('open')?.[1]).toEqual(['2'])
  wrapper.unmount()
})

it('点位数据更新时清除旧图层，只保留新的设备标记', async () => {
  const oldPoint: LedgerMapPoint = { id: '1', ledgerCode: 'XM-001', destinationAddress: '厦门市思明区', deviceCondition: 'GOOD', latitude: '24.4798345', longitude: '118.0894251' }
  const newPoint: LedgerMapPoint = { id: '9007199254740993', ledgerCode: 'XM-002', destinationAddress: '厦门市湖里区', deviceCondition: 'FAULT', latitude: '24.5150000', longitude: '118.1470000' }
  const wrapper = mount(MapCanvas, { props: { points: [oldPoint] } })
  expect(state.activeMarkerCount).toBe(1)
  const initialClearCount = state.clearCount

  await wrapper.setProps({ points: [newPoint] })
  expect(state.clearCount).toBe(initialClearCount + 1)
  expect(state.activeMarkerCount).toBe(1)
  expect(state.coordinates[state.coordinates.length - 1]).toEqual([24.515, 118.147])
  state.markerClicks[state.markerClicks.length - 1]!()
  expect(wrapper.emitted('open')?.[0]).toEqual(['9007199254740993'])
  wrapper.unmount()
})

it('选点保留七位小数字符串而非地图浮点值', () => {
  const wrapper = mount(MapCanvas, { props: { pickable: true } })
  state.mapClick?.({ latlng: { lat: 24.479834512, lng: 118.089425145 } })
  expect(wrapper.emitted('pick')?.[0]).toEqual(['24.4798345', '118.0894251'])
  wrapper.unmount()
})

it('地图提示将台账文字作为纯文本，不能插入用户提供的 HTML', () => {
  const malicious = '<img src=x onerror=alert(1)>'
  const wrapper = mount(MapCanvas, { props: { points: [
    { id: '1', ledgerCode: malicious, destinationAddress: '厦门市思明区', deviceCondition: 'GOOD', latitude: '24.4798345', longitude: '118.0894251' }
  ] } })
  expect(state.tooltips[0]).toBeInstanceOf(HTMLElement)
  const tooltip = state.tooltips[0] as HTMLElement
  expect(tooltip.textContent).toContain(malicious)
  expect(tooltip.querySelector('img')).toBeNull()
  wrapper.unmount()
})

it('选择台账点位时自动把地图放大到设备级位置', async () => {
  const wrapper = mount(MapCanvas, { props: { selected: null } })
  await wrapper.setProps({ selected: { latitude: '24.4798345', longitude: '118.0894251' } })
  expect(state.views[state.views.length - 1]).toEqual({ coordinates: [24.4798345, 118.0894251], zoom: 17 })
  wrapper.unmount()
})
