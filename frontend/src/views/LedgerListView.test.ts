import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import LedgerListView from './LedgerListView.vue'
import type { Ledger } from '../types/ledger'

// 保留完整响应结构；只替换外部 HTTP，不替换 Drawer/Form/Input 或 Transition。
const recordA: Ledger = {
  id: '9007199254740993', organizationId: '1', version: '9007199254740995',
  createdAt: '2026-10-26T09:00:00+08:00', updatedAt: '2026-10-27T10:00:00+08:00',
  ledgerCode: 'YJB-A', originalAddress: 'A 原地址', destinationAddress: 'A 目标地址',
  direction: 'OUT', applicationMethod: 'PHONE', applicantContact: '张三',
  applicantPhone: '13800000000', applicationDate: '2026-10-26', deviceCondition: 'REPAIR',
  accessRoom: 'A 机房', fiberCore: 'A 纤芯', networkStandard: '5G', bbuModel: 'BBU-A',
  rruModel: 'RRU-A', bbuRruPowerWatts: '120.50', managementIp: '10.0.0.1',
  managementVlan: 100, businessIp: '10.0.1.1', businessVlan: 200,
  deviceSerialNo: 'SN-A', assetOwner: '运维部', remarks: 'A 备注'
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
async function closeAndWait(target = button('取消', drawer())) {
  await click(target)
  await vi.waitFor(() => expect(drawer().closest<HTMLElement>('.el-overlay')?.style.display).toBe('none'))
}
function expectDefaults(date = '2026-10-27') {
  for (const label of ['台账编号', '原始地址', '目标地址', '申请联系人', '联系电话', '设备序列号', '接入机房', '纤芯', '制式', '资产归属', 'BBU 型号', 'RRU 型号', 'BBU–RRU 使用功耗（W）', '网管 IP', '网管 VLAN', '业务 IP', '业务 VLAN', '备注']) {
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
  await enter('备注', '本次输入')
}

beforeEach(async () => {
  vi.setSystemTime(new Date('2026-10-27T04:00:00Z'))
  // jsdom 没有布局观察器；不替换组件行为。
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  saveResponse = async () => success({ ...recordA, ledgerCode: 'YJB-NEW' })
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options) => {
    const path = String(url)
    if (options?.method === 'POST' || options?.method === 'PUT') return saveResponse()
    if (path.includes('/ledgers?')) return success({ records: [recordA, recordB], total: 2, page: 1, pageSize: 20 })
    if (path.endsWith(`/${recordA.id}`)) return success(recordA)
    if (path.endsWith(`/${recordB.id}`)) return success(recordB)
    throw new Error(`未安排的 HTTP 请求：${path}`)
  })
  // 显式保留一个真实 CSS 过渡窗口，快速重开用例不会绕过关闭动画。
  transitionStyle = document.createElement('style')
  transitionStyle.textContent = '.el-drawer-fade-enter-active, .el-drawer-fade-leave-active { transition-property: opacity; transition-duration: 0.1s; }'
  document.head.appendChild(transitionStyle)
  wrapper = mount(LedgerListView, { attachTo: document.body, global: { plugins: [ElementPlus], stubs: { transition: false, 'transition-group': false, teleport: false } } })
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

describe('新增台账独立表单会话', () => {
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
    await click(document.querySelector<HTMLButtonElement>('[aria-label="编辑台账"]')!)
    await vi.waitFor(() => expect(input('台账编号').value).toBe('YJB-A'))
    await enter('目标地址', '')
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(drawer().textContent).toContain('请输入目标地址'))
    await closeAndWait()
    await openCreate()
    expectDefaults()
  })

  it('编辑 A 后编辑 B 显示 B 内容，再次新增不沿用编辑内容', async () => {
    await click(document.querySelectorAll<HTMLButtonElement>('[aria-label="编辑台账"]')[0]!)
    await vi.waitFor(() => expect(input('台账编号').value).toBe('YJB-A'))
    expect(input('备注').value).toBe('A 备注')
    await enter('备注', 'A 未保存内容')
    await closeAndWait()
    await click(document.querySelectorAll<HTMLButtonElement>('[aria-label="编辑台账"]')[1]!)
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
    await click(button('保存台账', drawer()))
    await click(drawer().querySelector<HTMLButtonElement>('.el-drawer__close-btn')!)
    expect(drawer().classList.contains('open')).toBe(true)
    expect(vi.mocked(fetch).mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
    finishRequest(new Response(JSON.stringify({ code: 'INTERNAL_ERROR', message: '本次保存失败', data: null, traceId: 'test-failure' }), { status: 500 }))
    await vi.waitFor(() => expect(document.body.textContent).toContain('本次保存失败'))
    expect(input('台账编号').value).toBe('YJB-NEW')
    expect(input('备注').value).toBe('本次输入')
    expect(button('取消', drawer()).disabled).toBe(false)
    saveResponse = async () => success({ ...recordA, ledgerCode: 'YJB-NEW' })
    await click(button('保存台账', drawer()))
    await vi.waitFor(() => expect(document.body.textContent).toContain('台账已创建'))
  })
})
