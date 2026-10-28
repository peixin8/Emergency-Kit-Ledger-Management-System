// @vitest-environment jsdom
/* eslint-disable vue/one-component-per-file -- 测试专用表单桩保留真实的组件状态和提交逻辑 */
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import LedgerForm from './LedgerForm.vue'
import { emptyLedger } from '../types/ledger'

const Form = defineComponent({
  setup(_props, { slots, expose }) {
    expose({ validate: async () => true })
    return () => h('form', slots.default?.())
  }
})
const FormItem = defineComponent({
  props: { label: { type: String, default: '' } },
  setup(props, { slots }) { return () => h('label', [props.label, slots.default?.()]) }
})
const Input = defineComponent({
  props: { modelValue: { type: [String, Number], default: '' } },
  emits: ['update:modelValue'],
  setup(props, { emit }) { return () => h('input', { value: props.modelValue, onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value) }) }
})

it('地图点选目标地址坐标后以七位小数字符串提交，并提示变更地址后复核', async () => {
  const model = { ...emptyLedger(), id: '42', organizationId: '1', version: '1', createdAt: '', updatedAt: '', destinationAddress: '厦门市思明区' }
  const wrapper = mount(LedgerForm, { props: { modelValue: model }, global: { stubs: {
    'el-form': Form, 'el-form-item': FormItem, 'el-input': Input,
    'el-select': true, 'el-option': true, 'el-date-picker': true, 'el-input-number': true,
    MapCanvas: { emits: ['pick'], template: '<button type="button" class="test-pick" @click="$emit(\'pick\', \'24.4798345\', \'118.0894251\')">选择位置</button>' }
  } } })

  await wrapper.find('button.test-pick').trigger('click')
  expect((wrapper.find('.latitude-input input').element as HTMLInputElement).value).toBe('24.4798345')
  expect((wrapper.find('.longitude-input input').element as HTMLInputElement).value).toBe('118.0894251')
  await wrapper.find('.destination-address input').setValue('厦门市湖里区')
  expect(wrapper.text()).toContain('坐标需要复核')
  await wrapper.vm.submit()
  expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({ latitude: '24.4798345', longitude: '118.0894251' })
  wrapper.unmount()
})

it('编辑时保留 PTN 端口和设备故障说明，并允许原始与目标地址相同', async () => {
  const model = { ...emptyLedger(), id: '43', organizationId: '1', version: '1', createdAt: '', updatedAt: '', originalAddress: '厦门市思明区', destinationAddress: '厦门市思明区', ptnPort: '网元 A+槽口 2', deviceConditionDetail: '开关电源故障' }
  const wrapper = mount(LedgerForm, { props: { modelValue: model }, global: { stubs: {
    'el-form': Form, 'el-form-item': FormItem, 'el-input': Input,
    'el-select': true, 'el-option': true, 'el-date-picker': true, 'el-input-number': true, MapCanvas: true
  } } })
  expect(wrapper.text()).toContain('PTN 端口（网元+槽口）')
  expect(wrapper.text()).toContain('设备状况说明')
  await wrapper.vm.submit()
  expect(wrapper.emitted('save')?.[0]?.[0]).toMatchObject({ ptnPort: '网元 A+槽口 2', deviceConditionDetail: '开关电源故障', originalAddress: '厦门市思明区', destinationAddress: '厦门市思明区' })
  wrapper.unmount()
})
