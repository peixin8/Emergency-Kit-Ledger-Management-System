<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { LedgerMapPoint } from '../types/ledger'

const props = withDefaults(defineProps<{
  points?: LedgerMapPoint[]
  selected?: { latitude: string; longitude: string } | null
  pickable?: boolean
}>(), { points: () => [], selected: null, pickable: false })
const emit = defineEmits<{ open: [id: string]; pick: [latitude: string, longitude: string] }>()
const target = ref<HTMLElement | null>(null)
const tileError = ref(false)
let map: L.Map | null = null
let markers: L.LayerGroup | null = null

function drawMarkers() {
  if (!map || !markers) return
  markers.clearLayers()
  for (const point of props.points) {
    const latitude = Number(point.latitude)
    const longitude = Number(point.longitude)
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) continue
    const tooltip = document.createElement('span')
    tooltip.textContent = `${point.ledgerCode} · ${point.destinationAddress}`
    L.circleMarker([latitude, longitude], { radius: 8, color: '#185fbe', fillColor: '#3183e8', fillOpacity: 0.85, weight: 2 })
      .bindTooltip(tooltip)
      .on('click', () => emit('open', point.id))
      .addTo(markers)
  }
  if (props.selected) {
    const latitude = Number(props.selected.latitude)
    const longitude = Number(props.selected.longitude)
    if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
      L.circleMarker([latitude, longitude], { radius: 10, color: '#b74718', fillColor: '#f07542', fillOpacity: 0.85, weight: 2 }).addTo(markers)
      map.panTo([latitude, longitude])
    }
  }
}

onMounted(() => {
  if (!target.value) return
  map = L.map(target.value, { zoomControl: true }).setView([24.4798, 118.0894], 11)
  const tileUrl = import.meta.env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
  const attribution = import.meta.env.VITE_MAP_TILE_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  L.tileLayer(tileUrl, { maxZoom: 19, attribution }).on('tileerror', () => { tileError.value = true }).on('tileload', () => { tileError.value = false }).addTo(map)
  markers = L.layerGroup().addTo(map)
  if (props.pickable) map.on('click', event => emit('pick', event.latlng.lat.toFixed(7), event.latlng.lng.toFixed(7)))
  drawMarkers()
  setTimeout(() => map?.invalidateSize(), 0)
})
watch(() => [props.points, props.selected], drawMarkers)
onBeforeUnmount(() => { map?.remove(); map = null; markers = null })
</script>

<template>
  <div class="map-canvas-wrap">
    <div ref="target" class="map-canvas" :aria-label="pickable ? '厦门设备位置选点地图' : '厦门设备分布地图'" role="img" />
    <p v-if="tileError" class="map-tile-error" role="status">地图底图暂不可用；台账点位列表仍可使用。</p>
  </div>
</template>
