<template>
  <el-popover v-if="isLogin" :width="280" trigger="click">
    <template #reference>
      <button class="pdb-btn" :class="{ dated: !!item.startDate, late: late }" type="button">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><rect x="3.5" y="5.5" width="17" height="15" rx="2.5"/><path d="M3.5 10.5 H20.5 M8 3.5 V7 M16 3.5 V7"/></svg>
        {{ text }}
      </button>
    </template>
    <!-- 不可清空：后端 mergeEditableFields 跳过 null，清空不会落库（与 ItemEditDialog 同一约束） -->
    <el-date-picker
      :model-value="item.startDate ? item.startDate.slice(0, 10) : undefined"
      type="date"
      value-format="YYYY-MM-DD"
      placeholder="打算哪天开始"
      :clearable="false"
      style="width: 100%"
      @update:model-value="pick"
    />
  </el-popover>
  <span v-else-if="item.startDate" class="pdb-btn dated readonly" :class="{ late }">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><rect x="3.5" y="5.5" width="17" height="15" rx="2.5"/><path d="M3.5 10.5 H20.5 M8 3.5 V7 M16 3.5 V7"/></svg>
      {{ text }}
    </span>
</template>

<script setup lang="ts" name="PlanDateBadge">
import { getToken } from '@/utils/auth'
import { updateFrontItem } from '@/api/front/item'
import { planDateLabel, localDateKey } from '@/utils/plan'
import type { SgjItem } from '@/types/api/business/item'

/**
 * 计划日期角标（0.0.3 FR-003-3）：有日期显示相对文案（今天 / 周六 / 逾期 N 天 / M月D日），
 * 没有显示「定个日子」；登录可改（PUT startDate），匿名只读。
 * movie / book / travel / plan 四处卡片共用，文案口径与 /plan 页同一份 utils/plan.ts。
 */
const props = defineProps<{
  item: SgjItem
}>()

const emit = defineEmits<{
  (e: 'saved'): void
}>()

const { proxy } = getCurrentInstance() as { proxy: any }

const isLogin = computed(() => !!getToken())
const today = () => localDateKey(new Date())

const text = computed(() => props.item.startDate ? planDateLabel(props.item.startDate.slice(0, 10), today()) : '定个日子')

/** 逾期：计划日期早于今天且未完成 */
const late = computed(() => {
  if (!props.item.startDate || props.item.status === 'DONE') return false
  return props.item.startDate.slice(0, 10) < localDateKey(new Date())
})

function pick(value: string | null): void {
  if (!value || !props.item.itemId) return
  updateFrontItem(props.item.itemId, { startDate: value }).then(() => {
    proxy.$modal.msgSuccess(`计划日期：${value}`)
    emit('saved')
  }).catch(() => {})
}
</script>

<style scoped lang="scss">
/* 0.0.6 计划日期角标：纸色小胶囊，逾期转陶红；不换行，卡片一行放得下 */
.pdb-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  font-size: 13px;
  line-height: 1.7;
  white-space: nowrap;
  font-family: var(--hd-hand);
  color: var(--hd-ink-faint);
  background: var(--hd-paper);
  border: 1.4px dashed var(--hd-line-soft);
  border-radius: 8px 3px 7px 3px / 3px 7px 3px 8px;
  cursor: pointer;

  svg { width: 13px; height: 13px; flex: none }

  &.dated { color: var(--hd-ink-soft); border-style: solid }
  &.late { color: var(--hd-red); border-color: var(--hd-red-soft) }
  &.readonly { cursor: default }
  &:hover:not(.readonly) { color: var(--hd-red); border-color: var(--hd-red) }
}
</style>
