<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { LedgerInput, Ledger } from '../types/ledger'
import { conditionLabels, directionLabels, emptyLedger, methodLabels } from '../types/ledger'
import { addressesDiffer, validPower, validVlan } from '../types/validation'

const props = defineProps<{ modelValue?: Ledger | null; submitting?: boolean }>()
const emit = defineEmits<{ save: [value: LedgerInput] }>()
const formRef = ref<FormInstance>()
const form = reactive<LedgerInput>(emptyLedger())
watch(() => props.modelValue, value => Object.assign(form, emptyLedger(), value ?? {}), { immediate: true })
const isEdit = computed(() => !!props.modelValue)
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
  businessVlan: [{ validator: (_rule, value, callback) => validVlan(value) ? callback() : callback(new Error('VLAN 范围为 1–4094')), trigger: 'blur' }]
}
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const input = { ...form }
  if (input.bbuRruPowerWatts === '') input.bbuRruPowerWatts = null
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
      <el-form-item label="目标地址" prop="destinationAddress"><el-input v-model.trim="form.destinationAddress" placeholder="设备计划到达地址" maxlength="255" /></el-form-item>
      <el-form-item label="进出申请方式" prop="applicationMethod"><el-select v-model="form.applicationMethod" style="width:100%"><el-option v-for="(label, value) in methodLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="申请日期" prop="applicationDate"><el-date-picker v-model="form.applicationDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width:100%" /></el-form-item>
      <el-form-item label="申请联系人" prop="applicantContact"><el-input v-model.trim="form.applicantContact" placeholder="联系人姓名" maxlength="100" /></el-form-item>
      <el-form-item label="联系电话" prop="applicantPhone"><el-input v-model.trim="form.applicantPhone" placeholder="选填" maxlength="40" /></el-form-item>
    </div>
    <div class="form-section-title"><span>02</span> 设备与传输信息</div>
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
    <div class="form-section-title"><span>03</span> 网络参数</div>
    <div class="form-grid">
      <el-form-item label="网管 IP" prop="managementIp"><el-input v-model.trim="form.managementIp" placeholder="IPv4 或 IPv6" maxlength="45" /></el-form-item>
      <el-form-item label="网管 VLAN" prop="managementVlan"><el-input-number v-model="form.managementVlan" :min="1" :max="4094" :controls="false" placeholder="1–4094" style="width:100%" /></el-form-item>
      <el-form-item label="业务 IP" prop="businessIp"><el-input v-model.trim="form.businessIp" placeholder="IPv4 或 IPv6" maxlength="45" /></el-form-item>
      <el-form-item label="业务 VLAN" prop="businessVlan"><el-input-number v-model="form.businessVlan" :min="1" :max="4094" :controls="false" placeholder="1–4094" style="width:100%" /></el-form-item>
    </div>
    <div class="form-section-title"><span>04</span> 其他说明</div>
    <el-form-item label="备注" prop="remarks"><el-input v-model="form.remarks" type="textarea" :rows="3" placeholder="补充本次申请的特殊说明" maxlength="2000" show-word-limit /></el-form-item>
    <p v-if="isEdit" class="form-note">修改记录将保留审计记录；若其他人已更新此记录，系统会提示重新加载。</p>
  </el-form>
</template>
