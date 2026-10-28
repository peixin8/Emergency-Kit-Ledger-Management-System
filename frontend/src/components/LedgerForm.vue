<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { LedgerInput, Ledger } from '../types/ledger'
import { conditionLabels, directionLabels, emptyLedger, methodLabels } from '../types/ledger'
import { addressesDiffer, validPower, validVlan } from '../types/validation'
import { validCoordinates } from '../types/validation'
import { searchAddress, type AddressCandidate } from '../api/ledger'
import { ApiError } from '../api/http'
import MapCanvas from './MapCanvas.vue'

const props = defineProps<{ modelValue?: Ledger | null; submitting?: boolean }>()
const emit = defineEmits<{ save: [value: LedgerInput] }>()
const formRef = ref<FormInstance>()
const form = reactive<LedgerInput>(emptyLedger())
const positionAddress = ref('')
const candidates = ref<AddressCandidate[]>([])
const searchingAddress = ref(false)
const searchMessage = ref('')
let searchRevision = 0
let searchedAddress = ''
function clearSearch() {
  searchRevision++
  candidates.value = []
  searchingAddress.value = false
  searchMessage.value = ''
  searchedAddress = ''
}
watch(() => props.modelValue, value => { clearSearch(); Object.assign(form, emptyLedger(), value ?? {}); positionAddress.value = form.destinationAddress }, { immediate: true })
watch(() => form.destinationAddress, clearSearch)
const isEdit = computed(() => !!props.modelValue)
const selectedPosition = computed(() => {
  const latitude = normalizeCoordinate(form.latitude)
  const longitude = normalizeCoordinate(form.longitude)
  return latitude !== null && longitude !== null && validCoordinates(latitude, longitude) ? { latitude, longitude } : null
})
const locationNeedsReview = computed(() => selectedPosition.value !== null && addressesDiffer(positionAddress.value, form.destinationAddress))
function normalizeCoordinate(value: string | null) { return value === null || value.trim() === '' ? null : value.trim() }
function pickPosition(latitude: string, longitude: string) { form.latitude = latitude; form.longitude = longitude; positionAddress.value = form.destinationAddress }
async function findAddress() {
  const address = form.destinationAddress.trim()
  clearSearch()
  if (address.length < 2 || address.length > 255) {
    searchMessage.value = '请先输入 2–255 个字的目标地址。'
    return
  }
  const revision = searchRevision
  searchedAddress = address
  searchingAddress.value = true
  try {
    const result = await searchAddress(address)
    if (revision !== searchRevision || address !== form.destinationAddress.trim()) return
    candidates.value = result.results
    if (!result.results.length) searchMessage.value = '未找到匹配地址，请补充区、街道或门牌号后重试，也可在地图手动选点。'
  } catch (error) {
    if (revision !== searchRevision) return
    if (error instanceof ApiError && error.code === 'GEOCODE_NOT_CONFIGURED') searchMessage.value = '地址搜索需在后台配置高德 Web 服务 Key；目前可继续在地图手动选点。'
    else if (error instanceof ApiError && error.code === 'GEOCODE_INVALID_KEY') searchMessage.value = '地址搜索失败，请检查高德 Web 服务 Key 是否有效；也可在地图手动选点。'
    else if (error instanceof ApiError && error.code === 'GEOCODE_ACCOUNT_ISSUE') searchMessage.value = '地址搜索失败，请联系管理员检查高德账号状态或配额；也可在地图手动选点。'
    else if (error instanceof ApiError && error.code === 'GEOCODE_BUSY') searchMessage.value = '地址搜索繁忙，请稍后重试。'
    else if (error instanceof ApiError && error.code === 'GEOCODE_UNAVAILABLE') searchMessage.value = '地址搜索服务暂不可用，请稍后重试或在地图手动选点。'
    else searchMessage.value = error instanceof Error ? error.message : '地址搜索失败，请稍后重试。'
  } finally {
    if (revision === searchRevision) searchingAddress.value = false
  }
}
function selectCandidate(candidate: AddressCandidate) {
  if (searchedAddress !== form.destinationAddress.trim()) return
  pickPosition(candidate.latitude, candidate.longitude)
  candidates.value = []
  searchMessage.value = '已选中候选点位，请在地图核对实际位置；如有偏差，可点击地图微调。'
}
const rules: FormRules<LedgerInput> = {
  ledgerCode: [{ required: true, message: '请输入台账编号', trigger: 'blur' }],
  originalAddress: [{ required: true, message: '请输入原始地址', trigger: 'blur' }],
  destinationAddress: [{ required: true, message: '请输入目标地址', trigger: 'blur' }],
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
  input.ptnPort = input.ptnPort?.trim() || null
  input.deviceConditionDetail = input.deviceConditionDetail?.trim() || null
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
    <p class="form-location-hint">输入目标地址后点击搜索，选择候选位置并在地图核对；也可在厦门地图上点击实际位置或手动填写 WGS84 经纬度。未选点的台账会显示为待定位。</p>
    <div class="address-search-actions"><button type="button" class="address-search-button" :disabled="searchingAddress" @click="findAddress">{{ searchingAddress ? '搜索中…' : '搜索地址' }}</button><span>仅点击按钮时，将目标地址发送至高德地图服务查询。</span></div>
    <p v-if="searchMessage" class="address-search-message" role="status">{{ searchMessage }}</p>
    <ul v-if="candidates.length" class="address-candidates" aria-label="地址搜索候选位置"><li v-for="(candidate, index) in candidates" :key="`${candidate.latitude}-${candidate.longitude}-${index}`"><button type="button" class="address-candidate" @click="selectCandidate(candidate)"><strong>{{ candidate.displayName }}</strong><small>{{ candidate.latitude }}, {{ candidate.longitude }}</small></button></li></ul>
    <MapCanvas pickable :selected="selectedPosition" @pick="pickPosition" />
    <div class="form-grid location-coordinate-fields">
      <el-form-item label="纬度" prop="latitude" class="latitude-input"><el-input v-model.trim="form.latitude" inputmode="decimal" placeholder="例如 24.4798345" @change="positionAddress = form.destinationAddress" /></el-form-item>
      <el-form-item label="经度" prop="longitude" class="longitude-input"><el-input v-model.trim="form.longitude" inputmode="decimal" placeholder="例如 118.0894251" @change="positionAddress = form.destinationAddress" /></el-form-item>
    </div>
    <p v-if="locationNeedsReview" class="location-review-note" role="status">目标地址已变化，原有坐标需要复核；请在地图重新选点或更新经纬度。</p>
    <div class="form-section-title"><span>03</span> 设备与传输信息</div>
    <div class="form-grid">
      <el-form-item label="应急宝设备状况" prop="deviceCondition"><el-select v-model="form.deviceCondition" style="width:100%"><el-option v-for="(label, value) in conditionLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="设备状况说明" prop="deviceConditionDetail"><el-input v-model.trim="form.deviceConditionDetail" placeholder="故障或维修情况，选填" maxlength="255" /></el-form-item>
      <el-form-item label="设备序列号" prop="deviceSerialNo"><el-input v-model.trim="form.deviceSerialNo" placeholder="选填" maxlength="120" /></el-form-item>
      <el-form-item label="接入机房" prop="accessRoom"><el-input v-model.trim="form.accessRoom" placeholder="机房名称或位置" maxlength="120" /></el-form-item>
      <el-form-item label="PTN 端口（网元+槽口）" prop="ptnPort"><el-input v-model.trim="form.ptnPort" placeholder="网元和槽口，选填" maxlength="255" /></el-form-item>
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
