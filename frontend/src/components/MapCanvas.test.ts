// @vitest-environment jsdom
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi, type MockInstance } from 'vitest'
import * as L from 'leaflet'
import MapCanvas from './MapCanvas.vue'
import type { LedgerMapPoint } from '../types/ledger'

const wrappers: VueWrapper[] = []
const observers: { callback: ResizeObserverCallback; disconnect: ReturnType<typeof vi.fn> }[] = []
let width = 800
let height = 350
let firstViewVisibility: string | undefined
let fitSpy: MockInstance<L.Map['fitBounds']>

const localPoint: LedgerMapPoint = { id: '9007199254740993', ledgerCode: 'XM-001', destinationAddress: '厦门市思明区', deviceCondition: 'GOOD', latitude: '24.4798345', longitude: '118.0894251' }
const outsidePoint: LedgerMapPoint = { ...localPoint, id: '2', ledgerCode: 'FZ-001', destinationAddress: '福州市', latitude: '26.0745000', longitude: '119.2965000' }
function render(props: Partial<InstanceType<typeof MapCanvas>['$props']> = {}) {
  const wrapper = mount(MapCanvas, { props, attachTo: document.body })
  wrappers.push(wrapper)
  return wrapper
}
function mapInstance(): L.Map {
  const map = fitSpy.mock.contexts[0]
  if (!(map instanceof L.Map)) throw new Error('地图未按行政区域初始化')
  return map
}
function circleMarkers(map: L.Map) {
  const markers: L.CircleMarker[] = []
  map.eachLayer(layer => { if (layer instanceof L.CircleMarker) markers.push(layer) })
  return markers
}
function resize() { observers[0]!.callback([], {} as ResizeObserver) }

beforeEach(() => {
  width = 800; height = 350; firstViewVisibility = undefined; observers.length = 0
  Object.defineProperty(L.Browser, 'svg', { configurable: true, value: true })
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(() => width)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockImplementation(() => height)
  vi.stubGlobal('CSS', { supports: () => true })
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback: ResizeObserverCallback) { observers.push({ callback, disconnect: this.disconnect }) }
    observe() {}
    disconnect = vi.fn()
  })
  fitSpy = vi.spyOn(L.Map.prototype, 'fitBounds')
  const originalSetView = L.Map.prototype.setView
  vi.spyOn(L.Map.prototype, 'setView').mockImplementation(function (this: L.Map, ...args) {
    firstViewVisibility ??= this.getPane('mapPane')?.style.visibility
    return originalSetView.apply(this, args)
  })
})
afterEach(() => {
  for (const wrapper of wrappers.splice(0)) wrapper.unmount()
  vi.restoreAllMocks(); vi.unstubAllGlobals()
})

it('首帧先隐藏地理pane，全市初始化后按真实行政多边形裁剪并限制视野', () => {
  const wrapper = render()
  const map = mapInstance()
  const pane = map.getPane('mapPane')!
  expect(firstViewVisibility).toBe('hidden')
  expect(pane.style.clipPath).toMatch(/^path\(evenodd, "M/)
  expect(pane.style.visibility).toBe('visible')
  expect(map.getMinZoom()).toBeGreaterThan(1)
  expect(map.getZoom()).toBe(map.getMinZoom())
  expect(map.options.maxBoundsViscosity).toBe(1)
  const maxBounds = map.options.maxBounds
  if (!(maxBounds instanceof L.LatLngBounds)) throw new Error('未设置实际市域包围盒')
  expect(maxBounds.contains([24.4798345, 118.0894251])).toBe(true)
  expect(map.options).toMatchObject({ zoomAnimation: false, fadeAnimation: false, markerZoomAnimation: false, inertia: false, touchZoom: false })
  const tiles: L.TileLayer[] = []
  map.eachLayer(layer => { if (layer instanceof L.TileLayer) tiles.push(layer) })
  expect(tiles[0]?.options.noWrap).toBe(true)
  expect(wrapper.get('[role="img"]').attributes('aria-label')).toBe('福建省·厦门市设备分布地图')
})

it('已有或异步选中厦门坐标保持设备级17，点位刷新和抽屉resize不重置焦点', async () => {
  const selected = { latitude: localPoint.latitude, longitude: localPoint.longitude }
  const wrapper = render({ selected })
  const map = mapInstance()
  expect(map.getZoom()).toBe(17)
  expect(map.getCenter().lat).toBeCloseTo(24.4798345, 6)
  map.panTo([24.49, 118.1], { animate: false })
  const center = map.getCenter()
  await wrapper.setProps({ points: [localPoint], selected: { ...selected } })
  expect(map.getCenter()).toEqual(center)
  width = 480; height = 260; resize()
  expect(map.getZoom()).toBe(17)
  expect(map.getCenter().lat).toBeCloseTo(center.lat, 6)
  await wrapper.setProps({ selected: { latitude: '24.5150000', longitude: '118.1470000' } })
  expect(map.getZoom()).toBe(17)
  expect(map.getCenter().lat).toBeCloseTo(24.515, 6)
  await wrapper.setProps({ selected: null })
  expect(circleMarkers(map)).toHaveLength(1)
  expect(map.getZoom()).toBe(17)
})

it('外市记录数据保留，但不绘制、不飞出厦门并提示显示范围', async () => {
  const selected = { latitude: outsidePoint.latitude, longitude: outsidePoint.longitude }
  const points = [localPoint, outsidePoint]
  const wrapper = render({ selected, points })
  const map = mapInstance()
  expect(circleMarkers(map)).toHaveLength(1)
  expect(map.getZoom()).toBe(map.getMinZoom())
  expect(map.getCenter().lat).toBeLessThan(25)
  expect(wrapper.text()).toContain('超出厦门显示范围')
  expect(wrapper.props('points')).toEqual(points)
  expect(wrapper.props('selected')).toEqual(selected)
  await wrapper.setProps({ selected: { latitude: '24.5933146', longitude: '117.8821850' } })
  expect(circleMarkers(map)).toHaveLength(2)
  expect(map.getZoom()).toBe(17)
  expect(wrapper.text()).not.toContain('超出厦门显示范围')
})

it.each([
  ['最西', '24.5933146', '117.8821850'],
  ['最东', '24.5286118', '118.4494442'],
  ['最南', '24.3822222', '118.1316667'],
  ['最北', '24.9072638', '118.0123764']
])('%s边界点在17级及窄屏resize后距容器边缘仍留有标记半径', async (_name, latitude, longitude) => {
  const wrapper = render({ selected: { latitude, longitude } })
  const map = mapInstance()
  function expectMarkerInsideContainer() {
    const marker = circleMarkers(map)[0]!
    const pixel = map.latLngToContainerPoint(marker.getLatLng())
    const size = map.getSize()
    expect(map.getZoom()).toBe(17)
    expect(pixel.x).toBeGreaterThanOrEqual(12)
    expect(pixel.y).toBeGreaterThanOrEqual(12)
    expect(size.x - pixel.x).toBeGreaterThanOrEqual(12)
    expect(size.y - pixel.y).toBeGreaterThanOrEqual(12)
  }
  expectMarkerInsideContainer()
  width = 320; height = 260; resize()
  expectMarkerInsideContainer()
  expect(wrapper.props('selected')).toEqual({ latitude, longitude })
})

it('返回厦门全市只恢复视野，保留选点、不冒泡且不传出pick/open', async () => {
  const selected = { latitude: localPoint.latitude, longitude: localPoint.longitude }
  const wrapper = render({ selected, pickable: true })
  const map = mapInstance()
  const bubblingClick = vi.fn()
  wrapper.element.addEventListener('click', bubblingClick)
  const button = wrapper.get('button.map-return')
  expect(button.attributes('type')).toBe('button')
  await button.trigger('click')
  expect(map.getZoom()).toBe(map.getMinZoom())
  expect(circleMarkers(map)).toHaveLength(1)
  expect(wrapper.props('selected')).toEqual(selected)
  expect(wrapper.emitted('pick')).toBeUndefined()
  expect(wrapper.emitted('open')).toBeUndefined()
  expect(bubblingClick).not.toHaveBeenCalled()
  await wrapper.setProps({ points: [] })
  expect(map.getZoom()).toBe(map.getMinZoom())
})

it('区域外地图点击不选点，域内与边界选点仍保留七位小数字符串', () => {
  const wrapper = render({ pickable: true })
  const map = mapInstance()
  map.fire('click', { latlng: L.latLng(26.0745, 119.2965) })
  map.fire('click', { latlng: L.latLng(24.4, 117.9) })
  expect(wrapper.emitted('pick')).toBeUndefined()
  map.fire('click', { latlng: L.latLng(24.479834512, 118.089425145) })
  map.fire('click', { latlng: L.latLng(24.5933146, 117.882185) })
  expect(wrapper.emitted('pick')).toEqual([['24.4798345', '118.0894251'], ['24.5933146', '117.8821850']])
})

it('区域内设备标记保留原始字符串ID，刷新清理旧标记，工具提示使用纯文本', async () => {
  const malicious = '<img src=x onerror=alert(1)>'
  const wrapper = render({ points: [{ ...localPoint, ledgerCode: malicious }] })
  const map = mapInstance()
  const marker = circleMarkers(map)[0]!
  marker.fire('click')
  expect(wrapper.emitted('open')?.[0]).toEqual(['9007199254740993'])
  const content = marker.getTooltip()?.getContent()
  expect(content).toBeInstanceOf(HTMLElement)
  if (!(content instanceof HTMLElement)) throw new Error('提示不是安全文本节点')
  expect(content.textContent).toContain(malicious)
  expect(content.querySelector('img')).toBeNull()
  await wrapper.setProps({ points: [{ ...localPoint, id: '3', latitude: '24.5150000', longitude: '118.1470000' }] })
  expect(circleMarkers(map)).toHaveLength(1)
  expect(circleMarkers(map)[0]!.getLatLng()).toEqual(L.latLng(24.515, 118.147))
})

it('move/zoom当帧同步重投影裁剪路径，宽窄屏resize重算全市minZoom', () => {
  render()
  const map = mapInstance()
  const pane = map.getPane('mapPane')!
  const initial = pane.style.clipPath
  map.setView([24.5, 118.1], 13, { animate: false })
  expect(pane.style.clipPath).not.toBe(initial)
  const zoomed = pane.style.clipPath
  map.panTo([24.51, 118.11], { animate: false })
  // 平移时地理pane及其裁剪一起平移，保持同一地理形状。
  expect(pane.style.visibility).toBe('visible')
  expect(pane.style.clipPath).toBeTruthy()
  expect(zoomed).toMatch(/^path/)
  width = 320; height = 260; resize()
  expect(pane.style.visibility).toBe('visible')
  expect(map.getZoom()).toBeGreaterThanOrEqual(map.getMinZoom())
})

it('全市模式resize重新适配，零尺寸挂载恢复后仍能显示市域', async () => {
  width = 0; height = 0
  const wrapper = render()
  expect(wrapper.get('.map-canvas').element.querySelector<HTMLElement>('.leaflet-map-pane')?.style.visibility).toBe('hidden')
  width = 800; height = 350; resize()
  const map = mapInstance()
  const initialMinimum = map.getMinZoom()
  expect(map.getPane('mapPane')!.style.visibility).toBe('visible')
  width = 320; height = 260; resize()
  expect(map.getZoom()).toBe(map.getMinZoom())
  expect(map.getMinZoom()).toBeLessThanOrEqual(initialMinimum)
  await wrapper.vm.$nextTick()
  expect(wrapper.text()).not.toContain('厦门区域地图暂不可用')
})

it('投影异常时安全隐藏地图，恢复后才允许选点', async () => {
  const wrapper = render({ pickable: true })
  const map = mapInstance()
  const projection = vi.spyOn(map, 'latLngToLayerPoint').mockImplementation(() => L.point(NaN, 0))
  map.fire('move')
  expect(map.getPane('mapPane')!.style.visibility).toBe('hidden')
  map.fire('click', { latlng: L.latLng(24.4798345, 118.0894251) })
  expect(wrapper.emitted('pick')).toBeUndefined()
  await wrapper.vm.$nextTick()
  expect(wrapper.text()).toContain('厦门区域地图暂不可用')
  projection.mockRestore()
  map.fire('move')
  expect(map.getPane('mapPane')!.style.visibility).toBe('visible')
})

it('不支持裁剪时安全隐藏地理pane并提示，禁止地图选点', async () => {
  vi.stubGlobal('CSS', { supports: () => false })
  const wrapper = render({ pickable: true })
  expect(wrapper.get('.map-canvas').element.querySelector<HTMLElement>('.leaflet-map-pane')?.style.visibility).toBe('hidden')
  await wrapper.vm.$nextTick()
  expect(wrapper.text()).toContain('厦门区域地图暂不可用')
  expect(wrapper.emitted('pick')).toBeUndefined()
})

it('卸载断开ResizeObserver并移除地图监听和实例', () => {
  const wrapper = render()
  const map = mapInstance()
  const offSpy = vi.spyOn(map, 'off')
  const removeSpy = vi.spyOn(map, 'remove')
  wrapper.unmount()
  expect(observers[0]?.disconnect).toHaveBeenCalledOnce()
  expect(offSpy).toHaveBeenCalled()
  expect(removeSpy).toHaveBeenCalledOnce()
})
