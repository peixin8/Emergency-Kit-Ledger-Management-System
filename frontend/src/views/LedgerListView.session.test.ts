import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus, { ElMessageBox } from 'element-plus'
import LedgerListView from './LedgerListView.vue'
import type { Ledger } from '../types/ledger'

// 保留完整响应结构与真实 Drawer/Form/Input/Transition；地图仅验证表单外的契约。
const recordA: Ledger = {
  id: '9007199254740993', organizationId: '1', version: '9007199254740995',
  createdAt: '2026-10-26T09:00:00+08:00', updatedAt: '2026-10-27T10:00:00+08:00',
  ledgerCode: 'YJB-A', originalAddress: 'A 原地址', destinationAddress: 'A 目标地址',
  direction: 'OUT', applicationMethod: 'PHONE', applicantContact: '张三',
  applicantPhone: '13800000000', applicationDate: '2026-10-26', deviceCondition: 'REPAIR',
  accessRoom: 'A 机房', fiberCore: 'A 纤芯', networkStandard: '5G', bbuModel: 'BBU-A',
  rruModel: 'RRU-A', bbuRruPowerWatts: '120.50', managementIp: '10.0.0.1',
  managementVlan: 100, businessIp: '10.0.1.1', businessVlan: 200,
  deviceSerialNo: 'SN-A', assetOwner: '运维部', remarks: 'A 备注',
  deviceConditionDetail: 'A 故障说明', ptnPort: 'A 网元+槽口',
  latitude: '24.4798345', longitude: '118.0894251'
}
const recordB: Ledger = {
  ...recordA, id: '9007199254740994', ledgerCode: 'YJB-B', originalAddress: 'B 原地址',
  destinationAddress: 'B 目标地址', applicantContact: '李四', remarks: 'B 备注'
}
const success = (data: unknown) => new Response(JSON.stringify({ code: 'OK', message: '成功', data, traceId: 'test-success' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
let wrapper: VueWrapper
let transitionStyle: HTMLStyleElement
let saveResponse: () => Promise<Response>

function button(text: string, scope: Element = document.body) {
  const found = [...scope.querySelectorAll<HTMLButtonElement>('button')].find(candidate => candidate.textContent?.trim() === text)
  if (!found) throw new Error(`未找到按钮：${text}`)
  return found
}
function drawer() {
  const found = document.querySelector<HTMLElement>('.ledger-drawer')
  if (!found) throw new Error('台账抽屉未挂载')
  return found
}
function field(label: string) {
  const found = [...drawer().querySelectorAll<HTMLElement>('.el-form-item')].find(item => item.querySelector('label')?.textContent?.trim() === label)
  if (!found) throw new Error(`未找到字段：${label}`)
  return found
}
function input(label: string) {
  const found = field(label).querySelector<HTMLInputElement | HTMLTextAreaElement>('input, textarea')
  if (!found) throw new Error(`未找到输入框：${label}`)
  return found
}
async function enter(label: string, value: string) {
  const control = input(label)
  control.value = value
  control.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}
async function click(target: HTMLElement) {
  target.click()
  await nextTick()
  await flushPromises()
}
async function openCreate() {
  await click(button('新增台账'))
  await vi.waitFor(() => expect(drawer().classList.contains('open')).toBe(true))
  await nextTick()
}
async function openEdit(id: string) {
  const link = [...document.querySelectorAll<HTMLButtonElement>('button.record-link')]
    .find(candidate => candidate.textContent?.includes(id))
  if (!link) throw new Error(`未找到台账：${id}`)
  await click(link)
  await vi.waitFor(() => expect(button('编辑记录', drawer())).toBeTruthy())
  await click(button('编辑记录', drawer()))
}
async function closeAndWait(target = button('取消', drawer())) {
  await click(target)
  await vi.waitFor(() => expect(drawer().closest<HTMLElement>('.el-overlay')?.style.display).toBe('none'))
}
function expectDefaults(date = '2026-10-27') {
  for (const label of ['台账编号', '原始地址', '目标地址', '申请联系人', '联系电话', '设备序列号', '设备状况说明', '接入机房', 'PTN 端口（网元+槽口）', '纤芯', '制式', '资产归属', 'BBU 型号', 'RRU 型号', 'BBU–RRU 使用功耗（W）', '网管 IP', '网管 VLAN', '业务 IP', '业务 VLAN', '纬度', '经度', '备注']) {
    expect(input(label).value, label).toBe('')
  }
  expect(input('申请日期').value).toBe(date)
  expect(field('流转方向').textContent).toContain('进场')
  expect(field('进出申请方式').textContent).toContain('线上申请')
  expect(field('应急宝设备状况').textContent).toContain('状况良好')
  expect(drawer().querySelectorAll('.el-form-item__error')).toHaveLength(0)
}
async function fillValidCreate() {
  await enter('台账编号', 'YJB-NEW')
  await enter('原始地址', '仓库')
  await enter('目标地址', '应急现场')
  await enter('申请联系人', '王五')
  await enter('设备状况说明', '本次故障说明')
  await enter('PTN 端口（网元+槽口）', '本次网元+槽口')
  await enter('纬度', '24.4798345')
  await enter('经度', '118.0894251')
  await enter('备注', '本次输入')
}

beforeEach(async () => {
  vi.spyOn(ElMessageBox, 'confirm').mockImplementation(() => Promise.resolve('confirm') as ReturnType<typeof ElMessageBox.confirm>)
  vi.setSystemTime(new Date('2026-10-27T04:00:00Z'))
  // jsdom 没有布局观察器；不替换组件行为。
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  saveResponse = async () => success({ ...recordA, ledgerCode: 'YJB-NEW' })
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options) => {
    const path = String(url)
    if (options?.method === 'POST' || options?.method === 'PUT') return saveResponse()
    if (path.endsWith('/ledgers/map-points')) return success({ records: [], unlocatedCount: 2 })
    if (path.includes('/ledgers?')) return success({ records: [recordA, recordB], total: 2, page: 1, pageSize: 20 })
    if (path.endsWith(`/${recordA.id}`)) return success(recordA)
    if (path.endsWith(`/${recordB.id}`)) return success(recordB)
    throw new Error(`未安排的 HTTP 请求：${path}`)
  })
  // 显式保留一个真实 CSS 过渡窗口，快速重开用例不会绕过关闭动画。
  transitionStyle = document.createElement('style')
  transitionStyle.textContent = '.el-drawer-fade-enter-active, .el-drawer-fade-leave-active { transition-property: opacity; transition-duration: 0.1s; }'
  document.head.appendChild(transitionStyle)
  wrapper = mount(LedgerListView, { attachTo: document.body, global: { plugins: [ElementPlus], stubs: { MapCanvas: true, transition: false, 'transition-group': false, teleport: false } } })
  await flushPromises()
})
afterEach(() => {
  wrapper?.unmount()
  transitionStyle?.remove()
  document.body.replaceChildren()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

// 真实 Element Plus 表单与抽屉经历多轮详情/编辑转换，保留过渡且给完整流程足够时限。
describe('新增台账独立表单会话', { timeout: 15_000 }, () => {
  it('取消后再次新增清空草稿和旧校验，并按当前日期初始化', async () => {
    await openCreate()
    await enter('备注', '不应带到下次的草稿')
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(drawer().textContent).toContain('请输入台账编号'))
    await closeAndWait()
    vi.setSystemTime(new Date('2026-10-28T04:00:00Z'))
    await openCreate()
    expectDefaults('2026-10-28')
  })

  it('使用抽屉关闭按钮后再次新增清空输入', async () => {
    await openCreate()
    await fillValidCreate()
    await closeAndWait(drawer().querySelector<HTMLButtonElement>('.el-drawer__close-btn')!)
    await openCreate()
    expectDefaults()
  })

  it('关闭动画未结束立即重开也创建空白会话', async () => {
    await openCreate()
    await enter('台账编号', '上次草稿')
    await click(button('取消', drawer()))
    expect(drawer().closest('.el-overlay')?.classList.contains('el-drawer-fade-leave-active')).toBe(true)
    await openCreate()
    expectDefaults()
    // 确认过渡结束后新会话仍可编辑，不会被旧 closed 回调清走。
    await vi.waitFor(() => expect(drawer().closest('.el-overlay')?.className).not.toContain('fade-'))
    await enter('台账编号', '新草稿')
    expect(input('台账编号').value).toBe('新草稿')
  })

  it('保存成功后再次新增恢复默认值', async () => {
    await openCreate()
    await fillValidCreate()
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(document.body.textContent).toContain('台账已创建'))
    await vi.waitFor(() => expect(drawer().closest<HTMLElement>('.el-overlay')?.style.display).toBe('none'))
    await openCreate()
    expectDefaults()
  })

  it('编辑后新增清除记录字段和旧校验', async () => {
    await openEdit('YJB-A')
    await vi.waitFor(() => expect(input('台账编号').value).toBe('YJB-A'))
    expect(input('设备状况说明').value).toBe('A 故障说明')
    expect(input('PTN 端口（网元+槽口）').value).toBe('A 网元+槽口')
    expect(input('纬度').value).toBe(recordA.latitude)
    expect(input('经度').value).toBe(recordA.longitude)
    await enter('目标地址', '')
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(drawer().textContent).toContain('请输入目标地址'))
    await closeAndWait()
    await openCreate()
    expectDefaults()
  })

  it('编辑 A 后编辑 B 显示 B 内容，再次新增不沿用编辑内容', async () => {
    await openEdit('YJB-A')
    await vi.waitFor(() => expect(input('台账编号').value).toBe('YJB-A'))
    expect(input('备注').value).toBe('A 备注')
    await enter('备注', 'A 未保存内容')
    await closeAndWait()
    await openEdit('YJB-B')
    await vi.waitFor(() => expect(input('台账编号').value).toBe('YJB-B'))
    expect(input('原始地址').value).toBe('B 原地址')
    expect(input('备注').value).toBe('B 备注')
    await closeAndWait()
    await openCreate()
    expectDefaults()
  })

  it('保存失败保留输入并允许重试，提交期间阻止关闭和重复提交', async () => {
    let finishRequest: (response: Response) => void = () => { throw new Error('请求尚未开始') }
    saveResponse = () => new Promise<Response>(resolve => { finishRequest = resolve })
    await openCreate()
    await fillValidCreate()
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(button('取消', drawer()).disabled).toBe(true))
    expect(input('备注').disabled).toBe(true)
    expect(input('台账编号').disabled).toBe(true)
    expect(drawer().querySelector<HTMLButtonElement>('.address-search-button')?.disabled).toBe(true)
    await click(button('保存台账', drawer()))
    await click(drawer().querySelector<HTMLButtonElement>('.el-drawer__close-btn')!)
    expect(drawer().classList.contains('open')).toBe(true)
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
    finishRequest(new Response(JSON.stringify({ code: 'INTERNAL_ERROR', message: '本次保存失败', data: null, traceId: 'test-failure' }), { status: 500 }))
    await vi.waitFor(() => expect(document.body.textContent).toContain('本次保存失败'))
    expect(input('台账编号').value).toBe('YJB-NEW')
    expect(input('备注').value).toBe('本次输入')
    expect(input('设备状况说明').value).toBe('本次故障说明')
    expect(input('PTN 端口（网元+槽口）').value).toBe('本次网元+槽口')
    expect(input('纬度').value).toBe('24.4798345')
    expect(input('经度').value).toBe('118.0894251')
    expect(button('取消', drawer()).disabled).toBe(false)
    saveResponse = async () => success({ ...recordA, ledgerCode: 'YJB-NEW' })
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(document.body.textContent).toContain('台账已创建'))
  })
})

it('取消草稿需要明确放弃，继续编辑保留输入，浏览器离开会提醒', async () => {
  vi.mocked(ElMessageBox.confirm).mockRestore()
  await openCreate()
  await enter('备注', '保留我的输入')
  await click(button('取消', drawer()))
  expect(document.body.textContent).toContain('尚有未保存的修改')
  await click(button('继续编辑', document.body))
  expect(input('备注').value).toBe('保留我的输入')
  const event = new Event('beforeunload', { cancelable: true })
  window.dispatchEvent(event)
  expect(event.defaultPrevented).toBe(true)
})

it('版本冲突保留草稿，比较最新值后必须明确确认才能载入新版本', async () => {
  saveResponse = async () => new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: '已变化', data: null }), { status: 409 })
  await openEdit('YJB-A')
  await enter('备注', '我的草稿')
  await click(button('保存台账', drawer()))
  expect(input('备注').value).toBe('我的草稿')
  expect(button('保存台账', drawer()).disabled).toBe(true)
  expect(input('备注').disabled).toBe(true)
  const newest = { ...recordA, version: '9007199254740996', remarks: '他人修改' }
  vi.mocked(fetch).mockResolvedValueOnce(success(newest))
  await click(button('查看最新并比较', drawer()))
  expect(drawer().querySelector('.comparison-table')?.textContent).toContain('他人修改')
  expect(input('备注').value).toBe('我的草稿')
  vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
  await click(button('确认载入最新，重新编辑', drawer()))
  expect(input('备注').value).toBe('我的草稿')
  await click(button('确认载入最新，重新编辑', drawer()))
  expect(input('备注').value).toBe('他人修改')
})

it('切换当前与已删除视图同样保护未保存草稿', async () => {
  vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
  await openCreate(); await enter('备注', '未保存')
  await click(button('已删除记录'))
  expect(ElMessageBox.confirm).toHaveBeenCalledWith(expect.stringContaining('尚有未保存'), '放弃修改？', expect.any(Object))
  expect(input('备注').value).toBe('未保存')
  expect(document.querySelector('.deleted-ledgers')).toBeNull()
})
it('旧记录最新值晚返回不能注入另一条记录的冲突比较', async () => {
  saveResponse = async () => new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: '已变化', data: null }), { status: 409 })
  await openEdit('YJB-A'); await enter('备注', 'A草稿'); await click(button('保存台账', drawer()))
  let finish: (value: Response) => void = () => { throw new Error('尚未发请求') }
  vi.mocked(fetch).mockImplementationOnce(() => new Promise<Response>(resolve => { finish = resolve }))
  await click(button('查看最新并比较', drawer()))
  await openEdit('YJB-B'); await enter('备注', 'B草稿'); await click(button('保存台账', drawer()))
  finish(success({ ...recordA, remarks: 'A最新值' })); await flushPromises()
  expect(drawer().querySelector('.comparison-table')).toBeNull()
  expect(input('备注').value).toBe('B草稿')
})
