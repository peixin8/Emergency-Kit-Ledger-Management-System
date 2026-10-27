<script setup lang="ts">
import type { Ledger } from '../types/ledger'
import { conditionLabels, directionLabels, methodLabels } from '../types/ledger'
import { formatLedgerTimestamp } from '../types/date'
defineProps<{ ledger: Ledger }>()
const fields: { title: string; items: { label: string; key: keyof Ledger }[] }[] = [
  { title: '流转申请', items: [{ label: '台账编号', key: 'ledgerCode' }, { label: '申请日期', key: 'applicationDate' }, { label: '原始地址', key: 'originalAddress' }, { label: '目标地址', key: 'destinationAddress' }, { label: '申请联系人', key: 'applicantContact' }, { label: '联系电话', key: 'applicantPhone' }] },
  { title: '设备信息', items: [{ label: '设备序列号', key: 'deviceSerialNo' }, { label: '资产归属', key: 'assetOwner' }, { label: '接入机房', key: 'accessRoom' }, { label: '纤芯', key: 'fiberCore' }, { label: '制式', key: 'networkStandard' }, { label: 'BBU 型号', key: 'bbuModel' }, { label: 'RRU 型号', key: 'rruModel' }, { label: 'BBU–RRU 功耗', key: 'bbuRruPowerWatts' }] },
  { title: '网络参数', items: [{ label: '网管 IP', key: 'managementIp' }, { label: '网管 VLAN', key: 'managementVlan' }, { label: '业务 IP', key: 'businessIp' }, { label: '业务 VLAN', key: 'businessVlan' }] },
  { title: '记录信息', items: [{ label: 'ID', key: 'id' }, { label: '组织 ID', key: 'organizationId' }, { label: '版本', key: 'version' }, { label: '创建时间', key: 'createdAt' }, { label: '更新时间', key: 'updatedAt' }, { label: '备注', key: 'remarks' }] }
]
function display(value: unknown) { return value === null || value === undefined || value === '' ? '—' : String(value) }
function displayField(ledger: Ledger, key: keyof Ledger) {
  if (key === 'createdAt' || key === 'updatedAt') return formatLedgerTimestamp(ledger[key])
  return display(ledger[key])
}
</script>

<template>
  <div class="detail-head"><div><div class="eyebrow">LEDGER DETAIL</div><h3>{{ ledger.ledgerCode }}</h3><span class="detail-id">记录 ID · {{ ledger.id }}</span></div><el-tag effect="plain" :type="ledger.deviceCondition === 'GOOD' ? 'success' : ledger.deviceCondition === 'FAULT' ? 'danger' : 'warning'">{{ conditionLabels[ledger.deviceCondition] }}</el-tag></div>
  <div class="detail-summary"><div><small>流转方向</small><strong>{{ directionLabels[ledger.direction] }}</strong></div><div><small>申请方式</small><strong>{{ methodLabels[ledger.applicationMethod] }}</strong></div><div><small>申请日期</small><strong>{{ ledger.applicationDate }}</strong></div></div>
  <section v-for="section in fields" :key="section.title" class="detail-section"><h4>{{ section.title }}</h4><div class="detail-grid"><div v-for="item in section.items" :key="item.key" class="detail-cell"><span>{{ item.label }}</span><strong>{{ displayField(ledger, item.key) }}{{ item.key === 'bbuRruPowerWatts' && ledger[item.key] ? ' W' : '' }}</strong></div></div></section>
</template>
