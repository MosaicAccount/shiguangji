<template>
  <el-dialog
    :model-value="modelValue"
    title="地图选点"
    width="640px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="picker-container">
      <div v-if="error" class="picker-error">{{ error }}</div>
      <div v-show="!error" ref="chartRef" class="picker-chart"></div>
    </div>
    <template #footer>
      <div class="picker-footer">
        <span class="picker-coord">
          {{ lat != null && lng != null ? `已选：纬度 ${lat}，经度 ${lng}` : '点击地图选择地点' }}
        </span>
        <el-button type="primary" :disabled="lat == null || lng == null" @click="confirmPick">确认选择</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="MapPicker">
import * as echarts from 'echarts'
import { loadChinaMap, toCoord } from '@/utils/map'

const props = defineProps<{
  modelValue: boolean
  /** 打开时回显的纬度（可选） */
  latitude?: number | null
  /** 打开时回显的经度（可选） */
  longitude?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', latitude: number, longitude: number): void
}>()

const loading = ref(false)
const error = ref('')
const chartRef = ref<HTMLElement | null>(null)
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)
let chart: echarts.ECharts | null = null

/** 读取主题 CSS 变量（供 ECharts 取当前亮/暗色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

watch(
  () => props.modelValue,
  value => {
    if (value) {
      lat.value = props.latitude ?? null
      lng.value = props.longitude ?? null
      init()
    } else {
      chart?.dispose()
      chart = null
    }
  }
)

async function init(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const loaded = await loadChinaMap()
    if (!loaded) {
      error.value = '地图加载失败，请检查网络或稍后重试，也可以手动输入经纬度'
      return
    }
    await nextTick()
    render()
  } finally {
    loading.value = false
  }
}

function render(): void {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
    chart.getZr().on('click', onMapClick)
  }
  const selectedData = lat.value != null && lng.value != null
    ? [{ name: '已选', value: [lng.value, lat.value] }]
    : []
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'scatter' && params.value) {
          return `纬度：${params.value[1]}<br/>经度：${params.value[0]}`
        }
        return params.name || ''
      }
    },
    geo: {
      map: 'china',
      roam: true,
      zoom: 1.2,
      scaleLimit: { min: 1, max: 10 },
      label: { show: false },
      itemStyle: {
        areaColor: themeColor('--sgj-primary-soft'),
        borderColor: themeColor('--sgj-primary')
      },
      emphasis: {
        itemStyle: { areaColor: themeColor('--sgj-primary-soft') }
      }
    },
    series: [
      {
        name: '选点',
        type: 'scatter',
        coordinateSystem: 'geo',
        data: selectedData,
        symbolSize: 12,
        itemStyle: { color: themeColor('--sgj-primary') },
        label: {
          show: true,
          position: 'top',
          formatter: '{b}',
          color: themeColor('--sgj-primary'),
          fontSize: 12
        }
      }
    ]
  })
}

/** zrender 层点击 + convertFromPixel 拾取经纬度（geo 区域点击事件本身不带坐标） */
function onMapClick(e: any): void {
  if (!chart) return
  const pixel = [e.offsetX, e.offsetY]
  if (!chart.containPixel('geo', pixel)) return
  const coord = toCoord(chart.convertFromPixel('geo', pixel))
  if (!coord) return
  // 表单经纬度精度统一 6 位
  lat.value = Number(coord.lat.toFixed(6))
  lng.value = Number(coord.lng.toFixed(6))
  render()
}

function confirmPick(): void {
  if (lat.value == null || lng.value == null) return
  emit('confirm', lat.value, lng.value)
  emit('update:modelValue', false)
}

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<style scoped lang="scss">
.picker-container {
  height: 420px;
  background: var(--sgj-bg-card, #fff);
  border-radius: 12px;
  overflow: hidden;
  position: relative;

  .picker-chart {
    width: 100%;
    height: 100%;
  }

  .picker-error {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--sgj-text-4, #999);
  }
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .picker-coord {
    font-size: 13px;
    color: var(--sgj-text-2, #666);
  }
}
</style>
