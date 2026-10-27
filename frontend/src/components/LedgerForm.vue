<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { LedgerInput, Ledger } from '../types/ledger'
import { conditionLabels, directionLabels, emptyLedger, methodLabels } from '../types/ledger'
import { addressesDiffer, validPower, validVlan } from '../types/validation'
import { validCoordinates } from '../types/validation'
import MapCanvas from './MapCanvas.vue'

const props = defineProps<{ modelValue?: Ledger | null; submitting?: boolean }>()
const emit = defineEmits<{ save: [value: LedgerInput] }>()
const formRef = ref<FormInstance>()
const form = reactive<LedgerInput>(emptyLedger())
const positionAddress = ref('')
watch(() => props.modelValue, value => { Object.assign(form, emptyLedger(), value ?? {}); positionAddress.value = form.destinationAddress }, { immediate: true })
const isEdit = computed(() => !!props.modelValue)
const selectedPosition = computed(() => {
  const latitude = normalizeCoordinate(form.latitude)
  const longitude = normalizeCoordinate(form.longitude)
  return latitude !== null && longitude !== null && validCoordinates(latitude, longitude) ? { latitude, longitude } : null
})
const locationNeedsReview = computed(() => selectedPosition.value !== null && addressesDiffer(positionAddress.value, form.destinationAddress))
function normalizeCoordinate(value: string | null) { return value === null || value.trim() === '' ? null : value.trim() }
function pickPosition(latitude: string, longitude: string) { form.latitude = latitude; form.longitude = longitude; positionAddress.value = form.destinationAddress }
const rules: FormRules<LedgerInput> = {
  ledgerCode: [{ required: true, message: '请输入台账编号', trigger: 'blur' }],
  originalAddress: [{ required: true, message: '请输入原始地址', trigger: 'blur' }],
  destinationAddress: [{ required: true, message: '请输入目标地址', trigger: 'blur' }, { validator: (_rule, value, callback) => value && !addressesDiffer(form.originalAddress, value) ? callback(new Error('目标地址不能与原始地址相同')) : callback(), trigger: 'blur' }],
  applicationMethod: [{ required: true, message: '请选择申请方式', trigger: 'change' }],
  applicantContact: [{ required: true, message: '请输入申请联系人', trigger: 'blur' }],
  applicationDate: [{ required: true, message: '请选择申请日期', trigger: 'change' }],
  deviceCondition: [{ required: true, message: '请选择设备状况', trigger: 'change' }],
  direction: [{ required: true, message: '请选择流转方向', trigger: 'change' }],
  bbuRruPowerWatts: [{ validator: (_rule, value, callback) => validPower(value) ? callback() : callback(new Error('请输入不小于 0、最多两位小数的功耗')), trigger: 'blur' }],
  managementVlan: [{ validator: (_rule, value, callback) => validVlan(value) ? callback() : callback(new Error('VLAN 范围为 1–4094')), trigger: 'blur' }],
  businessVlan: [{ validator: (_rule, value, callback) => validVlan(value) ? callback() : callback(new Error('VLAN 范围为 1–4094')), trigger: 'blur' }],
  latitude: [{ validator: (_rule, _value, callback) => validCoordinates(normalizeCoordinate(form.latitude), normalizeCoordinate(form.longitude)) ? callback() : callback(new Error('纬度和经度需成对填写，且在有效范围内，最多七位小数')), trigger: 'blur' }],
  longitude: [{ validator: (_rule, _value, callback) => validCoordinates(normalizeCoordinate(form.latitude), normalizeCoordinate(form.longitude)) ? callback() : callback(new Error('纬度和经度需成对填写，且在有效范围内，最多七位小数')), trigger: 'blur' }]
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const input = { ...form }
  if (input.bbuRruPowerWatts === '') input.bbuRruPowerWatts = null
  input.latitude = normalizeCoordinate(input.latitude)
  input.longitude = normalizeCoordinate(input.longitude)
  if (!validCoordinates(input.latitude, input.longitude)) return
  emit('save', input)
}
defineExpose({ submit })
</script>

<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="ledger-form" @submit.prevent="submit">
    <div class="form-section-title"><span>01</span> 申请与流转信息</div>
    <div class="form-grid">
      <el-form-item label="台账编号" prop="ledgerCode"><el-input v-model.trim="form.ledgerCode" placeholder="如 YJB-2026-001" maxlength="64" /></el-form-item>
      <el-form-item label="流转方向" prop="direction"><el-select v-model="form.direction" style="width:100%"><el-option v-for="(label, value) in directionLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="原始地址" prop="originalAddress"><el-input v-model.trim="form.originalAddress" placeholder="设备当前所在地址" maxlength="255" /></el-form-item>
      <el-form-item label="目标地址" prop="destinationAddress" class="destination-address"><el-input v-model.trim="form.destinationAddress" placeholder="设备计划到达地址" maxlength="255" /></el-form-item>
      <el-form-item label="进出申请方式" prop="applicationMethod"><el-select v-model="form.applicationMethod" style="width:100%"><el-option v-for="(label, value) in methodLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="申请日期" prop="applicationDate"><el-date-picker v-model="form.applicationDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></el-form-item>
      <el-form-item label="申请联系人" prop="applicantContact"><el-input v-model.trim="form.applicantContact" placeholder="联系人姓名" maxlength="100" /></el-form-item>
      <el-form-item label="联系电话" prop="applicantPhone"><el-input v-model.trim="form.applicantPhone" placeholder="选填" maxlength="40" /></el-form-item>
    </div>
    <div class="form-section-title"><span>02</span> 目标地址点位</div>
    <p class="form-location-hint">请在厦门地图上点击设备目标地址的实际位置，也可手动填写 WGS84 经纬度。未选点的台账会显示为待定位。</p>
    <MapCanvas pickable :selected="selectedPosition" @pick="pickPosition" />
    <div class="form-grid location-coordinate-fields">
      <el-form-item label="纬度" prop="latitude" class="latitude-input"><el-input v-model.trim="form.latitude" inputmode="decimal" placeholder="例如 24.4798345" @change="positionAddress = form.destinationAddress" /></el-form-item>
      <el-form-item label="经度" prop="longitude" class="longitude-input"><el-input v-model.trim="form.longitude" inputmode="decimal" placeholder="例如 118.0894251" @change="positionAddress = form.destinationAddress" /></el-form-item>
    </div>
    <p v-if="locationNeedsReview" class="location-review-note" role="status">目标地址已变化，原有坐标需要复核；请在地图重新选点或更新经纬度。</p>
    <div class="form-section-title"><span>03</span> 设备与传输信息</div>
    <div class="form-grid">
      <el-form-item label="应急宝设备状况" prop="deviceCondition"><el-select v-model="form.deviceCondition" style="width:100%"><el-option v-for="(label, value) in conditionLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="设备序列号" prop="deviceSerialNo"><el-input v-model.trim="form.deviceSerialNo" placeholder="选填" maxlength="120" /></el-form-item>
      <el-form-item label="接入机房" prop="accessRoom"><el-input v-model.trim="form.accessRoom" placeholder="机房名称或位置" maxlength="120" /></el-form-item>
      <el-form-item label="纤芯" prop="fiberCore"><el-input v-model.trim="form.fiberCore" placeholder="纤芯编号或描述" maxlength="100" /></el-form-item>
      <el-form-item label="制式" prop="networkStandard"><el-input v-model.trim="form.networkStandard" placeholder="如 4G / 5G" maxlength="100" /></el-form-item>
      <el-form-item label="资产归属" prop="assetOwner"><el-input v-model.trim="form.assetOwner" placeholder="所属单位或责任方" maxlength="120" /></el-form-item>
      <el-form-item label="BBU 型号" prop="bbuModel"><el-input v-model.trim="form.bbuModel" placeholder="选填" maxlength="120" /></el-form-item>
      <el-form-item label="RRU 型号" prop="rruModel"><el-input v-model.trim="form.rruModel" placeholder="选填" maxlength="120" /></el-form-item>
      <el-form-item label="BBU–RRU 使用功耗（W）" prop="bbuRruPowerWatts"><el-input v-model.trim="form.bbuRruPowerWatts" placeholder="如 120.50" inputmode="decimal" /></el-form-item>
    </div>
    <div class="form-section-title"><span>04</span> 网络参数</div>
    <div class="form-grid">
      <el-form-item label="网管 IP" prop="managementIp"><el-input v-model.trim="form.managementIp" placeholder="IPv4 或 IPv6" maxlength="45" /></el-form-item>
      <el-form-item label="网管 VLAN" prop="managementVlan"><el-input-number v-model="form.managementVlan" :min="1" :max="4094" :controls="false" placeholder="1–4094" style="width:100%" /></el-form-item>
      <el-form-item label="业务 IP" prop="businessIp"><el-input v-model.trim="form.businessIp" placeholder="IPv4 或 IPv6" maxlength="45" /></el-form-item>
      <el-form-item label="业务 VLAN" prop="businessVlan"><el-input-number v-model="form.businessVlan" :min="1" :max="4094" :controls="false" placeholder="1–4094" style="width:100%" /></el-form-item>
    </div>
    <div class="form-section-title"><span>05</span> 其他说明</div>
    <el-form-item label="备注" prop="remarks"><el-input v-model="form.remarks" type="textarea" :rows="3" placeholder="补充本次申请的特殊说明" maxlength="2000" show-word-limit /></el-form-item>
    <p v-if="isEdit" class="form-note">修改记录将保留审计记录；若其他人已更新此记录，系统会提示重新加载。</p>
  </el-form>
</template>
