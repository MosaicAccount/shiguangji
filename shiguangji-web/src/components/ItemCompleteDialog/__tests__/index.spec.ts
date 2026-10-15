import { describe, it, expect, vi, beforeEach } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { ElButton, ElInput } from 'element-plus'
import ItemCompleteDialog from '../index.vue'
import { completeFrontItem } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 统一完成弹窗（0.0.3 FR-003-7）：
 * T-82 分类型形态（影视/书籍/地点的文案差异、地点无评分、日期默认今天）
 * T-83 提交回传用户改过的日期（补记场景不再被静默写成当天）
 */

vi.mock('@/api/front/item', () => ({
  completeFrontItem: vi.fn().mockResolvedValue({ code: 200 })
}))

const mockedComplete = vi.mocked(completeFrontItem)

/** el-dialog 桩：透传标题并渲染默认/底部插槽，捕获 update:modelValue */
const ElDialogStub = defineComponent({
  props: { modelValue: Boolean, title: String },
  emits: ['update:modelValue'],
  template: `<div class="dlg"><h3 class="dlg-title">{{ title }}</h3><slot /><slot name="footer" /></div>`
})

const ElFormStub = defineComponent({ template: '<form><slot /></form>' })

/** el-form-item 桩：把 label 渲染出来供断言 */
const ElFormItemStub = defineComponent({
  props: { label: String },
  template: '<div class="fi"><label>{{ label }}</label><slot /></div>'
})

/** el-date-picker 桩：受控 input，改值走 update:modelValue（与真组件的 v-model 契约一致） */
const ElDatePickerStub = defineComponent({
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  template: `<input class="date-input" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)" />`
})

const ElRateStub = defineComponent({
  props: { modelValue: { type: Number, default: 0 } },
  template: '<div class="rate-stub">{{ modelValue }}</div>'
})

function today(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** 与真实使用同序：先以关闭态挂载，再打开（打开瞬间重置表单、日期默认今天） */
async function mountDialog(item: Partial<SgjItem>) {
  const wrapper = mount(ItemCompleteDialog, {
    props: { modelValue: false, item: item as SgjItem },
    global: {
      components: {
        ElDialog: ElDialogStub,
        ElForm: ElFormStub,
        ElFormItem: ElFormItemStub,
        ElDatePicker: ElDatePickerStub,
        ElRate: ElRateStub,
        ElButton,
        ElInput
      }
    }
  })
  await wrapper.setProps({ modelValue: true })
  return wrapper
}

function labels(wrapper: VueWrapper): string[] {
  return wrapper.findAll('label').map(l => l.text())
}

beforeEach(() => {
  mockedComplete.mockClear()
})

describe('T-82 分类型形态', () => {
  it('影视：标记看过 + 看完日期/评分/短评，日期默认今天', async () => {
    const wrapper = await mountDialog({ itemId: 1, itemType: 'MOVIE', title: '肖申克的救赎' })
    expect(wrapper.find('.dlg-title').text()).toBe('标记看过：肖申克的救赎')
    expect(labels(wrapper)).toEqual(['看完日期', '评分', '短评'])
    expect(wrapper.find('textarea').attributes('placeholder')).toBe('写点短评吧（可选）')
    expect((wrapper.find('.date-input').element as HTMLInputElement).value).toBe(today())
    wrapper.unmount()
  })

  it('书籍：标记已读 + 读完日期/评分/读后感', async () => {
    const wrapper = await mountDialog({ itemId: 2, itemType: 'BOOK', title: '置身事内' })
    expect(wrapper.find('.dlg-title').text()).toBe('标记已读：置身事内')
    expect(labels(wrapper)).toEqual(['读完日期', '评分', '读后感'])
    expect(wrapper.find('textarea').attributes('placeholder')).toBe('写点读后感吧（可选）')
    wrapper.unmount()
  })

  it('地点：标记去过 + 去过日期/回忆，无评分行', async () => {
    const wrapper = await mountDialog({ itemId: 3, itemType: 'PLACE', title: '京都 · 岚山竹林' })
    expect(wrapper.find('.dlg-title').text()).toBe('标记去过：京都 · 岚山竹林')
    expect(labels(wrapper)).toEqual(['去过日期', '回忆'])
    expect(wrapper.find('textarea').attributes('placeholder')).toBe('写点回忆吧（可选）')
    wrapper.unmount()
  })
})

describe('T-83 提交回传用户改的日期', () => {
  it('补记 2026-10-01：completeFrontItem 收到改过的 finishDate，并发出 submitted', async () => {
    const wrapper = await mountDialog({ itemId: 1, itemType: 'MOVIE', title: '肖申克的救赎' })

    await wrapper.find('.date-input').setValue('2026-10-01')
    await wrapper.findAll('button').find(b => b.text().includes('确 定'))!.trigger('click')
    await flushPromises()

    expect(mockedComplete).toHaveBeenCalledTimes(1)
    expect(mockedComplete).toHaveBeenCalledWith(1, {
      finishDate: '2026-10-01',
      rating: undefined,
      comment: undefined
    })
    expect(wrapper.emitted('submitted')).toHaveLength(1)
    // 关闭走 update:modelValue，由调用方收口
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
    wrapper.unmount()
  })

  it('item 为 null 时点确定不发起请求', async () => {
    const wrapper = await mountDialog({})
    await wrapper.findAll('button').find(b => b.text().includes('确 定'))!.trigger('click')
    await flushPromises()
    expect(mockedComplete).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
