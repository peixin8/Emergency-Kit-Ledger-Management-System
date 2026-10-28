import * as L from 'leaflet'
import { containsCoordinate, regionBounds, regionPath, type Region } from './region'

export interface RegionViewport {
  showAll: () => void
  focus: (latitude: number, longitude: number) => void
  ready: () => boolean
  destroy: () => void
}

// 地理pane包含瓦片、标记和提示；控件在pane外，保持缩放与来源署名可用。
export function createRegionViewport(map: L.Map, region: Region | null, unavailable: (failed: boolean) => void): RegionViewport | null {
  const pane = map.getPane('mapPane')
  if (pane) pane.style.visibility = 'hidden'
  const coordinates = regionBounds(region)
  if (!pane || !coordinates || !globalThis.CSS?.supports('clip-path', 'path(evenodd, "M0 0L1 0L1 1Z")')) {
    unavailable(true)
    return null
  }
  const bounds = L.latLngBounds(coordinates)
  let overview = true
  let changingView = false
  let destroyed = false
  const hide = () => { pane.style.visibility = 'hidden' }
  const updateClip = () => {
    hide()
    if (destroyed || map.getZoom() === undefined || map.getSize().x <= 0 || map.getSize().y <= 0) return
    try {
      const path = regionPath(region, (latitude, longitude) => map.latLngToLayerPoint([latitude, longitude]))
      if (!path) throw new Error('区域投影不可用')
      pane.style.clipPath = `path(evenodd, "${path}")`
      if (!pane.style.clipPath) throw new Error('区域裁剪不可用')
      pane.style.visibility = 'visible'
      unavailable(false)
    } catch {
      unavailable(true)
    }
  }
  const userMoved = () => { if (!changingView) overview = false }
  const showAll = () => {
    if (destroyed) return
    changingView = true
    try {
      overview = true
      map.fitBounds(bounds, { padding: [20, 20], animate: false })
      updateClip()
    } finally { changingView = false }
  }
  const resize = () => {
    if (destroyed) return
    changingView = true
    try {
      const center = map.getZoom() === undefined ? null : map.getCenter()
      const zoom = map.getZoom()
      map.invalidateSize({ animate: false, pan: true })
      const size = map.getSize()
      if (size.x <= 40 || size.y <= 40) {
        hide()
        // 让零尺寸挂载的实例具备视图，之后invalidateSize才能刷新尺寸缓存。
        if (!center) map.setView(bounds.getCenter(), 10, { animate: false })
        hide()
        return
      }
      const minimum = Math.max(0, Math.min(19, map.getBoundsZoom(bounds, false, L.point(40, 40))))
      map.setMinZoom(minimum)
      if (overview || !center) showAll()
      else map.setView(center, Math.max(zoom, minimum), { animate: false })
      updateClip()
    } finally { changingView = false }
  }
  map.on('viewprereset', hide)
  map.on('move zoom viewreset resize', updateClip)
  map.on('movestart zoomstart', userMoved)
  // 交互视窗允许少量留白，避免边界点聚焦时被maxBounds推到容器边缘。
  // 裁剪、选点以及全市fit/minZoom始终使用原行政边界，不扩大可见地域。
  map.setMaxBounds(bounds.pad(0.05))
  resize()
  const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize)
  observer?.observe(map.getContainer())
  if (!observer) window.addEventListener('resize', resize)
  return {
    showAll,
    focus(latitude, longitude) {
      if (destroyed || !containsCoordinate(region, latitude, longitude)) return
      changingView = true
      try {
        overview = false
        map.setView([latitude, longitude], 17, { animate: false })
        updateClip()
      } finally { changingView = false }
    },
    ready: () => !destroyed && pane.style.visibility === 'visible',
    destroy() {
      destroyed = true
      hide()
      observer?.disconnect()
      window.removeEventListener('resize', resize)
      map.off('viewprereset', hide)
      map.off('move zoom viewreset resize', updateClip)
      map.off('movestart zoomstart', userMoved)
    }
  }
}
