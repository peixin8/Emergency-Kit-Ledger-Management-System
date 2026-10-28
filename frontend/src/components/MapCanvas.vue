<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { LedgerMapPoint } from '../types/ledger'
import { ledgerCoordinates } from '../types/location'
import { containsCoordinate, xiamenRegion } from '../maps/region'
import { createRegionViewport, type RegionViewport } from '../maps/regionViewport'

const props = withDefaults(defineProps<{
  points?: LedgerMapPoint[]
  selected?: { latitude: string; longitude: string } | null
  pickable?: boolean
}>(), { points: () => [], selected: null, pickable: false })
const emit = defineEmits<{ open: [id: string]; pick: [latitude: string, longitude: string] }>()
const target = ref<HTMLElement | null>(null)
const tileError = ref(false)
const regionError = ref(false)
const selectedCoordinates = computed(() => ledgerCoordinates(props.selected))
const outsideRegion = computed(() => selectedCoordinates.value !== null && !containsCoordinate(xiamenRegion, Number(selectedCoordinates.value.latitude), Number(selectedCoordinates.value.longitude)))
let map: L.Map | null = null
let markers: L.LayerGroup | null = null
let viewport: RegionViewport | null = null

function drawMarkers() {
  if (!map || !markers) return
  markers.clearLayers()
  for (const point of props.points) {
    const latitude = Number(point.latitude)
    const longitude = Number(point.longitude)
    if (!containsCoordinate(xiamenRegion, latitude, longitude)) continue
    const tooltip = document.createElement('span')
    tooltip.textContent = `${point.ledgerCode} · ${point.destinationAddress}`
    L.circleMarker([latitude, longitude], { radius: 8, color: '#185fbe', fillColor: '#3183e8', fillOpacity: 0.85, weight: 2 })
      .bindTooltip(tooltip)
      .on('click', () => emit('open', point.id))
      .addTo(markers)
  }
  if (selectedCoordinates.value) {
    const selected = selectedCoordinates.value
    if (containsCoordinate(xiamenRegion, Number(selected.latitude), Number(selected.longitude))) {
      const latitude = Number(selected.latitude)
      const longitude = Number(selected.longitude)
      L.circleMarker([latitude, longitude], { radius: 10, color: '#b74718', fillColor: '#f07542', fillOpacity: 0.85, weight: 2 }).addTo(markers)
    }
  }
}

function focusSelected() {
  const selected = selectedCoordinates.value
  if (selected) viewport?.focus(Number(selected.latitude), Number(selected.longitude))
}
function returnToXiamen() { viewport?.showAll() }

onMounted(() => {
  if (!target.value) return
  map = L.map(target.value, { zoomControl: true, maxZoom: 19, maxBoundsViscosity: 1, zoomAnimation: false, fadeAnimation: false, markerZoomAnimation: false, inertia: false, touchZoom: false, trackResize: false })
  viewport = createRegionViewport(map, xiamenRegion, failed => { regionError.value = failed })
  if (!viewport) return
  const tileUrl = import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
  const attribution = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  L.tileLayer(tileUrl, { maxZoom: 19, noWrap: true, attribution }).on('tileerror', () => { tileError.value = true }).on('tileload', () => { tileError.value = false }).addTo(map)
  map.attributionControl.addAttribution('厦门边界 © <a href="https://www.openstreetmap.org/relation/3242930">OpenStreetMap</a> · <a href="https://opendatacommons.org/licenses/odbl/1-0/">ODbL</a>')
  markers = L.layerGroup().addTo(map)
  map.on('click', event => {
    if (props.pickable && viewport?.ready() && containsCoordinate(xiamenRegion, event.latlng.lat, event.latlng.lng)) emit('pick', event.latlng.lat.toFixed(7), event.latlng.lng.toFixed(7))
  })
  drawMarkers()
  focusSelected()
})
watch(() => [props.points, props.selected], drawMarkers)
watch([() => props.selected?.latitude, () => props.selected?.longitude], focusSelected)
onBeforeUnmount(() => { viewport?.destroy(); viewport = null; map?.remove(); map = null; markers = null })
</script>

<template>
  <div class="map-canvas-wrap">
    <div ref="target" class="map-canvas" :aria-label="pickable ? '福建省·厦门市设备位置选点地图' : '福建省·厦门市设备分布地图'" role="img" />
    <button type="button" class="map-return" :disabled="regionError" @click.stop="returnToXiamen">返回厦门全市</button>
    <p v-if="regionError" class="map-tile-error" role="status">厦门区域地图暂不可用；台账数据仍可查看。</p>
    <p v-else-if="outsideRegion" class="map-tile-error" role="status">超出厦门显示范围；原有坐标仍保留。</p>
    <p v-else-if="tileError" class="map-tile-error" role="status">地图底图暂不可用；台账点位列表仍可使用。</p>
  </div>
</template>

<style scoped>
.map-canvas { background: #e9f0f4; }
.map-canvas :deep(.leaflet-map-pane) { visibility: hidden; }
.map-return { position: absolute; top: 10px; right: 10px; z-index: 800; padding: 7px 10px; border: 1px solid #b6c5d5; border-radius: 4px; background: #fff; color: #244a70; font-size: 12px; cursor: pointer; box-shadow: 0 1px 4px #18375920; }
.map-return:focus-visible { outline: 2px solid #2467b7; outline-offset: 2px; }
.map-return:disabled { cursor: default; color: #7b8896; }
</style>
