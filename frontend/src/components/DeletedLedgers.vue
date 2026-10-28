<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { listDeletedLedgers } from '../api/ledger'
import type { DeletedLedgerPage } from '../types/ledger'
import { formatLedgerTimestamp } from '../types/date'
const emit = defineEmits<{ history: [id: string] }>()
const filter = reactive({ keyword: '', deletedFrom: '', deletedTo: '', page: 1 })
const data = ref<DeletedLedgerPage>({ records: [], total: 0, page: 1, pageSize: 20 })
const loading = ref(false)
const error = ref('')
let revision = 0
let controller: AbortController | null = null
async function load() {
  if (filter.deletedFrom && filter.deletedTo && filter.deletedFrom > filter.deletedTo) { error.value = '删除开始日期不能晚于结束日期'; return }
  const current = ++revision; controller?.abort(); controller = new AbortController(); loading.value = true; error.value = ''
  try { const result = await listDeletedLedgers({ ...filter, pageSize: 20, keyword: filter.keyword.trim() || undefined, deletedFrom: filter.deletedFrom || undefined, deletedTo: filter.deletedTo || undefined }, controller.signal); if (current === revision) data.value = result }
  catch (reason) { if (current === revision) error.value = reason instanceof Error ? reason.message : '读取已删除记录失败' }
  finally { if (current === revision) loading.value = false }
}
function search() { filter.page = 1; void load() }
onMounted(load)
onBeforeUnmount(() => { revision++; controller?.abort() })
</script>
<template>
  <section class="data-panel deleted-ledgers" aria-label="已删除台账检索">
    <h2>已删除记录</h2><p>按编号、原始地址、目标地址或删除日期查找，点击历史查看删除前信息。</p>
    <div class="filter-row"><el-input v-model="filter.keyword" maxlength="100" clearable placeholder="搜索已删除编号或地址" @keyup.enter="search" /><el-date-picker v-model="filter.deletedFrom" type="date" value-format="YYYY-MM-DD" placeholder="删除开始日期" /><el-date-picker v-model="filter.deletedTo" type="date" value-format="YYYY-MM-DD" placeholder="删除结束日期" /><el-button @click="search">查询</el-button><el-button :disabled="loading" @click="load">刷新</el-button></div>
    <div v-if="error" role="alert" class="list-error">{{ error }}</div>
    <el-table v-loading="loading" :data="data.records" empty-text="没有符合条件的已删除台账"><el-table-column prop="ledgerCode" label="台账编号" /><el-table-column prop="originalAddress" label="原始地址" /><el-table-column prop="destinationAddress" label="目标地址" /><el-table-column label="删除时间"><template #default="{ row }">{{ formatLedgerTimestamp(row.deletedAt) }}</template></el-table-column><el-table-column label="操作"><template #default="{ row }"><el-button link type="primary" @click="emit('history', row.id)">查看历史</el-button></template></el-table-column></el-table>
    <el-pagination v-model:current-page="filter.page" :total="data.total" :page-size="20" layout="total, prev, pager, next" @current-change="load" />
  </section>
</template>
