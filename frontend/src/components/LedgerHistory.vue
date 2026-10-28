<script setup lang="ts">
import { ref, watch } from 'vue'
import { listLedgerHistory } from '../api/ledger'
import { formatLedgerTimestamp } from '../types/date'
import type { LedgerHistoryPage } from '../types/ledger'
import { changedFields } from '../types/history'
import LedgerDetail from './LedgerDetail.vue'

const props = defineProps<{ ledgerId: string }>()
const page = ref(1)
const loading = ref(false)
const error = ref('')
const selectedId = ref<string | null>(null)
const history = ref<LedgerHistoryPage>({ records: [], total: 0, page: 1, pageSize: 10 })
let sequence = 0
const actionLabels = { CREATE: '创建台账', UPDATE: '编辑修改', DELETE: '删除台账' }

async function load() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await listLedgerHistory(props.ledgerId, page.value, 10)
    if (current === sequence) {
      history.value = result
      selectedId.value = null
    }
  } catch (reason) {
    if (current === sequence) error.value = reason instanceof Error ? reason.message : '读取历史失败'
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => props.ledgerId, () => { page.value = 1; selectedId.value = null; void load() }, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="history-view">
    <p class="history-intro">台账 ID {{ ledgerId }} 的历史记录。点击一条记录查看当时的完整信息。</p>
    <div v-if="error" class="list-error" role="alert">{{ error }} <el-button link type="primary" @click="load">重试</el-button></div>
    <el-empty v-else-if="!loading && !history.records.length" description="暂无历史记录" />
    <el-timeline v-else>
      <el-timeline-item v-for="entry in history.records" :key="entry.id" :timestamp="formatLedgerTimestamp(entry.occurredAt)" placement="top">
        <div class="history-card">
          <button class="history-entry" type="button" :aria-expanded="selectedId === entry.id" @click="selectedId = selectedId === entry.id ? null : entry.id">
            <span class="history-entry-main"><strong>{{ actionLabels[entry.action] }}</strong><span>{{ entry.after?.ledgerCode ?? entry.before?.ledgerCode ?? `台账 ${entry.ledgerId}` }}</span></span>
            <span class="history-entry-meta">{{ formatLedgerTimestamp(entry.occurredAt) }} · 共享用户 · 记录 {{ entry.id }}</span>
            <span class="history-entry-open">{{ selectedId === entry.id ? '收起' : '查看详情' }}</span>
          </button>
          <p v-if="entry.before && entry.after" class="history-change-summary">变化：{{ changedFields(entry.before, entry.after).map(field => `${field.label}：${field.before} → ${field.after}`).join('；') || '无业务字段变化' }}</p>
          <div v-if="selectedId === entry.id" class="history-snapshot">
            <div v-if="entry.before"><h4>{{ entry.action === 'DELETE' ? '删除前' : '修改前' }}</h4><LedgerDetail :ledger="entry.before" /></div>
            <div v-if="entry.after"><h4>{{ entry.action === 'CREATE' ? '创建时' : '修改后' }}</h4><LedgerDetail :ledger="entry.after" /></div>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-pagination v-if="history.total > 10" v-model:current-page="page" :page-size="10" :total="history.total" layout="prev, pager, next" @current-change="load" />
  </div>
</template>
