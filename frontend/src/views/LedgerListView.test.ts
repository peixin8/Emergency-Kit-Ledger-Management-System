// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试专用的表格与抽屉轻量桩组件 */
import { defineComponent, h, inject, provide, ref, toRef } from 'vue'
import type { Ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import LedgerListView from './LedgerListView.vue'
import type { Ledger } from '../types/ledger'

const listLedgers = vi.fn()
const getLedger = vi.fn()
const listLedgerMapPoints = vi.fn()
vi.mock('../api/ledger', () => ({
  listLedgers: (...args: unknown[]) => listLedgers(...args),
  getLedger: (...args: unknown[]) => getLedger(...args),
  listLedgerMapPoints: (...args: unknown[]) => listLedgerMapPoints(...args),
  createLedger: vi.fn(), updateLedger: vi.fn(), deleteLedger: vi.fn(),
  importLedgers: vi.fn(), exportLedgers: vi.fn()
}))

const ledger: Ledger = {
  id: '9007199254740993', organizationId: '1', version: '1', ledgerCode: 'XM-001',
  originalAddress: '厦门市思明区', destinationAddress: '厦门市湖里区', direction: 'IN',
  applicationMethod: 'ONLINE', applicantContact: '张三', applicantPhone: '13800000000',
  applicationDate: '2026-10-27', deviceCondition: 'GOOD', accessRoom: '机房 A',
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

beforeEach(() => {
  listLedgers.mockReset().mockResolvedValue({ records: [ledger], total: 1, page: 1, pageSize: 20 })
  getLedger.mockReset().mockResolvedValue(ledger)
  listLedgerMapPoints.mockReset().mockResolvedValue({
    records: [{ id: ledger.id, ledgerCode: ledger.ledgerCode, destinationAddress: ledger.destinationAddress, deviceCondition: ledger.deviceCondition, latitude: '24.4798345', longitude: '118.0894251' }],
    unlocatedCount: 2
  })
})

it('主表仅显示摘要，点击台账编号后显示完整详情与后续操作', async () => {
  const wrapper = mount(LedgerListView, { global: {
    directives: { loading: () => {} },
    stubs: {
      'el-table': Table, 'el-table-column': Column, 'el-drawer': Drawer,
      'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-input': true, 'el-select': true, 'el-option': true, 'el-icon': true,
      'el-pagination': true, LedgerForm: true, LedgerHistory: true,
      MapCanvas: true,
      'el-tag': { template: '<span><slot /></span>' }
    }
  } })
  await flushPromises()

  const table = wrapper.find('.test-table')
  expect(table.text()).toContain('XM-001')
  expect(table.text()).not.toContain('张三')
  expect(table.text()).not.toContain('网管 IP')
  expect(table.text()).not.toContain('10.0.0.1')
  expect(table.findAll('.test-column').length).toBeLessThanOrEqual(5)

  await table.find('button.record-link').trigger('click')
  await flushPromises()
  const detail = wrapper.find('.test-drawer')
  expect(detail.text()).toContain('台账详情')
  expect(detail.text()).toContain('张三')
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

it('厦门地图逐台展示已定位设备入口与未定位数量，点击点位列表打开完整台账', async () => {
  const wrapper = mount(LedgerListView, { global: {
    directives: { loading: () => {} },
    stubs: {
      'el-table': Table, 'el-table-column': Column, 'el-drawer': Drawer,
      'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-input': true, 'el-select': true, 'el-option': true, 'el-icon': true,
      'el-pagination': true, LedgerForm: true, LedgerHistory: true, MapCanvas: true,
      'el-tag': { template: '<span><slot /></span>' }
    }
  } })
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
