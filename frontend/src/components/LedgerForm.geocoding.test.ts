// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试桩模拟 Element Plus 的表单输入 */
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import LedgerForm from './LedgerForm.vue'
import { emptyLedger } from '../types/ledger'
import { ApiError } from '../api/http'

const searchAddress = vi.hoisted(() => vi.fn())
vi.mock('../api/ledger', () => ({ searchAddress }))

const Form = defineComponent({ setup(_props, { slots, expose }) { expose({ validate: async () => true }); return () => h('form', slots.default?.()) } })
const FormItem = defineComponent({ props: { label: { type: String, default: '' } }, setup(props, { slots }) { return () => h('label', [props.label, slots.default?.()]) } })
const Input = defineComponent({ props: { modelValue: { type: [String, Number], default: '' } }, emits: ['update:modelValue'], setup(props, { emit }) { return () => h('input', { value: props.modelValue, onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value) }) } })
function mountForm(destinationAddress = '厦门市思明区') {
  return mount(LedgerForm, { props: { modelValue: { ...emptyLedger(), id: '42', organizationId: '1', version: '1', createdAt: '', updatedAt: '', destinationAddress } }, global: { stubs: {
    'el-form': Form, 'el-form-item': FormItem, 'el-input': Input,
    'el-select': true, 'el-option': true, 'el-date-picker': true, 'el-input-number': true,
    MapCanvas: { props: ['selected'], emits: ['pick'], template: '<div class="test-map" :data-latitude="selected?.latitude" :data-longitude="selected?.longitude"><button type="button" class="test-pick" @click="$emit(\'pick\', \'24.4798345\', \'118.0894251\')">地图点选</button></div>' }
  } } })
}
const candidate = { displayName: '厦门市思明区人民政府', latitude: '24.4798345', longitude: '118.0894251' }
beforeEach(() => searchAddress.mockReset())

it('只在点击搜索地址后查询，并由用户选择候选点位；目标地址保持原文且未自动保存', async () => {
  searchAddress.mockResolvedValue({ results: [candidate] })
  const wrapper = mountForm()
  await wrapper.find('.destination-address input').setValue('厦门市思明区政府')
  expect(searchAddress).not.toHaveBeenCalled()
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(searchAddress).toHaveBeenCalledWith('厦门市思明区政府')
  expect(wrapper.text()).toContain(candidate.displayName)
  expect(wrapper.find('.test-map').attributes('data-latitude')).toBeUndefined()
  await wrapper.find('button.address-candidate').trigger('click')
  expect((wrapper.find('.latitude-input input').element as HTMLInputElement).value).toBe(candidate.latitude)
  expect(wrapper.find('.test-map').attributes('data-longitude')).toBe(candidate.longitude)
  expect((wrapper.find('.destination-address input').element as HTMLInputElement).value).toBe('厦门市思明区政府')
  expect(wrapper.emitted('save')).toBeUndefined()
  wrapper.unmount()
})

it('地址变更后清空旧候选并忽略迟到结果', async () => {
  let resolveSearch!: (value: { results: typeof candidate[] }) => void
  searchAddress.mockReturnValue(new Promise(resolve => { resolveSearch = resolve }))
  const wrapper = mountForm()
  await wrapper.find('button.address-search-button').trigger('click')
  await wrapper.find('.destination-address input').setValue('厦门市湖里区')
  resolveSearch({ results: [candidate] })
  await nextTick(); await nextTick()
  expect(wrapper.find('button.address-candidate').exists()).toBe(false)
  expect(wrapper.find('.test-map').attributes('data-latitude')).toBeUndefined()
  wrapper.unmount()
})

it('切换台账时忽略上一条台账尚未返回的地址候选', async () => {
  let resolveSearch!: (value: { results: typeof candidate[] }) => void
  searchAddress.mockReturnValueOnce(new Promise(resolve => { resolveSearch = resolve }))
  const wrapper = mountForm()
  await wrapper.find('button.address-search-button').trigger('click')
  await wrapper.setProps({ modelValue: {
    ...emptyLedger(), id: '99', organizationId: '1', version: '1', createdAt: '', updatedAt: '',
    destinationAddress: '厦门市湖里区', latitude: '24.5150000', longitude: '118.1470000'
  } })
  resolveSearch({ results: [candidate] })
  await nextTick(); await nextTick()
  expect(wrapper.find('button.address-candidate').exists()).toBe(false)
  expect((wrapper.find('.destination-address input').element as HTMLInputElement).value).toBe('厦门市湖里区')
  expect((wrapper.find('.latitude-input input').element as HTMLInputElement).value).toBe('24.5150000')
  expect(wrapper.find('.test-map').attributes('data-longitude')).toBe('118.1470000')
  wrapper.unmount()
})

it('空结果和服务失败给出反馈，仍可地图手动点选', async () => {
  searchAddress.mockResolvedValueOnce({ results: [] }).mockRejectedValueOnce(new Error('服务暂不可用'))
  const wrapper = mountForm()
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(wrapper.text()).toContain('未找到')
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(wrapper.text()).toContain('服务暂不可用')
  await wrapper.find('button.test-pick').trigger('click')
  expect((wrapper.find('.latitude-input input').element as HTMLInputElement).value).toBe('24.4798345')
  wrapper.unmount()
})

it('未配置高德 Web 服务 Key 时说明设置要求并保留地图手动选点', async () => {
  searchAddress.mockRejectedValueOnce(new ApiError(503, 'GEOCODE_NOT_CONFIGURED', '地址服务未配置'))
  const wrapper = mountForm()
  expect(wrapper.text()).toContain('高德地图')
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(wrapper.text()).toContain('高德 Web 服务 Key')
  expect(wrapper.text()).toContain('手动选点')
  await wrapper.find('button.test-pick').trigger('click')
  expect((wrapper.find('.longitude-input input').element as HTMLInputElement).value).toBe('118.0894251')
  wrapper.unmount()
})

it('高德服务 Key 无效时提示检查配置', async () => {
  searchAddress.mockRejectedValueOnce(new ApiError(503, 'GEOCODE_INVALID_KEY', '地址服务不可用'))
  const wrapper = mountForm()
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(wrapper.text()).toContain('检查高德 Web 服务 Key')
  wrapper.unmount()
})

it('高德账号或配额异常时提示联系管理员检查账号', async () => {
  searchAddress.mockRejectedValueOnce(new ApiError(503, 'GEOCODE_ACCOUNT_ISSUE', '地理编码服务不可用'))
  const wrapper = mountForm()
  await wrapper.find('button.address-search-button').trigger('click')
  await nextTick(); await nextTick()
  expect(wrapper.text()).toContain('联系管理员检查高德账号状态或配额')
  wrapper.unmount()
})
it('替换编辑记录后重新建立草稿基线，保存期间拒绝地图和地址搜索', async () => {
  const wrapper = mountForm()
  await wrapper.find('.destination-address input').setValue('有修改')
  expect(wrapper.emitted('dirty')?.slice(-1)[0]).toEqual([true])
  await wrapper.setProps({ modelValue: { ...emptyLedger(), id: '2', organizationId: '1', version: '0', createdAt: '', updatedAt: '', destinationAddress: '新记录地址' } })
  expect(wrapper.vm.isDirty).toBe(false)
  await wrapper.setProps({ submitting: true })
  await wrapper.find('button.test-pick').trigger('click')
  await wrapper.find('button.address-search-button').trigger('click')
  expect(searchAddress).not.toHaveBeenCalled()
  expect(wrapper.find('.test-map').attributes('data-latitude')).toBeUndefined()
  await wrapper.vm.submit()
  expect(wrapper.emitted('save')).toBeUndefined()
  wrapper.unmount()
})
