<script setup lang="ts">
import { ref, watch } from 'vue'
import { listLedgerHistory } from '../api/ledger'
import { changedFields } from '../types/history'
import { formatLedgerTimestamp } from '../types/date'
import type { LedgerHistoryPage } from '../types/ledger'
import LedgerDetail from './LedgerDetail.vue'

const props = defineProps<{ ledgerId: string }>()
const page = ref(1)
const loading = ref(false)
const error = ref('')
const history = ref<LedgerHistoryPage>({ records: [], total: 0, page: 1, pageSize: 10 })
let sequence = 0
const actionLabels = { CREATE: '创建台账', UPDATE: '编辑修改', DELETE: '删除台账' }

async function load() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await listLedgerHistory(props.ledgerId, page.value, 10)
    if (current === sequence) history.value = result
  } catch (reason) {
    if (current === sequence) error.value = reason instanceof Error ? reason.message : '读取历史失败'
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(() => props.ledgerId, () => { page.value = 1; void load() }, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="history-view">
    <p class="history-intro">台账 ID {{ ledgerId }} 的创建、编辑和删除留痕。操作人统一记录为共享用户。</p>
    <div v-if="error" class="list-error" role="alert">{{ error }} <el-button link type="primary" @click="load">重试</el-button></div>
    <el-empty v-else-if="!loading && !history.records.length" description="暂无修改历史" />
    <el-timeline v-else>
      <el-timeline-item v-for="entry in history.records" :key="entry.id" :timestamp="formatLedgerTimestamp(entry.occurredAt)" placement="top">
        <div class="history-card">
          <div class="history-head"><strong>{{ actionLabels[entry.action] }}</strong><span>共享用户 · 记录 {{ entry.id }}</span></div>
          <div v-if="entry.action === 'UPDATE' && entry.before && entry.after" class="history-changes">
            <div v-for="change in changedFields(entry.before, entry.after)" :key="change.label"><span>{{ change.label }}</span><del>{{ change.before }}</del><span>→</span><strong>{{ change.after }}</strong></div>
            <p v-if="!changedFields(entry.before, entry.after).length">业务字段没有变化。</p>
          </div>
          <el-collapse>
            <el-collapse-item title="查看完整快照" :name="entry.id">
              <div v-if="entry.before"><h4>修改前</h4><LedgerDetail :ledger="entry.before" /></div>
              <div v-if="entry.after"><h4>修改后</h4><LedgerDetail :ledger="entry.after" /></div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-pagination v-if="history.total > 10" v-model:current-page="page" :page-size="10" :total="history.total" layout="prev, pager, next" @current-change="load" />
  </div>
</template>
