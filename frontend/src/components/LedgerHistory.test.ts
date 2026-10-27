// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import LedgerHistory from './LedgerHistory.vue'
import type { Ledger, LedgerHistoryPage } from '../types/ledger'

const listLedgerHistory = vi.fn()
vi.mock('../api/ledger', () => ({ listLedgerHistory: (...args: unknown[]) => listLedgerHistory(...args) }))

const ledger: Ledger = {
  id: '42', organizationId: '1', version: '1', ledgerCode: 'XM-001', originalAddress: '厦门市思明区',
  destinationAddress: '厦门市湖里区', direction: 'IN', applicationMethod: 'ONLINE', applicantContact: '张三',
  applicantPhone: '13800000000', applicationDate: '2026-10-27', deviceCondition: 'GOOD', accessRoom: '机房 A',
  fiberCore: '1', networkStandard: '5G', bbuModel: 'BBU-A', rruModel: 'RRU-A', bbuRruPowerWatts: '100',
  managementIp: '10.0.0.1', managementVlan: 101, businessIp: '10.1.0.1', businessVlan: 201,
  deviceSerialNo: 'SN-001', assetOwner: '厦门', remarks: '创建时备注', createdAt: '2026-10-27T08:00:00Z',
  updatedAt: '2026-10-27T08:00:00Z'
}

beforeEach(() => {
  listLedgerHistory.mockReset()
  const updated = { ...ledger, version: '2', remarks: '修改后备注' }
  const page: LedgerHistoryPage = {
    records: [
      { id: '3', ledgerId: '42', action: 'DELETE', occurredAt: '2026-10-27T10:00:00Z', before: updated, after: null },
      { id: '2', ledgerId: '42', action: 'UPDATE', occurredAt: '2026-10-27T09:00:00Z', before: ledger, after: updated },
      { id: '1', ledgerId: '42', action: 'CREATE', occurredAt: '2026-10-27T08:00:00Z', before: null, after: ledger }
    ], total: 3, page: 1, pageSize: 10
  }
  listLedgerHistory.mockResolvedValue(page)
})

it('每条历史显示简要摘要，点击后才展示该次完整只读快照', async () => {
  const wrapper = mount(LedgerHistory, {
    props: { ledgerId: '42' },
    global: { directives: { loading: () => {} }, stubs: {
      'el-timeline': { template: '<div><slot /></div>' },
      'el-timeline-item': { template: '<div><slot /></div>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-button': { template: '<button><slot /></button>' },
      'el-pagination': true,
      'el-empty': true
    } }
  })
  await flushPromises()

  const entries = wrapper.findAll('button.history-entry')
  expect(entries).toHaveLength(3)
  expect(wrapper.text()).not.toContain('修改后备注')
  expect(wrapper.text()).not.toContain('网管 IP')
  expect(wrapper.text()).toContain('删除台账')
  expect(wrapper.text()).toContain('编辑修改')
  expect(wrapper.text()).toContain('创建台账')

  await entries[1]!.trigger('click')
  expect(wrapper.text()).toContain('修改前')
  expect(wrapper.text()).toContain('修改后')
  expect(wrapper.text()).toContain('修改后备注')
  expect(wrapper.text()).toContain('网管 IP')
  expect(wrapper.find('input').exists()).toBe(false)

  await entries[0]!.trigger('click')
  expect(wrapper.text()).toContain('删除前')
  expect(wrapper.findAll('.history-snapshot h4').map(heading => heading.text())).toEqual(['删除前', '流转申请', '设备信息', '网络参数', '记录信息'])
  wrapper.unmount()
})
