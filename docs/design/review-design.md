# 0.0.4 回顾 —— 详细设计

> 架构：`review-architecture.md`；需求：`docs/requirements/0.0.4-review.md`；原型图（视觉与文案的最终基准）：`docs/design/mockups/review-0.0.4/review-page.html`
> **文案即定稿**：页面上的中文文案与原型图逐字对齐，实现不得改写。

## 1. 接口契约

### `GET /app/review/year?year=2024`

登录态接口（**不标 `@Anonymous`**，匿名 401）。`year` 必填，非四位数字或不在 `1900..2100` 返回 400（`AjaxResult.error(400, "year 非法")`）。

响应 `data`：

```jsonc
{
  "year": 2024,
  "years": [2024, 2023, 2022],        // 有数据的年份降序：条目 finish_date 年份 ∪ 笔记 create_time 年份
  "summary": {
    "total": 87,                       // 该年 DONE 条目数（finish_date 归年；不含 finish_date 为空者）
    "movie": 32, "book": 12, "place": 7,
    "note": 36                         // 该年笔记数（create_time 归年，本人全量，无公开/私人分叉）
  },
  "places": [                          // 该年 DONE 地点，按 finish_date 升序（连线次序即此序）
    {
      "itemId": 101, "title": "平潭",
      "province": "福建", "country": null,     // 境外点 province 为 null、country 为国名；两者皆空须归「未分类」
      "latitude": 25.50, "longitude": 119.79,  // 任一为 null 即「无坐标」：不上图但下发（前端在图内交代）
      "finishDate": "2024-03-12",
      "rating": 8.6, "comment": "北港村的石头厝住了一晚……",
      "tags": "海岛,骑行", "coverUrl": null, "photoCount": 3,
      "photos": ["/profile/upload/…jpg"],                       // 条目照片（相对路径）
      "notes": [ { "noteId": 12, "title": "故宫一日游路线", "createTime": "2026-06-22" } ]
    }
  ],
  "topRated": {                        // FR-004-3：影视/书籍分榜，各取前 N=5
    "movie": [ { "itemId": 1, "title": "…", "rating": 9.5, "finishDate": "2024-05-01", "coverUrl": "…" } ],
    "book":  [ … ]
  },
  "tags": [ { "name": "人文", "count": 12 }, … ]   // 已拆分、已清洗、按 count 降序；同名归并
}
```

空年份（`years` 含该年但无任何数据，或用户用 `‹ ›` 翻到无数据年）：`summary` 各值为 0、三个数组为空，前端渲染空态面板；**接口不返回 404**（年份切换是纯前端状态）。

### SQL（`SgjReviewMapper.xml`，`where` 前缀与 `selectTimeline` 逐字对齐）

```sql
-- 年份列表：条目 ∪ 笔记，降序
select distinct year(finish_date) as y from sgj_item
 where del_flag='0' and status='DONE' and finish_date is not null
   and create_by=#{createBy}   -- 动态：空则不限制（AppScopeHelper 口径）
union
select distinct year(create_time) as y from sgj_note
 where del_flag='0' and create_by=#{createBy};

-- 年度汇总（一次查条目分型，笔记数单独 count）
select item_type as itemType, count(*) as cnt from sgj_item
 where del_flag='0' and status='DONE' and finish_date is not null
   and year(finish_date)=#{year} and create_by=#{createBy}
 group by item_type;
select count(*) from sgj_note
 where del_flag='0' and year(create_time)=#{year} and create_by=#{createBy};

-- 年度地点（SgjItemVo 的 place 联查列照抄 SgjItemMapper，只取展示字段）
select i.item_id, i.title, i.rating, i.comment, i.tags, i.cover_url,
       i.finish_date, p.province, p.country, p.latitude, p.longitude,
       (select count(*) from sgj_item_photo pc where pc.item_id=i.item_id) as photo_count
from sgj_item i
left join sgj_item_place p on p.item_id = i.item_id
where i.del_flag='0' and i.status='DONE' and i.finish_date is not null
  and i.item_type='PLACE' and year(i.finish_date)=#{year} and i.create_by=#{createBy}
order by i.finish_date asc, i.update_time asc, i.item_id asc;

-- 评分榜（type = 'MOVIE' | 'BOOK'，各一次）
select item_id, title, rating, finish_date, cover_url
from sgj_item
where del_flag='0' and status='DONE' and finish_date is not null
  and rating is not null and item_type=#{type}
  and year(finish_date)=#{year} and create_by=#{createBy}
order by rating desc, finish_date desc, item_id desc
limit #{n};

-- 标签（原样拉回，Java 拆）
select tags from sgj_item
where del_flag='0' and status='DONE' and finish_date is not null
  and tags is not null and tags != ''
  and year(finish_date)=#{year} and create_by=#{createBy};
```

标签拆分（`AppReviewServiceImpl`，可单测）：`tags.split(",")` → `trim()` → 过滤空串 → `merge(count, +1)` → 按 count 降序、同 count 按名字字典序。

## 2. 页面结构（`views/front/review/index.vue`）

### 2.1 路由与守卫

```ts
{ path: '/review', component: FrontLayout, hidden: true,
  children: [{ path: '', component: () => import('@/views/front/review/index.vue'),
               name: 'FrontReview', meta: { title: '回顾' } }] }
```

`/review` **不加入** `PUBLIC_ROUTES` → 匿名被守卫弹登录（与 `/plan` 同）。守卫白名单单测（`publicRoutes` 的 spec）同步加一条「`/review` 不在白名单」的断言。

### 2.2 状态机

| 状态 | 触发 | 渲染 |
| --- | --- | --- |
| loading | 首次进入 / 切年份 | 手绘骨架（歪盒子轮廓 + 浅纸底），报头隐藏 |
| default | 数据到位且 `summary.total + note > 0` | 报头 + 统计卡 + 足迹 + 评分榜 + 标签 |
| empty | 数据到位但全为 0 | 页头保留、四数归零，主体为空态面板（「{year} 年还没有记录」+ 两处去处的链接） |

年份切换：`‹ ›` 步进（步进任何年份，含无数据年）；胶囊只列 `years`（当前年高亮）。年份来自 `years`，无 `years` 时（全新用户）直接空态、`‹ ›` 禁用。

### 2.3 报头（对齐原型 `review-page.html`）

- 结构：`YEAR IN REVIEW` 眉题 → 主行（`‹` 圆钮 + 年份 76px + `›` + 右侧汇总句「这一年，你记录了 **87** 件值得记住的小事。」，年份与句子**基线对齐**）→ 口径行「条目按完成日期归年 · 笔记按写下时间归年」→ 年份胶囊排。
- 统计卡四张等高等宽（`repeat(4,1fr)`）：看过 · 电影/剧集、读过 · 书籍、去过 · 地点、写过 · 笔记；类型色用 `hdk-*` 前缀（`--hd-red/--hd-moss/--hd-amber/--hd-ink-soft`），圈内衬线字（影/书/去/文）。

### 2.4 评分榜与标签分布

- 评分榜：`hd-cols` 两栏（影视 / 书籍，`repeat(2, minmax(0, 1fr))`——`1fr` 的自动最小尺寸会被长标题顶开把另一栏挤出去）；每行 `类型圆（影/剧/书） + 标题（单行省略） + 元信息 + 评分`，元信息按类型写 `电影 · M 月看过 / 剧集 · M 月看完 / 书籍 · M 月读完`；栏头注明参与条数「{total} 条里 {rated} 条有评分」（`rated` 由后端另给或前端以 `topRated` 长度不可得时写「仅含已评分条目」——**取后端加 `ratedCount` 字段**，见 §1 契约补充）。地点不设榜。同分次级排序 `finish_date` 近者在前（SQL 已含）。
- 标签分布：手绘条形（宽度按最大 count 归一），Top 10 之外收成一行「其余 {n} 个标签未进前十」；**口径写在区块上**——统计影视 / 书籍 / 地点的条目标签，笔记不参与（笔记只贡献「写过」那个数）。点击行为本期不做（非目标）。

> 契约补充：`topRated` 同级增加 `ratedCount: { movie: 32, book: 10 }`（该年该类型已评分条目总数，一条 `count(*) ... and rating is not null group by item_type` 即得），供栏头「{total} 条里 {rated} 条有评分」使用。

## 3. 足迹块（`ReviewMap.vue` + `mapLayout.ts`）

### 3.1 分档判定（D-48）

```ts
const PIN_LIMIT = { desktop: 10, mobile: 5 }   // mobile = 容器宽 < 768px
const usePins = places.length <= limit
```

### 3.2 逐点档绘制规则（顺序即管线）

全部为 `mapLayout.ts` 纯函数，输入输出均为普通对象（百分比坐标），**逐条与原型对齐**：

1. **等比投影**：`x = longitude`、`y = -latitude`（度数直投，不做任何轴拉伸——境外 / 南半球同样落框内）；
2. **自动缩放（含安全区）**：对全部点求 bbox，等比铺进容器，并**按像素内缩**（桌面左右 56px / 上下 62px，移动端 34px）——内缩是为了给图钉圆与文字标签留位置，否则贴边的点会被框裁掉（实测过：最北的点圆与标签一起被切）；单点居中 `(50, 50)`；
3. **按屏幕距离并簇** `clusterPoints(pts, gapX, gapY)`：贪心，阈值按**图钉圆直径 46px** 折算成百分比（`46/宽`、`46/高`）——两个圆只要叠上就该并，用固定 5% 会在纵向只差 17px 时漏并、在横向差 40px 时并得过狠；命中的簇做位置平均，**簇内次序保持时间序**；
4. **标签侧由路线方向决定**：对簇 `i`，取相邻簇（`i-1` / `i+1`）的 `y` 均值，均值在下方 → 标签放上方（`up`），否则下方；贴边（`x < 26%` 用 `l`、`x > 74%` 用 `r`）时标签向内生长不出框；
5. **连线收边**：相邻簇连线段以向量单位化在两端各缩进 `R = 27px`（圈半径 23 + 空隙），段长 `≤ 2R` 不画；SVG `viewBox` 取容器实际像素（`clientWidth/clientHeight`），1 单位 = 1px，线宽与收边距离不失真。

视觉：手账点阵纸底（`radial-gradient` 点阵）+ 手绘指北针 + 抖线外框（`filter: url(#rough)`），左下角标注「手绘示意 · 非比例地图」，左上角「点任一点 → 高德看真实位置」。无坐标地点：图右下角一行「{names} 没填坐标，没落在图上」。

### 3.3 聚合档（自适应阶梯）

```ts
const REGION: Record<string, string> = { 北京:'华北', … 黑龙江:'东北' }   // 34 省级行政区 → 7 大区
const LEVELS = [
  { name: '省',   key: p => isCN(p) ? p.province : p.country },        // 境外按国家成泡
  { name: '大区', key: p => isCN(p) ? REGION[p.province] ?? p.province : '境外' }
]
const AGG_MAX = 12
```

从「省」开始试，泡数 ≤ 12 即用；否则降到「大区」（大区仅 8 个，必然 ≤ 12，两级必够）。`isCN(p) = p.country == null || p.country === '' || p.country === '中国'`；`province` 与 `country` 皆空的归「未分类」。气泡位置取该级的**示意坐标表**（与原型 `PROV_POS` / `REGION_POS` 一致，真实现由高德定位）。计数文案「{total} 个地方 · 按{level} {n} 个」，图下标注「手绘示意 · 非比例 · 按{level}计数」。移动端：图上只留计数、省名进图下清单。

## 4. 弹层（`ReviewDetailModal.vue`）

| 态 | 触发 | 内容 |
| --- | --- | --- |
| 点详情 | 逐点档点图钉 / 组地图点图钉 | 手绘卡片**弹性展开**（`@keyframes` 过冲曲线）：胶带照片位（有封面用封面，否则手绘衬线字位）+ 地名 h3 + 「{year} 年 {M月} · {省/国} · ★ {rating}」+ 短评引用块 + **照片条**（`photos`，点开原图）+ **关联笔记**（`notes`，点进 `/note/detail?noteId=`）+ 标签 chip + 动作「在地图上定位 →」「看这条记录 →」 |
| 组地图 | 聚合档点气泡 | 同一弹层容器：标题「{组名} · {n} 个地方」+ 高德瓦片区（**首次打开才 `loadAMap()`**）+ 该组图钉按真实坐标 + 编号清单（近点并簇，图上编号对清单行）|

- 「在地图上定位」= 打开组地图弹层的单点模式（复用同一高德容器，中心为该点）；「看这条记录」= 跳既有条目详情（复用 `/movie` 等列表的详情抽屉入口约定；本期最小实现为 `router.push` 到对应模块列表）。
- **降级**：`loadAMap()` reject 时高德区显示「地图加载失败」+ 清单仍然完整可用（图不白屏）。
- 移动端：弹层改底部抽屉形态（同一内容，容器样式两套）。
- 挂载注意：弹层挂在布局根而非 `.plan-page` 令牌作用域内时，须自带 `plan-page` class，否则 `--hd-*` 未定义（原型已踩过）。

## 5. 入口（首页 hero 卡，FR-004-5 方案 A）

复用 `hero-map-entry` 结构与样式，加在 `hero-actions` 内（`flex-wrap` 天然换行）：

```
[ ✈ 旅行足迹地图 … ]  [ ✦ 2024 · 87 件小事 / 年度回顾 → ]
```

- 仅登录态渲染（`!!getToken()`，同 `/plan` 的 `isLogin` 判定）；访客不渲染。
- 副标题数字：`getFrontHomeData().summary` 已有 `doneTotal`——但「最近有数据年份」首页拿不到，入口卡**不额外发请求**，副标题固定文案「把一年攒成一面墙」+ 图标；年份与数字留给回顾页自己。首年可后续迭代。

## 6. 皮肤与样式约定（D-47）

- 回顾页 `<style>` 顶部 `@import '../plan/handdrawn.css'`（构建期内联，与 `/plan` 单源）；
- 新增页面级样式集中在 `review/index.vue` 与两个子组件的 scoped style，令牌一律用 `--hd-*`，不得出现硬编码色值；
- **共享皮肤缺口回写**：`handdrawn.css` 只给 `.g-movie .ring ellipse` 写了圈色，`.g-book` / `.g-place` 缺失（`/plan` 同样受影响）。本期在回顾页以 `.hd-page .row .glyph .ring ellipse{stroke:currentColor}` 兜底，并在 0.0.7 立一项把该规则并回 `handdrawn.css`（不在本期改共享文件，避免波及 `/plan`）；
- 移动端：报头单列（年份在上、说明句 `flex-basis:100%` 换行）、年份 54px、统计卡 `repeat(2,1fr)`、足迹逐点阈值降 5、评分榜 / 标签单列。

## 7. 不做的事（对齐需求 §4）

分享导出、跨年对比、连续天数、自定义区间、笔记正文盘点、标签点击跳转、`/travel` 加 `focus` 参数——全部不做。
