<template>
  <el-popover v-if="isLogin" :width="280" trigger="click">
    <template #reference>
      <button class="pdb-btn" :class="{ dated: !!item.startDate }" type="button">📅 {{ text }}</button>
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
  <span v-else-if="item.startDate" class="pdb-btn dated readonly">📅 {{ text }}</span>
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

function pick(value: string | null): void {
  if (!value || !props.item.itemId) return
  updateFrontItem(props.item.itemId, { startDate: value }).then(() => {
    proxy.$modal.msgSuccess(`计划日期：${value}`)
    emit('saved')
  }).catch(() => {})
}
</script>

<style scoped lang="scss">
.pdb-btn {
  border: 0;
  background: none;
  padding: 0;
  font-size: 12.5px;
  color: var(--sgj-text-4);
  cursor: pointer;
  font-family: inherit;

  &.dated {
    color: var(--sgj-text-3);
  }

  &.readonly {
    cursor: default;
  }

  &:hover.dated {
    color: var(--sgj-primary);
  }
}
</style>
