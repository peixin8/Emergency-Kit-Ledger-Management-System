// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试专用的表格与抽屉轻量桩组件 */
import { defineComponent, h, inject, provide, ref, toRef } from 'vue'
import type { Ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import LedgerListView from './LedgerListView.vue'
import type { Ledger } from '../types/ledger'
import MapCanvas from '../components/MapCanvas.vue'
import LedgerForm from '../components/LedgerForm.vue'
import { ApiError } from '../api/http'
import { emptyLedger } from '../types/ledger'

const listLedgers = vi.fn()
const getLedger = vi.fn()
const listLedgerMapPoints = vi.fn()
const importLedgers = vi.fn()
const deleteLedger = vi.fn()
const exportLedgers = vi.fn()
const createLedger = vi.fn()
vi.mock('../api/ledger', () => ({
  listLedgers: (...args: unknown[]) => listLedgers(...args),
  getLedger: (...args: unknown[]) => getLedger(...args),
  listLedgerMapPoints: (...args: unknown[]) => listLedgerMapPoints(...args),
  createLedger: (...args: unknown[]) => createLedger(...args), updateLedger: vi.fn(), deleteLedger: (...args: unknown[]) => deleteLedger(...args),
  importLedgers: (...args: unknown[]) => importLedgers(...args), exportLedgers: (...args: unknown[]) => exportLedgers(...args)
}))

const ledger: Ledger = {
  id: '9007199254740993', organizationId: '1', version: '1', ledgerCode: 'XM-001',
  originalAddress: '厦门市思明区', destinationAddress: '厦门市湖里区', direction: 'IN',
  applicationMethod: 'ONLINE', applicantContact: '张三', applicantPhone: '13800000000',
  applicationDate: '2026-10-27', deviceCondition: 'GOOD', deviceConditionDetail: null, accessRoom: '机房 A', ptnPort: null,
  fiberCore: '1', networkStandard: '5G', bbuModel: 'BBU-A', rruModel: 'RRU-A',
  bbuRruPowerWatts: '100', managementIp: '10.0.0.1', managementVlan: 101,
  businessIp: '10.1.0.1', businessVlan: 201, deviceSerialNo: 'SN-001',
  assetOwner: '厦门', remarks: '完整详情', createdAt: '2026-10-27T08:00:00Z',
  updatedAt: '2026-10-27T08:00:00Z', latitude: '24.4798345', longitude: '118.0894251'
}

const rowsKey = Symbol('rows')
const Table = defineComponent({
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    provide(rowsKey, toRef(props, 'data'))
    return () => h('div', { class: 'test-table' }, slots.default?.())
  }
})
const Column = defineComponent({
  props: { label: { type: String, default: '' }, prop: { type: String, default: '' } },
  setup(props, { slots }) {
    const rows = inject<Ref<Ledger[]>>(rowsKey, ref([]))
    return () => h('div', { class: 'test-column' }, [
      h('b', props.label),
      ...rows.value.map(row => h('div', { class: 'test-row' }, slots.default?.({ row }) ?? String(Reflect.get(row, props.prop) ?? '')))
    ])
  }
})
const Drawer = defineComponent({
  props: { modelValue: Boolean, title: { type: String, default: '' } },
  setup(props, { slots }) { return () => props.modelValue ? h('aside', { class: 'test-drawer' }, [h('h2', props.title), slots.default?.(), slots.footer?.()]) : null }
})

function mountLedgerList() {
  return mount(LedgerListView, { global: {
    directives: { loading: () => {} },
    stubs: {
      'el-table': Table, 'el-table-column': Column, 'el-drawer': Drawer,
      'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-input': true, 'el-select': true, 'el-option': true, 'el-icon': true,
      'el-pagination': true, LedgerForm: true, LedgerHistory: true, MapCanvas: true,
      'el-tag': { template: '<span><slot /></span>' }
    }
  } })
}

beforeEach(() => {
  listLedgers.mockReset().mockResolvedValue({ records: [ledger], total: 1, page: 1, pageSize: 20 })
  getLedger.mockReset().mockResolvedValue(ledger)
  listLedgerMapPoints.mockReset().mockResolvedValue({
    records: [{ id: ledger.id, ledgerCode: ledger.ledgerCode, destinationAddress: ledger.destinationAddress, deviceCondition: ledger.deviceCondition, latitude: '24.4798345', longitude: '118.0894251' }],
    unlocatedCount: 2
  })
  importLedgers.mockReset()
  deleteLedger.mockReset()
  exportLedgers.mockReset()
  createLedger.mockReset()
})

it('导入工具栏提供手动新增入口，打开与右上角一致的空白表单，并说明系统 ID 规则', async () => {
  const wrapper = mountLedgerList()
  await flushPromises()
  const toolbarButton = wrapper.findAll('.file-actions button').find(button => button.text() === '手动新增')
  expect(toolbarButton).toBeDefined()
  await toolbarButton!.trigger('click')
  expect(wrapper.find('.test-drawer h2').text()).toBe('新增台账')
  expect(wrapper.findComponent(LedgerForm).props('modelValue')).toBeNull()
  expect(getLedger).not.toHaveBeenCalled()
  expect(wrapper.find('.file-actions').text()).toContain('不同台账编号')
  expect(wrapper.find('.file-actions').text()).toContain('不同系统 ID')
  expect(wrapper.find('.file-actions').text()).toContain('同编号覆盖')
  wrapper.unmount()
})

it('工具栏手动新增保存成功后提交新台账并刷新列表与地图', async () => {
  const input = { ...emptyLedger(), ledgerCode: 'XM-NEW', originalAddress: '厦门市思明区', destinationAddress: '厦门市湖里区', applicantContact: '李四' }
  const created = { ...ledger, ...input, id: '2' }
  listLedgers.mockResolvedValueOnce({ records: [ledger], total: 1, page: 1, pageSize: 20 })
    .mockResolvedValue({ records: [created, ledger], total: 2, page: 1, pageSize: 20 })
  createLedger.mockResolvedValue(created)
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.success>)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    await wrapper.findAll('.file-actions button').find(button => button.text() === '手动新增')!.trigger('click')
    wrapper.findComponent(LedgerForm).vm.$emit('save', input)
    await flushPromises()
    expect(createLedger).toHaveBeenCalledWith(input)
    expect(success).toHaveBeenCalledWith('台账已创建')
    expect(listLedgers).toHaveBeenCalledTimes(2)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.test-table').text()).toContain('XM-NEW')
    expect(wrapper.find('.test-drawer').exists()).toBe(false)
    wrapper.unmount()
  } finally { success.mockRestore() }
})

it('工具栏手动新增保存失败时保留表单并反馈错误', async () => {
  const input = { ...emptyLedger(), ledgerCode: 'XM-NEW', originalAddress: '厦门市思明区', destinationAddress: '厦门市湖里区', applicantContact: '李四' }
  createLedger.mockRejectedValue(new Error('服务器暂不可用'))
  const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.error>)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    await wrapper.findAll('.file-actions button').find(button => button.text() === '手动新增')!.trigger('click')
    const form = wrapper.findComponent(LedgerForm)
    form.vm.$emit('save', input)
    await flushPromises()
    expect(createLedger).toHaveBeenCalledWith(input)
    expect(error).toHaveBeenCalledWith('服务器暂不可用')
    expect(wrapper.find('.test-drawer h2').text()).toBe('新增台账')
    expect(wrapper.findComponent(LedgerForm).vm).toBe(form.vm)
    expect(listLedgers).toHaveBeenCalledTimes(1)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  } finally { error.mockRestore() }
})

it('导入同时新增和覆盖台账时分别提示数量，并刷新列表和地图', async () => {
  const imported = { ...ledger, id: '2', ledgerCode: 'XM-002', direction: 'UNSPECIFIED' as const }
  listLedgers.mockResolvedValueOnce({ records: [ledger], total: 1, page: 1, pageSize: 20 })
    .mockResolvedValue({ records: [imported, ledger], total: 2, page: 1, pageSize: 20 })
  importLedgers.mockResolvedValue({ importedCount: 2, createdCount: 1, updatedCount: 1 })
  const success = vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.success>)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    expect(wrapper.text()).toContain('可导入现有台账')
    const fileInput = wrapper.find<HTMLInputElement>('input[type="file"]')
    const file = new File(['fake xlsx bytes'], '现有台账.xlsx')
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [file] })
    await fileInput.trigger('change')
    await flushPromises()
    expect(importLedgers).toHaveBeenCalledWith(file)
    expect(success).toHaveBeenCalledWith(expect.stringContaining('新增 1 条'))
    expect(success).toHaveBeenCalledWith(expect.stringContaining('覆盖 1 条'))
    expect(listLedgers).toHaveBeenCalledTimes(2)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.test-table').text()).toContain('XM-002')
    expect(wrapper.find('.test-table').text()).toContain('厦门市湖里区')
    wrapper.unmount()
  } finally { success.mockRestore() }
})

it('导入校验失败时显示后台给出的行号和列名，不刷新列表', async () => {
  const message = '第 4 行「BBU-RRU功耗」：单位不受支持'
  importLedgers.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', message))
  const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.error>)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    const fileInput = wrapper.find<HTMLInputElement>('input[type="file"]')
    Object.defineProperty(fileInput.element, 'files', { configurable: true, value: [new File(['fake'], '现有台账.xlsx')] })
    await fileInput.trigger('change')
    await flushPromises()
    expect(error).toHaveBeenCalledWith(expect.objectContaining({ message }))
    expect(listLedgers).toHaveBeenCalledTimes(1)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  } finally { error.mockRestore() }
})

it('主表仅显示摘要，点击台账编号后显示完整详情与后续操作', async () => {
  const wrapper = mountLedgerList()
  await flushPromises()

  const table = wrapper.find('.test-table')
  expect(table.text()).toContain('XM-001')
  expect(table.text()).not.toContain('张三')
  expect(table.text()).not.toContain('网管 IP')
  expect(table.text()).not.toContain('10.0.0.1')
  expect(table.findAll('.test-column')).toHaveLength(4)
  expect(table.text()).not.toContain('流转方向')
  expect(table.text()).not.toContain('设备状况')
  expect(wrapper.find('.filter-row').findAll('el-select')).toHaveLength(0)

  await table.find('button.record-link').trigger('click')
  await flushPromises()
  const detail = wrapper.find('.test-drawer')
  expect(detail.text()).toContain('台账详情')
  expect(detail.text()).toContain('张三')
  expect(detail.text()).toContain('流转方向')
  expect(detail.text()).toContain('设备状况')
  expect(detail.text()).toContain('网管 IP')
  expect(detail.text()).toContain('10.0.0.1')
  expect(detail.text()).toContain('纬度')
  expect(detail.text()).toContain('24.4798345')
  expect(detail.text()).toContain('编辑记录')
  expect(detail.text()).toContain('历史记录')
  expect(detail.text()).toContain('删除台账')
  await detail.findAll('button').find(button => button.text() === '编辑记录')!.trigger('click')
  await flushPromises()
  expect(wrapper.find('.test-drawer h2').text()).toBe('编辑台账')

  await table.find('button.record-link').trigger('click')
  await flushPromises()
  await wrapper.find('.test-drawer').findAll('button').find(button => button.text() === '历史记录')!.trigger('click')
  expect(wrapper.findAll('.test-drawer')).toHaveLength(1)
  expect(wrapper.find('.test-drawer h2').text()).toBe('历史记录')
  wrapper.unmount()
})

it('每行编辑按钮打开对应编号的编辑表单', async () => {
  const second = { ...ledger, id: '2', ledgerCode: 'XM-002' }
  listLedgers.mockResolvedValue({ records: [ledger, second], total: 2, page: 1, pageSize: 20 })
  getLedger.mockImplementation((id: string) => Promise.resolve(id === '2' ? second : ledger))
  const wrapper = mountLedgerList()
  await flushPromises()
  const actionRows = wrapper.findAll('.test-column:last-child .test-row')
  await actionRows[1]!.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
  await flushPromises()
  expect(getLedger).toHaveBeenCalledWith('2')
  expect(wrapper.find('.test-drawer h2').text()).toBe('编辑台账')
  wrapper.unmount()
})

it('每行删除按钮取消后保留记录，不调用删除接口', async () => {
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce(new Error('cancel'))
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    const actionRow = wrapper.find('.test-column:last-child .test-row')
    await actionRow.findAll('button').find(button => button.text() === '删除')!.trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('XM-001'), '删除确认', expect.any(Object))
    expect(deleteLedger).not.toHaveBeenCalled()
    expect(wrapper.find('.test-table').text()).toContain('XM-001')
    wrapper.unmount()
  } finally { confirm.mockRestore() }
})

it('每行删除确认后按该行 ID 删除，并刷新列表和地图', async () => {
  const second = { ...ledger, id: '2', ledgerCode: 'XM-002' }
  listLedgers.mockResolvedValueOnce({ records: [ledger, second], total: 2, page: 1, pageSize: 20 })
    .mockResolvedValue({ records: [ledger], total: 1, page: 1, pageSize: 20 })
  // Element Plus 将确认结果声明为不可构造的交叉类型；页面只依赖 Promise 成功完成。
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockImplementationOnce(() => Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
  deleteLedger.mockResolvedValue(undefined)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    const actionRows = wrapper.findAll('.test-column:last-child .test-row')
    await actionRows[1]!.findAll('button').find(button => button.text() === '删除')!.trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('XM-002'), '删除确认', expect.any(Object))
    expect(deleteLedger).toHaveBeenCalledWith('2')
    expect(listLedgers).toHaveBeenCalledTimes(2)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.test-table').text()).not.toContain('XM-002')
    wrapper.unmount()
  } finally { confirm.mockRestore() }
})

it('每行删除失败时保留记录并提示错误', async () => {
  const confirm = vi.spyOn(ElMessageBox, 'confirm').mockImplementationOnce(() => Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
  const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.error>)
  deleteLedger.mockRejectedValue(new Error('服务器暂不可用'))
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    const actionRow = wrapper.find('.test-column:last-child .test-row')
    await actionRow.findAll('button').find(button => button.text() === '删除')!.trigger('click')
    await flushPromises()
    expect(error).toHaveBeenCalledWith('服务器暂不可用')
    expect(listLedgers).toHaveBeenCalledTimes(1)
    expect(listLedgerMapPoints).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.test-table').text()).toContain('XM-001')
    wrapper.unmount()
  } finally { confirm.mockRestore(); error.mockRestore() }
})

it('首页列表和导出仅传入保留的检索条件', async () => {
  exportLedgers.mockRejectedValue(new Error('导出测试中断'))
  const error = vi.spyOn(ElMessage, 'error').mockImplementation(() => ({ close: () => {} }) as ReturnType<typeof ElMessage.error>)
  try {
    const wrapper = mountLedgerList()
    await flushPromises()
    expect(Object.keys(listLedgers.mock.calls[0]![0])).toEqual(['page', 'pageSize', 'keyword'])
    await wrapper.findAll('.file-actions button').find(button => button.text().includes('导出 Excel'))!.trigger('click')
    await flushPromises()
    expect(Object.keys(exportLedgers.mock.calls[0]![0])).toEqual(['keyword'])
    wrapper.unmount()
  } finally { error.mockRestore() }
})

it('厦门地图逐台展示已定位设备入口与未定位数量，点击点位列表打开完整台账', async () => {
  const wrapper = mountLedgerList()
  await flushPromises()
  expect(wrapper.text()).toContain('厦门设备地图')
  expect(wrapper.text()).toContain('待定位 2 台')
  const point = wrapper.find('button.map-point-link')
  expect(point.text()).toContain('XM-001')
  await point.trigger('click')
  await flushPromises()
  expect(wrapper.find('.test-drawer').text()).toContain('完整详情')
  wrapper.unmount()
})

it('点击编号后首页地图聚焦完整记录坐标，切换到无坐标记录时清除焦点', async () => {
  const unlocated = { ...ledger, id: '2', ledgerCode: 'XM-002', latitude: null, longitude: null }
  listLedgers.mockResolvedValue({ records: [ledger, unlocated], total: 2, page: 1, pageSize: 20 })
  getLedger.mockImplementation((id: string) => Promise.resolve(id === '2' ? unlocated : ledger))
  const wrapper = mountLedgerList()
  await flushPromises()
  const codes = wrapper.findAll('button.record-link')
  await codes[0]!.trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toEqual({ latitude: ledger.latitude, longitude: ledger.longitude })
  await codes[1]!.trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toBeNull()
  wrapper.unmount()
})

it('已定位详情切换到读取失败的记录时立即清除旧焦点，失败后不展示旧详情', async () => {
  const second = { ...ledger, id: '2', ledgerCode: 'XM-002' }
  listLedgers.mockResolvedValue({ records: [ledger, second], total: 2, page: 1, pageSize: 20 })
  getLedger.mockImplementation((id: string) => id === '2' ? Promise.reject(new Error('读取失败')) : Promise.resolve(ledger))
  const wrapper = mountLedgerList()
  await flushPromises()
  const codes = wrapper.findAll('button.record-link')
  await codes[0]!.trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toEqual({ latitude: ledger.latitude, longitude: ledger.longitude })

  await codes[1]!.trigger('click')
  expect(wrapper.findComponent(MapCanvas).props('selected')).toBeNull()
  await flushPromises()
  expect(wrapper.find('.test-drawer').exists()).toBe(false)
  expect(wrapper.findComponent(MapCanvas).props('selected')).toBeNull()
  wrapper.unmount()
})

it('快速切换台账时旧请求晚返回也不能覆盖新点位和新详情', async () => {
  const second = { ...ledger, id: '2', ledgerCode: 'XM-002', destinationAddress: '厦门市集美区', latitude: '24.5750000', longitude: '118.1000000' }
  listLedgers.mockResolvedValue({ records: [ledger, second], total: 2, page: 1, pageSize: 20 })
  let resolveFirst: (value: Ledger) => void = () => { throw new Error('旧请求未初始化') }
  const delayedFirst = new Promise<Ledger>(resolve => { resolveFirst = resolve })
  getLedger.mockImplementation((id: string) => id === '2' ? Promise.resolve(second) : delayedFirst)
  const wrapper = mountLedgerList()
  await flushPromises()
  const codes = wrapper.findAll('button.record-link')
  await codes[0]!.trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toBeNull()

  await codes[1]!.trigger('click')
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toEqual({ latitude: second.latitude, longitude: second.longitude })
  expect(wrapper.find('.test-drawer').text()).toContain('XM-002')
  resolveFirst(ledger)
  await flushPromises()
  expect(wrapper.findComponent(MapCanvas).props('selected')).toEqual({ latitude: second.latitude, longitude: second.longitude })
  expect(wrapper.find('.test-drawer').text()).toContain('XM-002')
  expect(wrapper.find('.test-drawer').text()).not.toContain('XM-001')
  wrapper.unmount()
})
