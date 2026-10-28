<script setup lang="ts">
import { computed } from 'vue'
import type { Ledger } from '../types/ledger'
import { conditionLabels, directionLabels, methodLabels } from '../types/ledger'
import { formatLedgerTimestamp } from '../types/date'
import { amapNavigationUrl, ledgerCoordinates } from '../types/location'
import MapCanvas from './MapCanvas.vue'

const props = withDefaults(defineProps<{ ledger: Ledger; showLocation?: boolean }>(), { showLocation: false })
const coordinates = computed(() => ledgerCoordinates(props.ledger))
const navigationUrl = computed(() => coordinates.value ? amapNavigationUrl(coordinates.value, props.ledger.destinationAddress) : null)
const fields: { title: string; items: { label: string; key: keyof Ledger }[] }[] = [
  { title: '流转申请', items: [{ label: '台账编号', key: 'ledgerCode' }, { label: '申请日期', key: 'applicationDate' }, { label: '原始地址', key: 'originalAddress' }, { label: '目标地址', key: 'destinationAddress' }, { label: '申请联系人', key: 'applicantContact' }, { label: '联系电话', key: 'applicantPhone' }] },
  { title: '目标地址点位', items: [{ label: '纬度', key: 'latitude' }, { label: '经度', key: 'longitude' }] },
  { title: '设备信息', items: [{ label: '应急宝设备状况', key: 'deviceCondition' }, { label: '设备状况说明', key: 'deviceConditionDetail' }, { label: '设备序列号', key: 'deviceSerialNo' }, { label: '资产归属', key: 'assetOwner' }, { label: '接入机房', key: 'accessRoom' }, { label: 'PTN 端口（网元+槽口）', key: 'ptnPort' }, { label: '纤芯', key: 'fiberCore' }, { label: '制式', key: 'networkStandard' }, { label: 'BBU 型号', key: 'bbuModel' }, { label: 'RRU 型号', key: 'rruModel' }, { label: 'BBU–RRU 功耗', key: 'bbuRruPowerWatts' }] },
  { title: '网络参数', items: [{ label: '网管 IP', key: 'managementIp' }, { label: '网管 VLAN', key: 'managementVlan' }, { label: '业务 IP', key: 'businessIp' }, { label: '业务 VLAN', key: 'businessVlan' }] },
  { title: '技术记录信息', items: [{ label: 'ID', key: 'id' }, { label: '组织 ID', key: 'organizationId' }, { label: '版本', key: 'version' }, { label: '创建时间', key: 'createdAt' }, { label: '更新时间', key: 'updatedAt' }] }
]
function display(value: unknown) { return value === null || value === undefined || value === '' ? '—' : String(value) }
function displayField(ledger: Ledger, key: keyof Ledger) {
  if (key === 'createdAt' || key === 'updatedAt') return formatLedgerTimestamp(ledger[key])
  if (key === 'deviceCondition') return conditionLabels[ledger.deviceCondition]
  return display(ledger[key])
}
</script>

<template>
  <div class="detail-head"><div><div class="eyebrow">LEDGER DETAIL</div><h3>{{ ledger.ledgerCode }}</h3><span class="detail-id">记录 ID · {{ ledger.id }}</span></div><el-tag effect="plain" :type="ledger.deviceCondition === 'GOOD' ? 'success' : ledger.deviceCondition === 'FAULT' ? 'danger' : 'warning'">{{ conditionLabels[ledger.deviceCondition] }}</el-tag></div>
  <section v-if="showLocation" class="detail-location" aria-label="目标地址定位">
    <div class="detail-location-heading"><div><h4>目标地址定位</h4><p>{{ ledger.destinationAddress }}</p></div><a v-if="navigationUrl" class="detail-navigation" :href="navigationUrl" target="_blank" rel="noopener noreferrer">高德导航 ↗</a></div>
    <MapCanvas v-if="coordinates" :key="ledger.id" :selected="coordinates" />
    <p v-else class="detail-location-empty">尚未定位，请编辑选择目标地址点位</p>
  </section>
  <div class="detail-summary"><div><small>流转方向</small><strong>{{ directionLabels[ledger.direction] }}</strong></div><div><small>申请方式</small><strong>{{ methodLabels[ledger.applicationMethod] }}</strong></div><div><small>申请日期</small><strong>{{ ledger.applicationDate }}</strong></div></div>
  <p class="detail-remarks"><strong>备注：</strong>{{ ledger.remarks || '—' }}</p><component :is="section.title === '技术记录信息' ? 'details' : 'section'" v-for="section in fields" :key="section.title" class="detail-section"><component :is="section.title === '技术记录信息' ? 'summary' : 'h4'">{{ section.title }}</component><div class="detail-grid"><div v-for="item in section.items" :key="item.key" class="detail-cell"><span>{{ item.label }}</span><strong>{{ displayField(ledger, item.key) }}{{ item.key === 'bbuRruPowerWatts' && ledger[item.key] ? ' W' : '' }}</strong></div></div></component>
</template>
