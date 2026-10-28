<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listLedgerMapPoints } from '../api/ledger'
import { conditionLabels } from '../types/ledger'
import type { LedgerMapPoints } from '../types/ledger'
import type { Coordinates } from '../types/location'
import MapCanvas from './MapCanvas.vue'

const emit = defineEmits<{ open: [id: string] }>()
defineProps<{ selected?: Coordinates | null }>()
const points = ref<LedgerMapPoints>({ records: [], unlocatedCount: 0 })
const loading = ref(false)
const error = ref('')
let sequence = 0
async function reload() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await listLedgerMapPoints()
    if (current === sequence) points.value = result
  } catch (reason) {
    if (current === sequence) error.value = reason instanceof Error ? reason.message : '点位加载失败'
  } finally {
    if (current === sequence) loading.value = false
  }
}
defineExpose({ reload })
onMounted(reload)
</script>

<template>
  <section class="location-panel" aria-label="厦门设备地图">
    <div class="location-heading">
      <div><h2>厦门设备地图</h2><p>每个圆点对应一条已选定目标地址坐标的台账。点击圆点或旁边的记录查看完整信息。</p></div>
      <div class="location-counts"><span>已定位 {{ points.records.length }} 台</span><span>待定位 {{ points.unlocatedCount }} 台</span><button type="button" :disabled="loading" @click="reload">刷新点位</button></div>
    </div>
    <div v-if="error" class="list-error" role="alert">{{ error }} <button type="button" @click="reload">重试</button></div>
    <div class="location-body">
      <MapCanvas :points="points.records" :selected="selected" @open="emit('open', $event)" />
      <div class="location-list">
        <p v-if="loading" role="status">正在加载点位…</p>
        <p v-else-if="!points.records.length" class="location-empty">暂无已定位设备。可在台账详情中编辑并选取目标地址位置。</p>
        <ul v-else>
          <li v-for="point in points.records" :key="point.id">
            <button type="button" class="map-point-link" @click="emit('open', point.id)">
              <strong>{{ point.ledgerCode }}</strong><span>{{ point.destinationAddress }}</span><small>{{ conditionLabels[point.deviceCondition] }} · ID {{ point.id }}</small>
            </button>
          </li>
        </ul>
      </div>
    </div>
    <p class="location-footnote">未定位台账不会生成推测点位；请打开对应台账补录坐标。</p>
  </section>
</template>
