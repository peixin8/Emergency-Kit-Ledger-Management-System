import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import ImportPreview from './ImportPreview.vue'
import { ApiError } from '../api/http'
const preview = vi.fn()
const confirm = vi.fn()
vi.mock('../api/ledger', () => ({ previewLedgerImport: (...args: unknown[]) => preview(...args), confirmLedgerImport: (...args: unknown[]) => confirm(...args) }))
const result = { fileHash: 'a'.repeat(64), previewFingerprint: 'b'.repeat(64), importedCount: 1, createdCount: 0, updatedCount: 1, missingOptionalColumns: ['备注'], rows: [{ line: 2, ledgerCode: 'A', action: 'UPDATE', targetId: '1', targetVersion: '2', changedFields: ['ptnPort'], clearedFields: ['remarks'] }] }
const file = new File(['bytes'], '台账.xlsx')
function setup() { return mount(ImportPreview, { props: { file }, global: { stubs: { 'el-button': { template: '<button><slot /></button>' } } } }) }
beforeEach(() => { preview.mockReset().mockResolvedValue(result); confirm.mockReset().mockResolvedValue({ importedCount: 1, createdCount: 0, updatedCount: 1 }) })
it('预览显示变化和清空中文字段，取消不写入', async () => {
  const wrapper = setup(); await flushPromises()
  expect(wrapper.text()).toContain('台账.xlsx'); expect(wrapper.text()).toContain('PTN 端口'); expect(wrapper.text()).toContain('清空：备注')
  await wrapper.findAll('button').find(item => item.text() === '取消')!.trigger('click')
  expect(confirm).not.toHaveBeenCalled(); expect(wrapper.emitted('cancel')).toHaveLength(1)
})
it('确认传回原始文件和预览指纹，过期不能自动重试确认', async () => {
  confirm.mockRejectedValue(new ApiError(409, 'IMPORT_PREVIEW_STALE', '预览已过期'))
  const wrapper = setup(); await flushPromises()
  await wrapper.findAll('button').find(item => item.text() === '确认导入')!.trigger('click'); await flushPromises()
  expect(confirm).toHaveBeenCalledWith(file, result.previewFingerprint)
  expect(wrapper.text()).toContain('重新预览'); expect(confirm).toHaveBeenCalledTimes(1)
  expect(wrapper.findAll('button').some(item => item.text() === '确认导入')).toBe(false)
})
