<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CirclePlus, Delete, Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { createLedger, deleteLedger, exportLedgers, getLedger, importLedgers, listLedgers, updateLedger } from '../api/ledger'
import { ApiError } from '../api/http'
import LedgerForm from '../components/LedgerForm.vue'
import LedgerDetail from '../components/LedgerDetail.vue'
import LedgerHistory from '../components/LedgerHistory.vue'
import LedgerLocationMap from '../components/LedgerLocationMap.vue'
import type { Ledger, LedgerInput, LedgerPage } from '../types/ledger'
import { formatLedgerTimestamp } from '../types/date'
import { ledgerCoordinates } from '../types/location'

const loading = ref(false)
const listError = ref('')
const pageData = ref<LedgerPage>({ records: [], total: 0, page: 1, pageSize: 20 })
const filter = reactive<{ keyword: string; page: number; pageSize: number }>({ keyword: '', page: 1, pageSize: 20 })
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'detail'>('create')
const current = ref<Ledger | null>(null)
const drawerLoading = ref(false)
const submitting = ref(false)
const formRef = ref<InstanceType<typeof LedgerForm> | null>(null)
const locationMapRef = ref<InstanceType<typeof LedgerLocationMap> | null>(null)
const historyVisible = ref(false)
const historyId = ref('')
const historyLookupId = ref('')
const importing = ref(false)
const exporting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const drawerTitle = computed(() => drawerMode.value === 'create' ? '新增台账' : drawerMode.value === 'edit' ? '编辑台账' : '台账详情')
const selectedCoordinates = computed(() => drawerMode.value === 'detail' ? ledgerCoordinates(current.value) : null)
let activeRequest: AbortController | null = null
let requestSequence = 0
let detailSequence = 0

async function load() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  const sequence = ++requestSequence
  loading.value = true
  listError.value = ''
  try {
    const result = await listLedgers({ page: filter.page, pageSize: filter.pageSize, keyword: filter.keyword.trim() || undefined }, controller.signal)
    if (sequence === requestSequence) pageData.value = result
  } catch (error) {
    if (sequence === requestSequence) listError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function search() { filter.page = 1; void load() }
function reset() { filter.keyword = ''; filter.page = 1; void load() }
function openCreate() { detailSequence++; drawerLoading.value = false; current.value = null; drawerMode.value = 'create'; drawerVisible.value = true }
async function openRecord(id: string, mode: 'edit' | 'detail') {
  const sequence = ++detailSequence
  drawerMode.value = mode
  current.value = null
  drawerVisible.value = true
  drawerLoading.value = true
  try { const result = await getLedger(id); if (sequence === detailSequence) current.value = result }
  catch (error) { if (sequence === detailSequence) { ElMessage.error(error instanceof Error ? error.message : '读取失败'); drawerVisible.value = false } }
  finally { if (sequence === detailSequence) drawerLoading.value = false }
}
async function save(input: LedgerInput) {
  if (submitting.value) return
  submitting.value = true
  try {
    if (drawerMode.value === 'edit' && current.value) await updateLedger(current.value.id, input, current.value.version)
    else await createLedger(input)
    ElMessage.success(drawerMode.value === 'edit' ? '台账已更新' : '台账已创建')
    drawerVisible.value = false
    await load()
    await locationMapRef.value?.reload()
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) ElMessage.error('数据已变化或编号重复，请检查后刷新重试')
    else ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally { submitting.value = false }
}
async function remove(record: Ledger) {
  try { await ElMessageBox.confirm(`确认删除台账「${record.ledgerCode}」？此操作会保留审计记录。`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) }
  catch { return }
  try { await deleteLedger(record.id); detailSequence++; drawerVisible.value = false; current.value = null; ElMessage.success('台账已删除'); if (pageData.value.records.length === 1 && filter.page > 1) filter.page--; await load(); await locationMapRef.value?.reload() }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
function openHistory(id: string) { drawerVisible.value = false; historyId.value = id; historyVisible.value = true }
function lookupHistory() {
  const id = historyLookupId.value.trim()
  if (!/^\d+$/.test(id)) { ElMessage.warning('请输入有效的台账 ID'); return }
  openHistory(id)
}
async function downloadExcel() {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await exportLedgers({ keyword: filter.keyword.trim() || undefined })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `应急宝台账-${new Date().toLocaleDateString('sv-SE')}.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success('Excel 已下载')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导出失败') }
  finally { exporting.value = false }
}
async function uploadExcel(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  try {
    if (!/\.xlsx$/i.test(file.name)) throw new Error('请选择 .xlsx 文件')
    const result = await importLedgers(file)
    ElMessage.success(`成功导入 ${result.importedCount} 条台账：新增 ${result.createdCount} 条，覆盖 ${result.updatedCount} 条`)
    filter.page = 1
    await load()
    await locationMapRef.value?.reload()
  } catch (error) { ElMessage.error({ message: error instanceof Error ? error.message : '导入失败', duration: error instanceof ApiError && error.code === 'IMPORT_STATUS_UNKNOWN' ? 12000 : 8000, showClose: true }) }
  finally { importing.value = false; input.value = '' }
}
watch(() => filter.pageSize, () => { filter.page = 1; void load() })
onMounted(load)
</script>

<template>
  <div class="workspace">
    <div class="page-heading"><div><div class="eyebrow">EQUIPMENT LEDGER <span class="eyebrow-line"></span> 共享工作台</div><h1>设备进出台账</h1><p>多人共用同一份台账，点击编号查看完整信息与历史记录。</p></div></div>
    <div class="overview-strip"><div><span class="overview-icon">▤</span><span><small>当前结果</small><strong>{{ pageData.total }} <em>条记录</em></strong></span></div><div class="overview-note"><span class="status-dot"></span> 数据实时写入共享数据库</div></div>
    <LedgerLocationMap ref="locationMapRef" :selected="selectedCoordinates" @open="id => openRecord(id, 'detail')" />
    <section class="data-panel"><div class="panel-heading"><div><h2>台账记录</h2><p>按编号、位置及设备信息快速检索；点击编号查看详情</p></div><span class="panel-count">共 {{ pageData.total }} 条</span></div>
      <div class="filter-row"><el-input v-model="filter.keyword" class="search-input" clearable placeholder="搜索编号、地址或联系人" @keyup.enter="search" @clear="search"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button @click="reset"><el-icon><Refresh /></el-icon> 重置</el-button><el-button type="primary" plain @click="search"><el-icon><Search /></el-icon> 查询</el-button></div>
      <div class="file-row"><div class="file-actions"><el-button :loading="exporting" @click="downloadExcel"><el-icon><Download /></el-icon> 导出 Excel</el-button><el-button :loading="importing" @click="fileInput?.click()"><el-icon><Upload /></el-icon> 导入 Excel</el-button><el-button type="primary" plain @click="openCreate"><el-icon><CirclePlus /></el-icon> 手动新增</el-button><input ref="fileInput" type="file" accept=".xlsx" class="visually-hidden" aria-label="选择 Excel 文件" @change="uploadExcel" /><span>可导入现有台账或系统导出的 .xlsx 文件；Excel 中不同台账编号会生成不同系统 ID，同编号覆盖并保留原 ID，不支持在 Excel 指定系统 ID。覆盖会更新全部业务信息，空白值会清空旧值；旧版模板缺少的可选列也会清空旧值，建议先导出新版文件后编辑重导入。导入失败时会提示具体行号和列名，整份文件不会部分导入。</span></div><div class="history-lookup"><el-input v-model="historyLookupId" placeholder="已删除台账的 ID" aria-label="历史台账 ID" @keyup.enter="lookupHistory" /><el-button @click="lookupHistory">查询历史</el-button></div></div>
      <div v-if="listError" class="list-error" role="alert"><span>{{ listError }}</span><el-button link type="primary" @click="load">重试</el-button></div>
      <el-table v-loading="loading" :data="pageData.records" class="ledger-table" empty-text="暂无台账记录，可点击手动新增" height="min(58vh, 570px)" row-key="id">
        <el-table-column label="台账编号" min-width="165"><template #default="{ row }"><button class="record-link" @click="openRecord(row.id, 'detail')">{{ row.ledgerCode }} <el-icon><ArrowRight /></el-icon></button><small class="table-sub">ID {{ row.id }}</small></template></el-table-column>
        <el-table-column label="原始地址 → 目标地址" min-width="260"><template #default="{ row }"><div class="address-cell"><span>{{ row.originalAddress }}</span><el-icon><ArrowRight /></el-icon><span>{{ row.destinationAddress }}</span></div></template></el-table-column>
        <el-table-column label="更新时间" width="165"><template #default="{ row }">{{ formatLedgerTimestamp(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="145"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" :aria-label="`编辑台账 ${row.ledgerCode}`" @click="openRecord(row.id, 'edit')">编辑</el-button><el-button link type="danger" :aria-label="`删除台账 ${row.ledgerCode}`" @click="remove(row)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="table-footer"><span>显示第 {{ pageData.total ? (filter.page - 1) * filter.pageSize + 1 : 0 }}–{{ Math.min(filter.page * filter.pageSize, pageData.total) }} 条，共 {{ pageData.total }} 条</span><el-pagination v-model:current-page="filter.page" v-model:page-size="filter.pageSize" :total="pageData.total" :page-sizes="[10, 20, 50, 100]" layout="sizes, prev, pager, next" background @current-change="load" /></div>
    </section>
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(760px, 96vw)" class="ledger-drawer" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :before-close="(done: () => void) => { if (!submitting) done() }" @closed="detailSequence++"><div v-loading="drawerLoading" class="drawer-content"><LedgerDetail v-if="drawerMode === 'detail' && current" :ledger="current" show-location /><LedgerForm v-else-if="drawerMode === 'create' || current" ref="formRef" :model-value="current" :submitting="submitting" @save="save" /></div><template #footer><div class="drawer-actions"><el-button :disabled="submitting" @click="drawerVisible = false">{{ drawerMode === 'detail' ? '关闭' : '取消' }}</el-button><el-button v-if="drawerMode !== 'detail'" type="primary" :loading="submitting" @click="formRef?.submit()">保存台账</el-button><template v-else-if="current"><el-button @click="openHistory(current.id)">历史记录</el-button><el-button type="danger" plain :icon="Delete" @click="remove(current)">删除台账</el-button><el-button type="primary" @click="openRecord(current.id, 'edit')">编辑记录</el-button></template></div></template></el-drawer>
    <el-drawer v-model="historyVisible" title="历史记录" size="min(850px, 96vw)" class="ledger-drawer"><LedgerHistory v-if="historyVisible && historyId" :ledger-id="historyId" /></el-drawer>
  </div>
</template>
