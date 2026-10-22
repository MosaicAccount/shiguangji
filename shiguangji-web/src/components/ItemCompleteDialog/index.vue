<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="520px"
    append-to-body
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <el-form :model="form" :label-width="config.rating ? '80px' : '90px'">
      <el-form-item :label="config.dateLabel">
        <!-- 0.0.3 FR-003-7：日期默认今天但可改——补记昨天看的，不该被迫接受今天 -->
        <el-date-picker
          v-model="form.finishDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item v-if="config.rating" label="评分">
        <el-rate v-model="form.rating" :max="10" show-score />
      </el-form-item>
      <el-form-item :label="config.commentLabel">
        <el-input v-model="form.comment" type="textarea" :rows="3" :placeholder="config.commentPlaceholder" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">记下这一刻</el-button>
      <el-button @click="close">取消</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="ItemCompleteDialog">
import { completeFrontItem } from '@/api/front/item'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 统一完成弹窗（0.0.3 FR-003-7）：movie / book / travel / home 四处共用一份，
 * 替换原先三份逐字重复的页内弹窗。分类型文案见 LABELS；地点无评分（给一个地方
 * 打几分说不通）。提交仍走 completeFrontItem（POST /app/item/{id}/complete），
 * 成功后 emitted('submitted') 由调用方给提示并刷新——文案与刷新策略留在页面侧。
 */

const props = defineProps<{
  modelValue: boolean
  /** 待完成的条目（null 时弹窗不可提交） */
  item: SgjItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submitted'): void
}>()

interface CompleteLabels {
  /** 标题动词，如「标记看过」 */
  verb: string
  dateLabel: string
  commentLabel: string
  commentPlaceholder: string
  /** 地点不显示评分 */
  rating: boolean
}

/** 与抽取前各页弹窗的文案逐字一致（回归基线即设计 §5.4 这张表） */
const LABELS: Record<string, CompleteLabels> = {
  MOVIE: { verb: '标记看过', dateLabel: '看完日期', commentLabel: '短评', commentPlaceholder: '写点短评吧（可选）', rating: true },
  TV: { verb: '标记看过', dateLabel: '看完日期', commentLabel: '短评', commentPlaceholder: '写点短评吧（可选）', rating: true },
  BOOK: { verb: '标记已读', dateLabel: '读完日期', commentLabel: '读后感', commentPlaceholder: '写点读后感吧（可选）', rating: true },
  PLACE: { verb: '标记去过', dateLabel: '去过日期', commentLabel: '回忆', commentPlaceholder: '写点回忆吧（可选）', rating: false }
}

const config = computed<CompleteLabels>(() => LABELS[props.item?.itemType || ''] || LABELS.MOVIE)

const dialogTitle = computed(() => `${config.value.verb}：${props.item?.title || ''}`)

const form = reactive({
  finishDate: undefined as string | undefined,
  rating: undefined as number | undefined,
  comment: undefined as string | undefined
})

const submitting = ref(false)

/** 本地今天的 YYYY-MM-DD（补记场景的默认值，用户可改） */
function localToday(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

watch(() => props.modelValue, open => {
  if (open) {
    form.finishDate = localToday()
    form.rating = undefined
    form.comment = undefined
  }
})

function close(): void {
  emit('update:modelValue', false)
}

async function submit(): Promise<void> {
  if (!props.item?.itemId || submitting.value) return
  submitting.value = true
  try {
    await completeFrontItem(props.item.itemId, {
      finishDate: form.finishDate,
      rating: form.rating,
      comment: form.comment
    })
    close()
    emit('submitted')
  } catch {
    // 失败提示由请求层统一弹出，这里保持与原页内实现一致的静默
  } finally {
    submitting.value = false
  }
}
</script>
