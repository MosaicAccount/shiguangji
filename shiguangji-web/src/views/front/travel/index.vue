<template>
  <div class="travel-page">
    <div class="page-banner anim">
      <div class="banner-blob" aria-hidden="true"></div>
      <div class="banner-blob2" aria-hidden="true"></div>
      <div class="banner-text">
        <h1>足迹</h1>
        <p>记录你去过和想去的地方。</p>
      </div>
      <el-button v-if="isLogin" type="primary" class="banner-add" @click="openAdd">＋ 添加想去</el-button>
    </div>

    <div class="filter-bar">
      <div class="filter-group">
        <button class="filter-pill" :class="{ active: mode === 'card' }" @click="switchMode('card')">卡片模式</button>
        <button class="filter-pill" :class="{ active: mode === 'map' }" @click="switchMode('map')">路线图模式</button>
      </div>
      <div v-if="isLogin && mode === 'card'" class="filter-group">
        <button
          v-for="(s, si) in sgj_place_status"
          :key="s.value"
          class="filter-pill"
          :class="{ active: activeStatus === s.value }"
          @click="switchStatus(s.value)"
        >{{ s.label }}</button>
      </div>
      <el-input
        v-if="mode === 'card'"
        v-model="searchTitle"
        placeholder="搜索地点"
        clearable
        class="search-input"
        @keyup.enter="loadData"
        @clear="loadData"
      >
        <template #prefix><span class="search-icon">⌕</span></template>
        <template #append>
          <el-button @click="loadData">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- 标签筛选：豆瓣式标签行（PLACE 模块，仅卡片模式），点击选中、再点取消 -->
    <tag-pills v-if="mode === 'card'" v-model="searchTag" module="PLACE" @update:model-value="loadData" />

    <!-- 卡片模式 -->
    <template v-if="mode === 'card'">
      <div v-loading="loading" class="card-grid">
        <el-empty v-if="!loading && !list.length && !loadError" description="暂无数据" />
        <div v-if="loadError" class="load-error">
          <span>加载失败，请稍后重试</span>
          <el-button size="small" round @click="loadData">重试</el-button>
        </div>
        <div v-for="(item, idx) in list" :key="item.itemId" class="place-card anim" :style="{ '--d': ((idx % 4) * 30) + 'ms' }" @click="openDetail(item)">
          <div class="card-cover" :style="coverStyle(idx)">
            <!-- ：封面加载失败兜底（隐藏 img 显示占位图标） -->
            <img v-if="item.coverUrl && !isCoverError(item)" :src="item.coverUrl" class="card-cover-img" :alt="item.title" loading="lazy" @error="onCoverError(item)" />
            <template v-else>
              <span class="card-glyph">地</span>
              <span class="card-type-pill">地点</span>
            </template>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div v-if="item.city || item.country" class="card-location">
              {{ [item.city, item.country].filter(Boolean).join(' · ') }}
            </div>
            <div v-if="item.bestSeason" class="card-season">{{ item.bestSeason }}</div>
            <div v-if="item.comment" class="card-comment">{{ item.comment }}</div>
          </div>
          <!-- ：整卡点击已开详情，操作区只留高频操作（去重「查看详情」按钮） -->
          <div class="card-actions" @click.stop>
            <el-button v-if="item.status === 'WANT' && isLogin" type="primary" size="small" round @click="openComplete(item)">
              标记去过
            </el-button>
            <el-button v-if="item.status === 'DONE' && isLogin" type="warning" size="small" round @click="handleUncomplete(item)">
              取消完成
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="hasMore" class="load-more-wrap">
        <el-button :loading="loadingMore" round @click="loadMore">加载更多</el-button>
      </div>
    </template>

    <!-- 路线图模式 -->
    <div v-else class="map-mode">
      <div class="map-toolbar">
        <el-select v-model="mapLimit" style="width: 160px" @change="initMapChart">
          <el-option label="最近20个足迹" :value="20" />
          <el-option label="最近50个足迹" :value="50" />
          <el-option label="全部足迹" :value="0" />
        </el-select>
        <span class="map-tip">绿色为去过地点，按时间顺序连线</span>
      </div>
      <div v-loading="mapLoading" class="map-container">
        <div v-if="mapError" class="map-error">{{ mapError }}</div>
        <div v-else ref="mapRef" class="map-chart"></div>
      </div>
      <div class="map-legend">
        <span><i class="legend-dot visited"></i> 去过</span>
        <span><i class="legend-dot wish"></i> 想去</span>
        <span class="legend-line">—— 旅行轨迹</span>
      </div>
    </div>

    <!-- 添加想去 -->
    <el-dialog v-model="addOpen" title="添加想去" width="560px" append-to-body>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="地点名称" prop="title">
          <el-input v-model="addForm.title" placeholder="请输入地点名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="addForm.address" placeholder="详细地址（可选）" maxlength="300" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="addForm.city" placeholder="城市（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item label="国家">
          <el-input v-model="addForm.country" placeholder="国家（可选）" maxlength="100" />
        </el-form-item>
        <el-form-item label="经纬度">
          <div class="coord-row">
            <el-input-number
              v-model="addForm.latitude"
              :min="-90"
              :max="90"
              :precision="6"
              :controls="false"
              placeholder="纬度"
              style="width: 130px"
            />
            <el-input-number
              v-model="addForm.longitude"
              :min="-180"
              :max="180"
              :precision="6"
              :controls="false"
              placeholder="经度"
              style="width: 130px"
            />
            <el-button type="primary" plain size="small" @click="openMapPicker">
              🗺 地图选点
            </el-button>
          </div>
          <div class="coord-tip">填写经纬度或地图选点，填写后地点才会出现在旅行地图中</div>
        </el-form-item>
                  <el-form-item label="最佳季节">
            <el-select v-model="addForm.bestSeason" placeholder="选择季节（可选）" clearable style="width: 100%">
              <el-option v-for="s in sgj_best_season" :key="s.value" :label="s.label" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="addForm.placeCategory" placeholder="选择或输入分类（可选）" clearable filterable allow-create style="width: 100%">
              <el-option v-for="c in sgj_place_category" :key="c.value" :label="c.label" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="标签">
            <!-- 标签来自后台标签管理（PLACE 模块），禁止自由输入 -->
            <tag-select v-model="addForm.tags" module="PLACE" placeholder="选择标签（可选）" />
          </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 地图选点 -->
    <map-picker v-model="pickerOpen" :latitude="addForm.latitude" :longitude="addForm.longitude" @confirm="confirmPick" />

    <!-- 标记去过 -->
    <el-dialog v-model="completeOpen" :title="'标记去过：' + (currentItem?.title || '')" width="520px" append-to-body>
      <el-form ref="completeFormRef" :model="completeForm" label-width="90px">
        <el-form-item label="去过日期">
          <el-date-picker v-model="completeForm.finishDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="回忆">
          <el-input v-model="completeForm.comment" type="textarea" :rows="3" placeholder="写点回忆吧（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="submitComplete">确 定</el-button>
        <el-button @click="completeOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailOpen" :title="detail?.title || '详情'" size="420px">
      <div v-if="detail" class="detail-content">
        <div class="detail-cover">
          <img v-if="detail.coverUrl && !isCoverError(detail)" :src="detail.coverUrl" class="detail-cover-img" :alt="detail.title" @error="onCoverError(detail)" />
          <div v-else class="detail-icon">地</div>
        </div>
        <!-- 6.2 详情抽屉头部 meta 行：类型图标 + 状态 tag -->
        <div class="detail-meta">
          <span class="detail-type-icon">📍</span>
          <el-tag :type="detail.status === 'DONE' ? 'success' : 'warning'" size="small">
            {{ detail.status === 'DONE' ? '去过' : '想去' }}
          </el-tag>
        </div>
        <h2>{{ detail.title }}</h2>
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="地址">{{ detail.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="城市">{{ detail.city || '-' }}</el-descriptions-item>
          <el-descriptions-item label="省/州">{{ detail.province || '-' }}</el-descriptions-item>
          <el-descriptions-item label="国家">{{ detail.country || '-' }}</el-descriptions-item>
          <el-descriptions-item label="经纬度">
            {{ detail.latitude != null && detail.longitude != null
              ? detail.latitude + ', ' + detail.longitude
              : '-' }}
          </el-descriptions-item>
                    <el-descriptions-item label="最佳季节">{{ selectDictLabel(sgj_best_season, detail.bestSeason) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ selectDictLabel(sgj_place_category, detail.placeCategory) || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.comment" class="detail-comment">
          <h3>我的回忆</h3>
          <p>{{ detail.comment }}</p>
        </div>
        <item-notes :item-id="detail.itemId" />
        <div v-if="isLogin" class="detail-actions">
          <el-button type="primary" round @click="openEditDetail">编辑</el-button>
          <el-button type="danger" round @click="handleDelete(detail)">删除</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 编辑条目 -->
    <item-edit-dialog
      v-model="editOpen"
      :item-id="editingId"
      @saved="handleEdited"
    />
  </div>
</template>

<script setup lang="ts" name="FrontTravel">
import * as echarts from 'echarts'
import { getToken } from '@/utils/auth'
import { useDict } from '@/utils/dict'
import ItemEditDialog from '@/components/ItemEditDialog/index.vue'
import ItemNotes from '@/components/ItemNotes/index.vue'
import MapPicker from '@/components/MapPicker/index.vue'
import TagSelect from '@/components/TagSelect/index.vue'
import TagPills from '@/components/TagPills/index.vue'
import { loadChinaMap } from '@/utils/map'
import { listFrontItem, getFrontItem, addFrontItem, completeFrontItem, delFrontItem, uncompleteFrontItem } from '@/api/front/item'
import { getTravelTrajectory } from '@/api/front/travel'
import { selectDictLabel } from '@/utils/sgj'
import type { SgjItem } from '@/types/api/business/item'
import type { TravelPoint, TravelTrajectory } from '@/types/api/front/travel'

const { sgj_place_status, sgj_place_category, sgj_best_season } = useDict('sgj_place_status', 'sgj_place_category', 'sgj_best_season')

const { proxy } = getCurrentInstance() as { proxy: any }
const router = useRouter()
const route = useRoute()

/** 是否登录（访客只读，登录后可管理） */
const isLogin = computed(() => !!getToken())

const mode = ref<'card' | 'map'>('card')
/** 访客默认只看已完成内容 */
const activeStatus = ref<'WANT' | 'DONE'>(isLogin.value ? 'WANT' : 'DONE')
const searchTitle = ref('')
/** 标签筛选（选择即查询，仅卡片模式） */
const searchTag = ref('')
const list = ref<SgjItem[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref(false)
/** 分页 */
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
const hasMore = computed(() => list.value.length < total.value)

const mapRef = ref<HTMLElement | null>(null)
const mapLoading = ref(false)
const mapError = ref('')
const mapLimit = ref<number>(50)
const trajectory = ref<TravelTrajectory | null>(null)
let mapChart: any = null

const addOpen = ref(false)
const completeOpen = ref(false)
const detailOpen = ref(false)
const editOpen = ref(false)
const editingId = ref<number>()
const currentItem = ref<SgjItem | null>(null)
const detail = ref<SgjItem | null>(null)

const pickerOpen = ref(false)

const addForm = reactive({
  title: undefined as string | undefined,
  address: undefined as string | undefined,
  city: undefined as string | undefined,
  country: undefined as string | undefined,
  latitude: undefined as number | undefined,
  longitude: undefined as number | undefined,
  bestSeason: undefined as string | undefined,
  placeCategory: undefined as string | undefined,
  tags: undefined as string | undefined
})

/** ：封面加载失败兜底——记录失败的 itemId，模板隐藏 img 显示占位图标 */
const coverErrorIds = ref<Set<number>>(new Set())
function onCoverError(item: SgjItem): void {
  if (item.itemId != null) {
    coverErrorIds.value.add(item.itemId)
  }
}
function isCoverError(item: SgjItem): boolean {
  return item.itemId != null && coverErrorIds.value.has(item.itemId)
}

/** 状态胶囊切换 */
function switchStatus(status: 'WANT' | 'DONE'): void {
  if (activeStatus.value === status) return
  activeStatus.value = status
  loadData()
}

/** 模式胶囊切换（原 el-radio-group 逻辑） */
function switchMode(next: 'card' | 'map'): void {
  if (mode.value === next) return
  mode.value = next
  handleModeChange()
}

/** 卡片封面占位色块：按索引轮换灰陶浅/鼠尾草浅/灰铜浅/中性 */
function coverStyle(index: number): Record<string, string> {
  const soft = ['var(--sgj-primary-soft)', 'var(--sgj-moss-soft)', 'var(--sgj-amber-soft)', 'var(--sgj-cover)']
  const ink = ['var(--sgj-primary-dark)', 'var(--sgj-moss)', 'var(--sgj-amber)', 'var(--sgj-text-2)']
  const i = index % soft.length
  return { background: soft[i], color: ink[i] }
}

/** 读取主题 CSS 变量（供 ECharts 取当前亮/暗色） */
function themeColor(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#A85F52'
}

const completeForm = reactive({
  finishDate: undefined,
  comment: undefined
})

const addRules = {
  title: [{ required: true, message: '请输入地点名称', trigger: 'blur' }]
}

function loadData(): Promise<void> {
  loading.value = true
  loadError.value = false
  pageNum.value = 1
  return listFrontItem({
    itemType: 'PLACE',
    status: activeStatus.value,
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize
  }).then(response => {
    list.value = response.data || []
    total.value = (response as any).total || 0
  }).catch(() => {
    loadError.value = true
  }).finally(() => {
    loading.value = false
  })
}

/** 加载更多 */
function loadMore(): void {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  pageNum.value += 1
  listFrontItem({
    itemType: 'PLACE',
    status: activeStatus.value,
    title: searchTitle.value || undefined,
    tags: searchTag.value || undefined,
    pageNum: pageNum.value,
    pageSize: pageSize
  }).then(response => {
    list.value = list.value.concat(response.data || [])
    total.value = (response as any).total || 0
  }).catch(() => {
    pageNum.value -= 1
  }).finally(() => {
    loadingMore.value = false
  })
}

async function handleModeChange(): Promise<void> {
  if (mode.value === 'map') {
    await loadMapData()
  }
}

async function loadMapData(): Promise<void> {
  mapLoading.value = true
  mapError.value = ''
  try {
    const response = await getTravelTrajectory()
    trajectory.value = response.data || null
    // 轨迹接口不含短评/评分，异步按 itemId 拉取详情缓存供 tooltip 使用（失败静默降级）
    preloadPointDetails()
    await nextTick()
    const loaded = await loadChinaMap()
    if (!loaded) {
      mapError.value = '中国地图加载失败，请检查网络或稍后重试'
      return
    }
    if (mapRef.value) {
      initMapChart()
    }
  } finally {
    mapLoading.value = false
  }
}

/** ：轨迹点详情缓存（itemId -> SgjItem，供 tooltip 显示短评摘要与评分） */
const pointDetailCache = ref<Map<number, SgjItem>>(new Map())

function preloadPointDetails(): void {
  const points = [...(trajectory.value?.visited || []), ...(trajectory.value?.wish || [])]
  const ids = Array.from(new Set(points.map(p => p.itemId).filter((id): id is number => id != null)))
  if (!ids.length) return
  Promise.allSettled(ids.map(id => getFrontItem(id))).then(results => {
    const cache = new Map(pointDetailCache.value)
    results.forEach((r, i) => {
      if (r.status === 'fulfilled' && r.value.data?.itemId != null) {
        cache.set(r.value.data.itemId, r.value.data)
      }
    })
    pointDetailCache.value = cache
  })
}

function initMapChart(): void {
  if (!mapRef.value) return
  const visited = trajectory.value?.visited || []
  const wish = trajectory.value?.wish || []
  const displayVisited = mapLimit.value ? visited.slice(-mapLimit.value) : visited

  mapChart?.dispose()
  mapChart = echarts.init(mapRef.value)

  const visitedData = displayVisited.map((p: TravelPoint) => ({
    name: p.title,
    value: [p.longitude, p.latitude, p.finishDate || '', p.itemId]
  }))
  const wishData = wish.map((p: TravelPoint) => ({
    name: p.title,
    value: [p.longitude, p.latitude, '', p.itemId]
  }))
  const lineCoords = displayVisited
    .filter((p: TravelPoint) => p.longitude != null && p.latitude != null)
    .map((p: TravelPoint) => [p.longitude, p.latitude])

  mapChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'lines') return '旅行轨迹'
        const name = params.name || ''
        const value = params.value || []
        const date = value[2] || ''
        // tooltip 增强——评分 + 短评摘要（截断 50 字）
        const itemId = value[3]
        const cached = itemId != null ? pointDetailCache.value.get(Number(itemId)) : undefined
        const lines = [`<b>${name}</b>`]
        if (date) lines.push(date)
        if (cached?.rating != null) lines.push(`⭐ ${cached.rating}`)
        if (cached?.comment) {
          const summary = cached.comment.length > 50 ? cached.comment.slice(0, 50) + '…' : cached.comment
          lines.push(summary)
        }
        return lines.join('<br/>')
      }
    },
    geo: {
      map: 'china',
      roam: true,
      zoom: 1.2,
      scaleLimit: {
        min: 1,
        max: 10
      },
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
        name: '去过',
        type: 'effectScatter',
        coordinateSystem: 'geo',
        data: visitedData,
        symbolSize: 10,
        itemStyle: { color: themeColor('--sgj-moss') },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          color: themeColor('--sgj-text'),
          fontSize: 12
        }
      },
      {
        name: '想去',
        type: 'scatter',
        coordinateSystem: 'geo',
        data: isLogin.value ? wishData : [],
        symbolSize: 8,
        itemStyle: { color: themeColor('--sgj-amber') },
        label: {
          show: true,
          position: 'right',
          formatter: '{b}',
          color: themeColor('--sgj-text'),
          fontSize: 12
        }
      },
      {
        name: '轨迹',
        type: 'lines',
        coordinateSystem: 'geo',
        polyline: true,
        data: lineCoords.length > 1 ? [{ coords: lineCoords }] : [],
        lineStyle: {
          color: themeColor('--sgj-primary'),
          width: 2,
          curveness: 0.2
        },
        effect: {
          show: true,
          period: 6,
          trailLength: 0.2,
          symbol: 'arrow',
          symbolSize: 6,
          color: themeColor('--sgj-primary')
        }
      }
    ]
  })

  mapChart.on('click', (params: any) => {
    if (params.seriesType !== 'effectScatter' && params.seriesType !== 'scatter') return
    const value = params.value || []
    const itemId = value[3] ?? value[2]
    if (!itemId) return
    getFrontItem(itemId).then(response => {
      detail.value = response.data || null
      detailOpen.value = true
    })
  })
}

function handleResize(): void {
  mapChart?.resize()
}

function openAdd(): void {
  Object.assign(addForm, {
    title: undefined,
    address: undefined,
    city: undefined,
    country: undefined,
    latitude: undefined,
    longitude: undefined,
    bestSeason: undefined,
    placeCategory: undefined,
    tags: undefined
  })
  addOpen.value = true
}

function openMapPicker(): void {
  pickerOpen.value = true
}

/** 地图选点确认后回填表单经纬度 */
function confirmPick(latitude: number, longitude: number): void {
  addForm.latitude = latitude
  addForm.longitude = longitude
}

function submitAdd(): void {
  proxy.$refs['addFormRef'].validate((valid: boolean) => {
    if (!valid) return
    const data = {
      ...addForm,
      itemType: 'PLACE',
      status: 'WANT'
    } as SgjItem
    addFrontItem(data).then(() => {
      addOpen.value = false
      proxy.$modal.msgSuccess('添加成功')
      // B-03：刷新列表后反查新条目，提供"去写笔记"入口
      loadData().then(() => {
        const created = list.value.find(it => it.title === data.title && it.itemType === data.itemType)
        if (created?.itemId) {
          goWriteNote(created.itemId, created.title || '')
        }
      })
    }).catch(() => {})
  })
}

/** B-03：提示并跳转笔记页快速新建笔记（携带关联条目参数） */
function goWriteNote(itemId: number, title: string): void {
  proxy.$modal.confirm(`是否立即为「${title}」写一篇笔记？`, '添加成功', {
    confirmButtonText: '去写笔记',
    cancelButtonText: '暂不'
  }).then(() => {
    router.push({ path: '/note', query: { itemId: String(itemId), title: title, write: '1' } })
  }).catch(() => {})
}

function openComplete(item: SgjItem): void {
  currentItem.value = item
  Object.assign(completeForm, {
    finishDate: undefined,
    comment: undefined
  })
  completeOpen.value = true
}

function submitComplete(): void {
  if (!currentItem.value?.itemId) return
  completeFrontItem(currentItem.value.itemId, completeForm).then(() => {
    proxy.$modal.msgSuccess('已标记去过')
    completeOpen.value = false
    loadData()
  }).catch(() => {})
}

function openDetail(item: SgjItem): void {
  if (!item.itemId) return
  getFrontItem(item.itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 *  配套：按 itemId 直接打开详情（首页时间线跳转 /travel?itemId= 时调用）
 */
function openDetailById(itemId: number): void {
  getFrontItem(itemId).then(response => {
    detail.value = response.data || null
    detailOpen.value = true
  }).catch(() => {})
}

/**
 * / 配套：读取路由参数
 * - ?itemId=：自动打开对应地点详情（首页时间线跳转）
 * - ?mode=map：直达地图模式（首页「旅行足迹地图」入口卡）
 */
function handleRouteQuery(): void {
  const rawId = route.query.itemId
  if (rawId) {
    const itemId = Number(rawId)
    if (itemId) {
      openDetailById(itemId)
    }
  }
  if (route.query.mode === 'map') {
    mode.value = 'map'
    loadMapData()
  }
  router.replace({ path: '/travel', query: {} })
}

/** 打开编辑（详情抽屉内） */
function openEditDetail(): void {
  if (!detail.value?.itemId) return
  editingId.value = detail.value.itemId
  editOpen.value = true
}

/** 编辑保存成功 */
function handleEdited(): void {
  loadData()
  if (detail.value?.itemId) {
    getFrontItem(detail.value.itemId).then(response => {
      detail.value = response.data || null
    }).catch(() => {})
  }
}

/** 取消完成（DONE -> WANT） */
function handleUncomplete(item: SgjItem): void {
  if (!item.itemId) return
  uncompleteFrontItem(item.itemId).then(() => {
    proxy.$modal.msgSuccess('已取消完成')
    loadData()
  }).catch(() => {})
}

/** 删除条目（二次确认） */
function handleDelete(item?: SgjItem): void {
  const itemId = item?.itemId
  if (!itemId) return
  proxy.$modal.confirm('是否确认删除该条目？删除后可在后台回收站恢复。').then(() => {
    return delFrontItem(itemId)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    detailOpen.value = false
    loadData()
  }).catch(() => {})
}

onMounted(() => {
  loadData()
  handleRouteQuery()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  mapChart?.dispose()
})
</script>

<style scoped lang="scss">
.travel-page {
  color: var(--sgj-text);
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  margin: 20px 0;
}

.load-error {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: var(--sgj-text-4);
}

.page-banner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 56px;
  height: 176px;
  margin-bottom: 24px;
  border-radius: 24px;
  background: #282e2c;
  color: #e7ece9;
  overflow: hidden;

  .banner-blob {
    position: absolute;
    right: -40px;
    top: -120px;
    width: 300px;
    height: 300px;
    border-radius: 50%;
    background: rgba(168, 95, 82, 0.35);
    pointer-events: none;
  }

  .banner-blob2 {
    position: absolute;
    right: 140px;
    bottom: -90px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(93, 111, 102, 0.4);
    pointer-events: none;
  }

  .banner-text {
    position: relative;
    z-index: 1;

    h1 {
      margin: 0;
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 36px;
      line-height: 1.3;
    }

    p {
      margin: 12px 0 0;
      font-size: 14px;
      color: #aeb8b3;
      letter-spacing: 0.5px;
    }
  }

  .banner-add {
    position: relative;
    z-index: 1;
    height: 46px;
    padding: 0 28px;
    border-radius: 23px;
    font-size: 13px;
    font-weight: 500;
    letter-spacing: 1px;
  }

}

html.dark .page-banner {
  background: var(--sgj-bg-deep);
}

html.dark .detail-content .detail-icon {
  color: var(--sgj-primary-light);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;

  .filter-group {
    display: flex;
    gap: 8px;
  }

  .filter-pill {
    padding: 8px 18px;
    border: 1px solid var(--sgj-border-card);
    border-radius: 17px;
    background: var(--sgj-bg-card);
    color: var(--sgj-text-2);
    font-size: 13px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.16s;

    &:hover {
      border-color: var(--sgj-primary);
      color: var(--sgj-primary);
    }

    &.active {
      background: var(--sgj-primary);
      border-color: var(--sgj-primary);
      color: #fff;
    }
  }

  .search-input {
    width: 220px;
    margin-left: auto;
  }
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.place-card {
  background: var(--sgj-bg-card);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(23, 27, 26, 0.06);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 24px rgba(23, 27, 26, 0.12);
  }

  .card-cover {
    position: relative;
    aspect-ratio: 16 / 10;
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;

    .card-cover-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .card-glyph {
      font-family: var(--sgj-font-serif);
      font-size: 44px;
      line-height: 1;
    }

    .card-type-pill {
      position: absolute;
      top: 12px;
      left: 12px;
      padding: 2px 10px;
      border-radius: 10px;
      background: var(--sgj-bg-card);
      color: var(--sgj-text-2);
      font-size: 11px;
    }
  }

  .card-body {
    padding: 16px;
    flex: 1;

    .card-title {
      font-family: var(--sgj-font-serif);
      font-weight: 700;
      font-size: 16px;
      color: var(--sgj-text);
      margin-bottom: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-location {
      font-size: 12px;
      color: var(--sgj-text-3);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-season {
      display: inline-block;
      margin-top: 8px;
      padding: 1px 10px;
      border-radius: 11px;
      background: var(--sgj-moss-soft);
      color: var(--sgj-moss);
      font-size: 11px;
    }

    .card-comment {
      font-size: 12px;
      color: var(--sgj-text-2);
      line-height: 1.5;
      margin-top: 8px;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }

  .card-actions {
    padding: 0 16px 16px;
    display: flex;
    gap: 8px;
  }
}

.map-mode {
  .map-container {
    height: 560px;
    background: var(--sgj-bg-card);
    border-radius: 16px;
    box-shadow: 0 4px 16px rgba(23, 27, 26, 0.06);
    overflow: hidden;
    position: relative;

    .map-chart {
      width: 100%;
      height: 100%;
    }

    .map-error {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--sgj-text-4);
    }
  }

  .map-toolbar {
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 12px;

    .map-tip {
      font-size: 13px;
      color: var(--sgj-text-3);
    }
  }

  .map-legend {
    display: flex;
    gap: 20px;
    margin-top: 12px;
    font-size: 13px;
    color: var(--sgj-text-2);

    .legend-dot {
      display: inline-block;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      margin-right: 4px;

      &.visited {
        background: var(--sgj-moss);
      }

      &.wish {
        background: var(--sgj-amber);
      }
    }

    .legend-line {
      color: var(--sgj-primary);
    }
  }
}

.coord-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.coord-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sgj-text-4);
}

.detail-content {
  text-align: center;

  /* 6.2 详情抽屉头部 meta 行：类型图标 + 状态 tag */
  .detail-meta {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin: 12px 0 4px;

    .detail-type-icon {
      font-size: 20px;
    }
  }

  .detail-cover {
    display: flex;
    align-items: center;
    justify-content: center;

    .detail-cover-img {
      max-width: 100%;
      max-height: 320px;
      border-radius: 12px;
      box-shadow: 0 6px 20px rgba(23, 27, 26, 0.18);
    }
  }

  .detail-icon {
    /* 无封面/封面失效占位：衬线字浅色块，与列表卡片字形占位同语言 */
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 132px;
    height: 184px;
    border-radius: 12px;
    background: var(--sgj-primary-soft);
    color: var(--sgj-primary-dark);
    font-family: var(--sgj-font-serif);
    font-size: 56px;
    font-weight: 700;
  }

  h2 {
    color: var(--sgj-text);
    margin: 8px 0;
  }

  .detail-desc {
    margin-top: 16px;
    text-align: left;
  }

  .detail-comment {
    margin-top: 20px;
    text-align: left;

    h3 {
      color: var(--sgj-text-2);
      margin-bottom: 8px;
    }

    p {
      color: var(--sgj-text);
      line-height: 1.6;
    }
  }

  .detail-actions {
    margin-top: 24px;
    display: flex;
    justify-content: center;
    gap: 12px;
  }
}

/* 移动端最小适配：工具栏换行、搜索框占满整行、卡片操作按钮可换行、地图高度收敛 */
@media (max-width: 768px) {
  .toolbar {
    flex-wrap: wrap;
    gap: 8px;

    .el-input {
      width: 100% !important;
    }
  }

  /*  移动端卡片栅格：minmax(160px,1fr) */
  .card-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 12px;
  }

  .card-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .el-button + .el-button {
      margin-left: 0;
    }
  }

  .map-toolbar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .map-container {
    height: 420px;
  }

  .detail-actions {
    flex-wrap: wrap;
  }
}
</style>