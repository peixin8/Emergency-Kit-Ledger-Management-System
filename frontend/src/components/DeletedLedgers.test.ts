import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import ElementPlus, { ElDatePicker, ElInput, ElPagination } from 'element-plus'
import DeletedLedgers from './DeletedLedgers.vue'
const listDeletedLedgers = vi.fn()
vi.mock('../api/ledger', () => ({ listDeletedLedgers: (...args: unknown[]) => listDeletedLedgers(...args) }))
beforeEach(() => {
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  listDeletedLedgers.mockReset().mockResolvedValue({ records: [{ id: '9007199254740993', ledgerCode: '已删除-A', originalAddress: '原址', destinationAddress: '目标址', deletedAt: '2026-10-28T12:00:00+08:00' }], total: 21, page: 1, pageSize: 20 })
})
afterEach(() => vi.unstubAllGlobals())
it('按编号地址和上海日期分页检索已删除记录，点击历史保留字符串ID', async () => {
  const wrapper = mount(DeletedLedgers, { global: { plugins: [ElementPlus] } }); await flushPromises()
  wrapper.findComponent(ElInput).vm.$emit('update:modelValue', '地址')
  const dates = wrapper.findAllComponents(ElDatePicker)
  dates[0]!.vm.$emit('update:modelValue', '2026-10-01'); dates[1]!.vm.$emit('update:modelValue', '2026-10-28')
  await wrapper.findAll('button').find(button => button.text() === '查询')!.trigger('click'); await flushPromises()
  expect(listDeletedLedgers).toHaveBeenLastCalledWith({ keyword: '地址', deletedFrom: '2026-10-01', deletedTo: '2026-10-28', page: 1, pageSize: 20 }, expect.any(AbortSignal))
  await wrapper.findAll('button').find(button => button.text() === '查看历史')!.trigger('click')
  expect(wrapper.emitted('history')).toEqual([['9007199254740993']])
  wrapper.findComponent(ElPagination).vm.$emit('update:current-page', 2)
  wrapper.findComponent(ElPagination).vm.$emit('current-change', 2); await flushPromises()
  expect(listDeletedLedgers).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }), expect.any(AbortSignal))
  expect(wrapper.text()).not.toContain('恢复')
  wrapper.unmount()
})
it('逆序日期直接提示且不请求服务端', async () => {
  const wrapper = mount(DeletedLedgers, { global: { plugins: [ElementPlus] } }); await flushPromises()
  const dates = wrapper.findAllComponents(ElDatePicker)
  dates[0]!.vm.$emit('update:modelValue', '2026-10-28'); dates[1]!.vm.$emit('update:modelValue', '2026-10-01')
  await wrapper.findAll('button').find(button => button.text() === '查询')!.trigger('click')
  expect(wrapper.text()).toContain('删除开始日期不能晚于结束日期')
  expect(listDeletedLedgers).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})
