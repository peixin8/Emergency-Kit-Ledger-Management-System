<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CirclePlus, Delete, Download, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { createLedger, deleteLedger, exportLedgerBatches, exportLedgers, getLedger, listLedgers, updateLedger } from '../api/ledger'
import { ApiError } from '../api/http'
import ImportPreview from '../components/ImportPreview.vue'
import DeletedLedgers from '../components/DeletedLedgers.vue'
import { changedFields } from '../types/history'
import LedgerForm from '../components/LedgerForm.vue'
import LedgerDetail from '../components/LedgerDetail.vue'
import LedgerHistory from '../components/LedgerHistory.vue'
import LedgerLocationMap from '../components/LedgerLocationMap.vue'
import type { Ledger, ImportResult, LedgerFilters, LedgerInput, LedgerPage } from '../types/ledger'
import { formatLedgerTimestamp } from '../types/date'
import { ledgerCoordinates } from '../types/location'

const loading = ref(false)
const listError = ref('')
const pageData = ref<LedgerPage>({ records: [], total: 0, page: 1, pageSize: 20 })
const filter = reactive<{ keyword: string; page: number; pageSize: number; locationStatus?: 'UNLOCATED' }>({ keyword: '', page: 1, pageSize: 20 })
const activeFilters = ref<LedgerFilters>({})
const refreshing = ref(false)
const deletedView = ref(false)
const refreshedAt = ref('')
const dirty = ref(false)
const conflict = ref(false)
const latest = ref<Ledger | null>(null)
const conflictDraft = ref<LedgerInput | null>(null)
const latestLoading = ref(false)
const importFile = ref<File | null>(null)
const importBusy = ref(false)
const importVisible = computed(() => !!importFile.value)
const comparison = computed(() => latest.value && conflictDraft.value ? changedFields(latest.value, conflictDraft.value) : [])
const drawerVisible = ref(false)
const drawerMode = ref<'create' | 'edit' | 'detail'>('create')
const current = ref<Ledger | null>(null)
const drawerLoading = ref(false)
const submitting = ref(false)
const formSession = ref(0)
const formRef = ref<InstanceType<typeof LedgerForm> | null>(null)
const locationMapRef = ref<InstanceType<typeof LedgerLocationMap> | null>(null)
const historyVisible = ref(false)
const historyId = ref('')
const exporting = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const drawerTitle = computed(() => drawerMode.value === 'create' ? '新增台账' : drawerMode.value === 'edit' ? '编辑台账' : '台账详情')
const selectedCoordinates = computed(() => drawerMode.value === 'detail' ? ledgerCoordinates(current.value) : null)
let activeRequest: AbortController | null = null
let requestSequence = 0
let detailSequence = 0
let refreshSequence = 0
let pendingRefresh: Promise<void> | null = null
let pendingRefreshKey = ''

async function load() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  const sequence = ++requestSequence
  loading.value = true
  listError.value = ''
  try {
    const result = await listLedgers({ page: filter.page, pageSize: filter.pageSize, ...activeFilters.value }, controller.signal)
    if (sequence === requestSequence) { pageData.value = result; return true }
  } catch (error) {
    if (sequence === requestSequence) listError.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}
function refresh() { return refreshData(false) }
function refreshData(force: boolean): Promise<void> {
  const key = JSON.stringify({ page: filter.page, pageSize: filter.pageSize, ...activeFilters.value })
  if (!force && pendingRefresh && pendingRefreshKey === key) return pendingRefresh
  pendingRefreshKey = key
  const sequence = ++refreshSequence; refreshing.value = true
  pendingRefresh = (async () => {
    try {
      const result = await Promise.all([load(), locationMapRef.value?.reload(activeFilters.value)])
      if (sequence === refreshSequence && result[0] && result[1]) refreshedAt.value = formatLedgerTimestamp(new Date().toISOString())
    } finally { if (sequence === refreshSequence) { refreshing.value = false; pendingRefresh = null } }
  })()
  return pendingRefresh
}
function search() { filter.page = 1; activeFilters.value = { keyword: filter.keyword.trim() || undefined, ...(filter.locationStatus ? { locationStatus: filter.locationStatus } : {}) }; void refresh() }
function reset() { filter.keyword = ''; filter.locationStatus = undefined; search() }
function unlocated() { filter.locationStatus = filter.locationStatus ? undefined : 'UNLOCATED'; search() }
async function canLeave() {
  if (submitting.value) return false
  if (!drawerVisible.value || !dirty.value || drawerMode.value === 'detail') return true
  try { await ElMessageBox.confirm('尚有未保存的修改，放弃后无法找回。', '放弃修改？', { type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续编辑' }); return true } catch { return false }
}
async function switchView(deleted: boolean) { if (!await canLeave()) return; dirty.value = false; drawerVisible.value = false; detailSequence++; deletedView.value = deleted }
async function closeDrawer(done?: () => void) { if (!await canLeave()) return; dirty.value = false; detailSequence++; if (done) done(); else drawerVisible.value = false }
async function openCreate() { if (!await canLeave()) return; dirty.value = false; conflict.value = false; latest.value = null; latestLoading.value = false; formSession.value++; detailSequence++; drawerLoading.value = false; current.value = null; drawerMode.value = 'create'; drawerVisible.value = true }
async function openRecord(id: string, mode: 'edit' | 'detail') {
  if (!await canLeave()) return
  dirty.value = false; conflict.value = false; latest.value = null; latestLoading.value = false; formSession.value++
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
  if (submitting.value || conflict.value) return
  submitting.value = true
  try {
    if (drawerMode.value === 'edit' && current.value) await updateLedger(current.value.id, input, current.value.version)
    else await createLedger(input)
    ElMessage.success(drawerMode.value === 'edit' ? '台账已更新' : '台账已创建')
    dirty.value = false; drawerVisible.value = false
    await refreshData(true)
  } catch (error) {
    if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') { conflict.value = true; conflictDraft.value = { ...input }; latest.value = null; ElMessage.warning('记录已被他人修改，草稿已保留，请查看最新记录并比较。') }
    else if (error instanceof ApiError && error.status === 409) ElMessage.error('台账编号重复，请修改编号后重试')
    else ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally { submitting.value = false }
}
async function remove(record: Ledger) {
  if (!await canLeave()) return
  try { await ElMessageBox.confirm(`确认删除台账「${record.ledgerCode}」？此操作会保留审计记录。`, '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }) }
  catch { return }
  try { await deleteLedger(record.id, record.version); dirty.value = false; detailSequence++; drawerVisible.value = false; current.value = null; ElMessage.success('台账已删除'); if (pageData.value.records.length === 1 && filter.page > 1) filter.page--; await refreshData(true) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '删除失败') }
}
async function openHistory(id: string) { if (!await canLeave()) return; dirty.value = false; drawerVisible.value = false; historyId.value = id; historyVisible.value = true }
async function viewLatest() {
  if (!current.value || latestLoading.value) return
  const id = current.value.id; const session = formSession.value; const sequence = detailSequence
  const isCurrent = () => drawerVisible.value && current.value?.id === id && formSession.value === session && detailSequence === sequence && conflict.value
  latestLoading.value = true
  try { const result = await getLedger(id); if (isCurrent()) latest.value = result }
  catch (reason) { if (isCurrent()) ElMessage.error(reason instanceof Error ? reason.message : '读取最新记录失败') }
  finally { if (isCurrent()) latestLoading.value = false }
}
async function restartEditing() {
  if (!latest.value) return
  const selected = latest.value; const session = formSession.value; const sequence = detailSequence
  try { await ElMessageBox.confirm('这会放弃当前草稿，载入最新版本供重新编辑；请先核对差异。', '重新编辑最新记录', { confirmButtonText: '载入最新并重新编辑', cancelButtonText: '保留草稿' }) } catch { return }
  if (!drawerVisible.value || !conflict.value || session !== formSession.value || sequence !== detailSequence || selected.id !== current.value?.id) return
  current.value = selected; formSession.value++; dirty.value = false; conflict.value = false; latest.value = null
}
async function downloadExcel(batches = false) {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await (batches ? exportLedgerBatches(activeFilters.value) : exportLedgers(activeFilters.value))
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `应急宝台账-${new Date().toLocaleDateString('sv-SE')}.${batches ? 'zip' : 'xlsx'}`
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success('Excel 已下载')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导出失败') }
  finally { exporting.value = false }
}
function uploadExcel(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]; input.value = ''
  if (!file || importBusy.value) return
  if (!/\.xlsx$/i.test(file.name)) { ElMessage.error('请选择 .xlsx 文件'); return }
  if (file.size > 2 * 1024 * 1024) { ElMessage.error('文件不能超过 2 MiB'); return }
  importFile.value = file
}
async function imported(result: ImportResult) {
  importFile.value = null
  ElMessage.success(`已处理 ${result.importedCount} 条有效台账：新增 ${result.createdCount} 条，实际覆盖 ${result.updatedCount} 条，无变化 ${result.unchangedCount} 条`)
  filter.page = 1; await refreshData(true)
}
function beforeUnload(event: BeforeUnloadEvent) { if ((drawerVisible.value && dirty.value) || submitting.value || importBusy.value) { event.preventDefault(); event.returnValue = '' } }
function activated() { if (!refreshing.value && !deletedView.value) void refresh() }
watch(() => filter.pageSize, () => { filter.page = 1; void refresh() })
onMounted(() => { void refresh(); window.addEventListener('beforeunload', beforeUnload); window.addEventListener('focus', activated) })
onBeforeUnmount(() => { activeRequest?.abort(); requestSequence++; detailSequence++; window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('focus', activated) })
</script>

<template>
  <div class="workspace">
    <div class="page-heading"><div><div class="eyebrow">EQUIPMENT LEDGER <span class="eyebrow-line"></span> 共享工作台</div><h1>设备进出台账</h1><p>多人共用同一份台账，点击编号查看完整信息与历史记录。</p></div></div>
    <div class="overview-strip"><div><span class="overview-icon">▤</span><span><small>当前结果</small><strong>{{ pageData.total }} <em>条台账</em></strong></span></div><div class="overview-note"><span class="status-dot"></span> {{ refreshedAt ? `最后成功刷新：${refreshedAt}` : '正在读取共享台账' }}</div></div>
    <div class="view-switch"><el-button :type="!deletedView ? 'primary' : 'default'" @click="switchView(false)">当前台账</el-button><el-button :type="deletedView ? 'primary' : 'default'" @click="switchView(true)">已删除记录</el-button><el-button v-if="!deletedView" :disabled="refreshing" @click="refresh">刷新台账与地图</el-button></div>
    <DeletedLedgers v-if="deletedView" @history="openHistory" />
    <LedgerLocationMap v-show="!deletedView" ref="locationMapRef" :selected="selectedCoordinates" @unlocated="unlocated" @refresh="refresh" @open="id => openRecord(id, 'detail')" />
    <section v-if="!deletedView" class="data-panel"><div class="panel-heading"><div><h2>台账记录</h2><p>按编号、位置及设备信息快速检索；点击编号查看详情</p></div><span class="panel-count">共 {{ pageData.total }} 条</span></div>
      <div class="filter-row"><el-button :type="filter.locationStatus ? 'primary' : 'default'" @click="unlocated">{{ filter.locationStatus ? '仅看待定位（点击取消）' : '待定位' }}</el-button><el-input v-model="filter.keyword" class="search-input" clearable maxlength="100" placeholder="编号、地址、联系人、序列号、机房、IP、资产归属" @keyup.enter="search" @clear="search"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-button @click="reset"><el-icon><Refresh /></el-icon> 重置</el-button><el-button type="primary" plain @click="search"><el-icon><Search /></el-icon> 查询</el-button></div>
      <div class="file-row"><div class="file-actions"><el-button :loading="exporting" @click="downloadExcel()"><el-icon><Download /></el-icon> 导出 Excel</el-button><el-button :disabled="importVisible" @click="fileInput?.click()"><el-icon><Upload /></el-icon> 导入 Excel</el-button><el-button :loading="exporting" @click="downloadExcel(true)">ZIP 分卷导出</el-button><el-button type="primary" plain @click="openCreate"><el-icon><CirclePlus /></el-icon> 新增台账</el-button><input ref="fileInput" type="file" accept=".xlsx" class="visually-hidden" aria-label="选择 Excel 文件" @change="uploadExcel" /><details class="import-instructions"><summary>导入规则与文件限制</summary><span>可导入现有台账或系统导出的 .xlsx 文件；Excel 中不同台账编号会生成不同系统 ID，同编号仅在信息变化时覆盖并保留原 ID，完全相同则计为无变化；不支持在 Excel 指定系统 ID。覆盖会更新全部业务信息，空白值会清空旧值；旧版模板缺少的可选列也会清空旧值，建议先导出新版文件后编辑重导入。导入失败时会提示具体行号和列名，整份文件不会部分导入。最多 1000 行、2 MiB，预览后确认才写入。</span></details></div></div>
      <div v-if="listError" class="list-error" role="alert"><span>{{ listError }}</span><el-button link type="primary" @click="refresh">重试</el-button></div>
      <el-table v-loading="loading" :data="pageData.records" class="ledger-table" empty-text="暂无台账记录，可点击右上角新增" height="min(58vh, 570px)" row-key="id">
        <el-table-column label="台账编号" min-width="165"><template #default="{ row }"><button class="record-link" @click="openRecord(row.id, 'detail')">{{ row.ledgerCode }} <el-icon><ArrowRight /></el-icon></button><small class="table-sub">ID {{ row.id }}</small></template></el-table-column>
        <el-table-column label="原始地址 → 目标地址" min-width="260"><template #default="{ row }"><div class="address-cell"><span>{{ row.originalAddress }}</span><el-icon><ArrowRight /></el-icon><span>{{ row.destinationAddress }}</span></div></template></el-table-column>
        <el-table-column label="更新时间" width="165"><template #default="{ row }">{{ formatLedgerTimestamp(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="145"><template #default="{ row }"><div class="row-actions"><el-button link type="primary" :aria-label="`编辑台账 ${row.ledgerCode}`" @click="openRecord(row.id, 'edit')">编辑</el-button><el-button link type="danger" :aria-label="`删除台账 ${row.ledgerCode}`" @click="remove(row)">删除</el-button></div></template></el-table-column>
      </el-table>
      <div class="table-footer"><span>显示第 {{ pageData.total ? (filter.page - 1) * filter.pageSize + 1 : 0 }}–{{ Math.min(filter.page * filter.pageSize, pageData.total) }} 条，共 {{ pageData.total }} 条</span><el-pagination v-model:current-page="filter.page" v-model:page-size="filter.pageSize" :total="pageData.total" :page-sizes="[10, 20, 50, 100]" layout="sizes, prev, pager, next" background @current-change="refresh" /></div>
    </section>
    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="min(760px, 96vw)" class="ledger-drawer" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :before-close="closeDrawer" @closed="detailSequence++"><div v-loading="drawerLoading" class="drawer-content"><LedgerDetail v-if="drawerMode === 'detail' && current" :ledger="current" show-location /><LedgerForm v-else-if="drawerMode === 'create' || current" :key="formSession" ref="formRef" :model-value="current" :submitting="submitting || conflict" @dirty="dirty = $event" @save="save" /><section v-if="conflict" class="conflict-panel" role="alert"><h3>版本冲突：草稿已保留</h3><p>保存已暂停。查看最新记录与当前草稿差异，确认后重新编辑。</p><el-button :loading="latestLoading" @click="viewLatest">查看最新并比较</el-button><template v-if="latest"><table class="comparison-table"><thead><tr><th>字段</th><th>最新记录</th><th>当前草稿</th></tr></thead><tbody><tr v-for="field in comparison" :key="field.label"><td>{{ field.label }}</td><td>{{ field.before }}</td><td>{{ field.after }}</td></tr></tbody></table><p v-if="!comparison.length">业务字段一致，版本已变化。</p><details><summary>查看最新完整记录</summary><LedgerDetail :ledger="latest" /></details><el-button @click="restartEditing">确认载入最新，重新编辑</el-button></template></section></div><template #footer><div class="drawer-actions"><el-button :disabled="submitting" @click="closeDrawer()">{{ drawerMode === 'detail' ? '关闭' : '取消' }}</el-button><el-button v-if="drawerMode !== 'detail'" type="primary" :loading="submitting" :disabled="conflict" @click="formRef?.submit()">保存台账</el-button><template v-else-if="current"><el-button @click="openHistory(current.id)">历史记录</el-button><el-button type="danger" plain :icon="Delete" @click="remove(current)">删除台账</el-button><el-button type="primary" @click="openRecord(current.id, 'edit')">编辑记录</el-button></template></div></template></el-drawer>
    <el-drawer :model-value="importVisible" title="导入预览与确认" size="min(850px, 96vw)" :close-on-click-modal="!importBusy" :close-on-press-escape="!importBusy" :before-close="(done: () => void) => { if (!importBusy) { importFile = null; done() } }"><ImportPreview v-if="importFile" :file="importFile" @cancel="importFile = null" @busy="importBusy = $event" @imported="imported" /></el-drawer>
    <el-drawer v-model="historyVisible" title="历史记录" size="min(850px, 96vw)" class="ledger-drawer"><LedgerHistory v-if="historyVisible && historyId" :ledger-id="historyId" /></el-drawer>
  </div>
</template>
