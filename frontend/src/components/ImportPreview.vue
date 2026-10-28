<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { previewLedgerImport, confirmLedgerImport } from '../api/ledger'
import { ApiError } from '../api/http'
import type { ImportPreviewResult, ImportResult, LedgerInput } from '../types/ledger'
import { fieldLabels } from '../types/history'
const props = defineProps<{ file: File }>()
const emit = defineEmits<{ cancel: []; imported: [result: ImportResult]; busy: [value: boolean] }>()
const preview = ref<ImportPreviewResult | null>(null)
const loading = ref(false)
const confirming = ref(false)
const error = ref('')
const stale = ref(false)
const unknownResult = ref(false)
const label = (key: keyof LedgerInput) => fieldLabels.find(field => field.key === key)?.label ?? key
async function recalculate() {
  if (loading.value || confirming.value) return
  loading.value = true; error.value = ''; preview.value = null; stale.value = false
  try { preview.value = await previewLedgerImport(props.file); unknownResult.value = false }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '预览失败' }
  finally { loading.value = false }
}
async function confirm() {
  if (!preview.value || loading.value || confirming.value || stale.value || unknownResult.value) return
  confirming.value = true; emit('busy', true); error.value = ''
  try { emit('imported', await confirmLedgerImport(props.file, preview.value.previewFingerprint)) }
  catch (reason) {
    stale.value = reason instanceof ApiError && reason.code === 'IMPORT_PREVIEW_STALE'
    unknownResult.value = reason instanceof ApiError && reason.code === 'IMPORT_STATUS_UNKNOWN'
    error.value = stale.value ? '共享台账已变化，预览已过期，请重新预览并核对。' : reason instanceof Error ? reason.message : '导入失败'
  } finally { confirming.value = false; emit('busy', false) }
}
defineExpose({ confirming })
onMounted(recalculate)
</script>
<template>
  <section class="import-preview" aria-label="Excel 导入预览">
    <h3>{{ file.name }}</h3>
    <p v-if="loading" role="status">正在计算预览，尚未写入台账…</p>
    <p v-if="error" class="list-error" role="alert">{{ error }}</p>
    <template v-if="preview">
      <p>共 {{ preview.importedCount }} 条：新增 {{ preview.createdCount }} 条，实际覆盖 {{ preview.updatedCount }} 条，无变化 {{ preview.unchangedCount }} 条。</p>
      <p v-if="preview.missingOptionalColumns.length" class="preview-warning">缺少可选列：{{ preview.missingOptionalColumns.join('、') }}。覆盖行的这些旧值将清空。</p>
      <div class="preview-rows"><article v-for="row in preview.rows" :key="row.line"><strong>第 {{ row.line }} 行 · {{ row.ledgerCode }} · {{ row.action === 'CREATE' ? '新增' : row.action === 'UPDATE' ? '实际覆盖' : '无变化' }}</strong><p>变化：{{ row.changedFields.map(label).join('、') || '无业务字段变化' }}</p><p v-if="row.clearedFields.length" class="preview-warning">清空：{{ row.clearedFields.map(label).join('、') }}</p></article></div>
    </template>
    <div class="preview-actions"><el-button :disabled="confirming" @click="emit('cancel')">取消</el-button><el-button v-if="!preview || stale || unknownResult" :loading="loading" :disabled="confirming" @click="recalculate">重新预览</el-button><el-button v-if="preview && !stale && !unknownResult" type="primary" :loading="confirming" :disabled="loading || confirming" @click="confirm">确认导入</el-button></div>
  </section>
</template>
