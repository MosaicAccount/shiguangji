# 0.0.5 链接 —— 详细设计

> 架构：`link-architecture.md`；需求：`docs/requirements/0.0.5-link.md`；原型图（视觉与文案基准）：`docs/design/mockups/link-0.0.5/index.html`

## 1. 语法与解析口径（FR-005-1 / FR-005-3）

### 1.1 token 定义

- 标记形如 `[[标题]]`；括号内首尾空白忽略（`[[ 标题 ]]` 与 `[[标题]]` 等价），其余字符逐字比较、**区分大小写**（库内标题保存时已 trim）。
- 正则：`\[\[([^\[\]\r\n]+)\]\]` —— 内层含 `[`、`]`、换行的都不成立；空 `[[]]`、未闭合 `[[x`、嵌套 `[[a [[b]] c]]` 按普通文本。
- **已知限制**（不设转义）：标题本身含 `[` / `]` / 换行的笔记无法被引用；标记内夹 Markdown 强调（`[[**x**]]`）渲染时会被拆断，同样不成立。

### 1.2 哪些位置的 `[[...]]` 不解析

正文渲染成段落时，以下区间内的 `[[...]]` 原样保留、不计反链：**围栏代码块、行内代码、Markdown 链接的文字与 URL、图片、引用式链接、HTML 标签**。实现是先造一份**等长掩码文本**（这些区间的字符逐个替换为空格、保留换行），再在掩码文本上跑正则——下标与原文对齐，反链片段直接按掩码下标切原文：

```
原文：说明 `[[不是链接]]`，但 [[番茄工作法]] 是链接
掩码：说明                ，但 [[番茄工作法]] 是链接
```

掩码顺序：围栏代码（按行扫描状态机，``` 与 ~~~、允许 ≤3 空格缩进与信息串）→ 行内代码（反引号 run 成对：`` (`+)([\s\S]*?)\1 ``）→ 链接/图片/引用式链接（以 `](` 与 `][` 定位、向左做**括号配平**找起始 `[`，整体掩掉——链接文字里嵌 `[[...]]` 时扁平正则掩不住，配平才能整体处理）→ HTML 标签（`<[^>\n]+>`）。

### 1.3 解析与可见性

- 解析范围＝当前查看者可见的笔记，与详情接口同一口径：匿名＝publicOwner 且 `is_public='1'`；其他登录用户＝本人；博主/管理员＝全量。
- 可见范围内命中 1 篇 → 成链；0 篇 → `NOT_FOUND`；≥2 篇 → `AMBIGUOUS`（D-14：不猜）。
- 对匿名，「私密」与「不存在」同表现为 `NOT_FOUND`，不泄露存在性。

## 2. 接口契约

### 2.1 `GET /app/note/{noteId}`（扩展出参，既有行为不变）

响应 `data` 在笔记本体上新增三个字段（`@JsonInclude(NON_NULL)`，无 token 时 `links` 为 `[]`、无标题可比时不下发计数字段）：

```jsonc
{
  "noteId": 101, "title": "时间管理", "body": "……[[番茄工作法]]……", "……": "既有字段不变",
  "links": [                       // 按 distinct raw 去重；前端据此替换所有出现位置
    { "raw": "[[番茄工作法]]", "title": "番茄工作法", "noteId": 102, "reason": null },
    { "raw": "[[ 深度工作 ]]",   "title": "深度工作",   "noteId": null, "reason": "NOT_FOUND" },
    { "raw": "[[周记]]",         "title": "周记",       "noteId": null, "reason": "AMBIGUOUS" }
  ],
  "backlinks": [                   // 本篇标题在可见范围内唯一时才有；否则空数组
    { "noteId": 201, "title": "番茄工作法", "updateTime": "2026-10-10 09:15:00",
      "snippet": "…它是 [[时间管理]] 里最可执行的一小块…", "hitCount": 1 }
  ],
  "sameTitleCount": 1              // 可见范围内同名笔记数（含本篇）；>1 时前端反链区块改显说明
}
```

`reason` 取值：`NOT_FOUND` / `AMBIGUOUS`。列表接口 `/app/note/list`、后台 `/business/note/*`、导出均**不**下发这三个字段。

### 2.2 `GET /app/search?keyword=三体`（新增，`@Anonymous`）

- `keyword` 必填；trim 后长度 < 2 返回业务错误「关键词至少 2 个字」（前端本就拦截，此处为兜底）。
- 分组固定顺序：`movie`（电影+电视剧混排）/ `book` / `place` / `note`；空组不下发（`total` 为 0 的组直接省略，前端无需再判）。
- 每组 `rows` 最多 10 条，`total` 为该组命中总数（`PageHelper` 分页 total）；组内按 `update_time` 倒序（复用既有列表口径）。
- 匹配：条目按 `title like`（不搜标签/短评/备注/导演/演员/作者）；笔记按既有「标题+正文」全文检索（ngram + 0 命中 like 兜底，`cleanKeyword` 剔布尔运算符）。
- **任何查看者都不下发 `comment` / `remark`**（条目结果在服务端置空，笔记结果本就不带 `remark` 之外的私人字段、正文不下发），因此无访客脱敏分支。

```jsonc
{
  "code": 200, "data": {
    "keyword": "三体",
    "groups": [
      { "key": "movie", "total": 1, "rows": [ { "itemId": 9011, "itemType": "TV", "title": "三体", "status": "DONE", "rating": 8.9, "coverUrl": null, "releaseYear": 2023 } ] },
      { "key": "book",  "total": 1, "rows": [ { "itemId": 9023, "itemType": "BOOK", "title": "三体", "status": "DONE", "rating": 9.3, "coverUrl": "…", "author": "刘慈欣" } ] },
      { "key": "note",  "total": 2, "rows": [ { "noteId": 9001, "title": "三体 读后笔记", "excerpt": "…<摘要片段>…", "hitTotal": 3, "updateTime": "…" } ] }
    ]
  }
}
```

## 3. 后端设计

### 3.1 `SgjNoteLinkUtils`（纯函数，可脱离 DB 单测）

| 方法 | 职责 |
| --- | --- |
| `maskNonLinkText(String body)` | 返回等长掩码文本（§1.2 顺序；区间内非换行字符替换为空格） |
| `extractNoteTokens(String body)` | 掩码 + 正则 → `List<NoteToken{raw,title,start}>`，按 distinct raw 去重、保首现下标 |
| `resolveLinks(List<NoteToken>, List<SgjNote> refs)` | 候选按标题逐字 `equals` 分组 → `List<NoteLink>`（1/0/≥2 三态） |
| `buildBacklinks(List<SgjNote> candidates, String title)` | 逐篇 `buildBody` → 掩码 → 精确校验（token.title 逐字等于目标标题才算），产出 `NoteBacklink`（含 hitCount 与首现片段） |
| `countExactTitle(List<SgjNote> refs, String title)` | 同名计数（含 CI 排序规则对策：只认 `equals`） |
| `snippetAround(String body, int index)` | 按掩码下标切原文，前后各约 28 字符、折叠空白 |
| `escapeLike(String)` | `\ % _` 转义（SQL 侧 `escape '\\'`） |

`NoteToken` 为工具类内静态嵌套类。判定逻辑全部收敛在这几个静态方法里（沿 `AppReviewServiceImpl.aggregateTags` 的可测性先例）。

### 3.2 SQL（`SgjNoteMapper.xml` 新增段，不动 `listWhere`）

```xml
<!-- 目标候选：只做候选，逐字判定在 Java（库排序规则不区分大小写） -->
selectNoteRefsByTitles(titles, createBy, publicOnly):
  select n.note_id, n.title, n.is_public, n.create_by
  from sgj_note n
  where n.del_flag = '0'
    [and n.create_by = #{createBy}]      -- null＝不限（博主/管理员）
    [and n.is_public = '1']              -- 仅匿名
    and n.title in (…)                   -- 空集合时 1=0 兜底

<!-- 反链候选：LIKE 只做候选，代码块里的假引用由 Java 校验剔除 -->
selectBacklinkCandidates(likeTitle, excludeNoteId, createBy, publicOnly):
  select n.note_id, n.title, n.content, n.update_time, n.create_time
  from sgj_note n
  where n.del_flag = '0'
    and n.note_id != #{excludeNoteId}
    and n.content like concat('%', #{likeTitle}, '%') escape '\\'
    [and n.create_by = #{createBy}] [and n.is_public = '1']
  order by coalesce(n.update_time, n.create_time) desc, n.note_id desc
```

`SgjItemMapper.selectSearchItems(itemTypes, titleLike, createBy)`：`selectSgjItemVo` 联查 + `item_type in (...)` + `title like ... escape '\\'` + `create_by`，`order by update_time desc`（复用既有列清单；`comment`/`remark` 查出后由服务层置空）。

### 3.3 `attachLinkContext` 编排（`SgjNoteServiceImpl`）

```
attachLinkContext(note, createBy, anonymous):
  tokens = extractNoteTokens(note.body)
  refs   = tokens 空 ? [] : selectNoteRefsByTitles(distinct titles, createBy, anonymous)
  note.links = resolveLinks(tokens, refs)                     // 空则 []
  title = note.title?.trim()
  if title 空: note.backlinks = []; return                    // 存量脏数据兜底
  note.sameTitleCount = countExactTitle(selectNoteRefsByTitles([title], …), title)
  if sameTitleCount > 1: note.backlinks = []; return          // 重名：链接全部降级，不列来源
  cands = selectBacklinkCandidates(escapeLike(title), note.noteId, createBy, anonymous)
  note.backlinks = buildBacklinks(cands, title)
```

`AppNoteController.getInfo` 在既有可见性校验之后、`maskNoteForGuest` 之前调用；编辑页复用同一接口，天然拿到 `backlinks` 供改名提醒计数。

### 3.4 `AppSearchController` / `AppSearchServiceImpl`

- 控制器：`@Anonymous`；解析范围 `resolveCreateBy()` + `isAnonymous()` 传入服务；关键词校验。
- 服务：四组依次 `PageUtils.startPage(1, 10)` → 查询 → `((Page<?>) list).getTotal()` 取 total；笔记组走 `selectSgjNoteFrontList`（keyword + 匿名 `is_public='1'`，摘要/命中数/兜底全部复用）；条目各组 `comment`/`remark` 置空；`total == 0` 的组不下发。

## 4. 前端设计

### 4.1 `utils/noteLinks.ts`

```ts
linkifyNoteTokens(root, links)   // 按 raw 长度倒序；TreeWalker 遍历文本节点，
                                 // 跳过祖先为 code/pre/a/textarea 的节点；命中 raw →
                                 // noteId 造 <a class="note-link" href="/note/detail?noteId=">
                                 // 否则 <span class="note-link is-broken" title="笔记未找到|标题重名，无法确定目标">
                                 // 服务端没下发的 [[...]] 不动；替换后文本里已无 raw，天然幂等
isLinkableTitle(title)           // 无 [ ] 与换行 → 可作为插入候选
```

- 节点全用 `createElement` + `textContent`，标题不参与 URL；`href` 只由数字 noteId 拼。
- **顺序：互链 → 大纲 → 关键词高亮**（高亮会把文本节点切进 `<mark>`，切完再找 raw 就找不全）；`applyHighlight` 开头再调一次幂等的 linkify 兜底。

### 4.2 详情页（`detail.vue`）

- `watch(note)` 的 nextTick 里：先 `linkifyNoteTokens(bodyRef 的 .markdown-viewer, note.links)`，再抽大纲；`applyHighlight` 开头重调 linkify。
- 正文容器事件委托：`closest('a.note-link')` → `preventDefault` + `router.push({ path:'/note/detail', query:{ noteId } })`（丢掉旧 `keyword`，新篇不带上一篇的高亮打开）。
- **同路由复用修复**：新增 `watch(() => route.query.noteId, …)` 重载笔记（互链跳转是同组件换 query，不重开组件）。
- 反链区块：`NoteBacklinks` 组件挂在 `· 完 ·` 之后；`sameTitleCount > 1` → 一行说明「本篇标题与另外 N-1 篇重复，引用无法定位」；否则 `backlinks.length > 0` 列表（标题可点、时间、片段），0 条整块不渲染。桌面/移动同一位置。
- 样式（`--sgj-*` 令牌）：互链＝主题色点状下划线、hover 浅底；降级＝次级色虚线下划线 + `title` 提示，不可点；反链＝`--sgj-moss-soft` 卡片、来源行 hover 浅底，与原型一致。

### 4.3 编辑器（FR-005-6 / FR-005-7）

- `MarkdownEditor` 工具栏新增「插入笔记链接」（`插图` 旁，前后台共用组件同时生效）；打开 `NoteLinkPicker`。
- `NoteLinkPicker`：`listFrontNote({ title, pageNum, pageSize: 20 })` 按标题搜（防抖 300ms）+「加载更多」；**同名按 title 合并为一条**；`!isLinkableTitle(title)` 置灰并注明原因；选中 `emit('select', title)`。预览不解析互链（真实行为），插入后 toast 提示。
- 插入为**行内**插入（光标处原样插入 `[[标题]]`、不加前后空行，与「插图」的独占一行不同），插完恢复光标。
- `edit.vue`：加载笔记时记 `savedTitle` / `savedBacklinkCount`；保存校验通过后，若 `form.noteId && 标题变更 && count > 0` → `proxy.$modal.confirm('有 N 篇笔记仍引用旧标题「X」，保存后这些链接会失效。系统不会自动改写正文。')`，取消则不落库；确认或无影响则照常保存。

### 4.4 全局搜索（FR-005-5）

- 入口：前台头部主题切换左侧搜索图标（`svg-icon icon-class="search"`），桌面与移动都在，导航项与移动 tab 数量不动。
- `/search`：hero 输入框（进入聚焦、回车/按钮提交）、`?keyword=` 直链可刷新可回退；关键词 < 2 字 `msgWarning` 不发请求；空态（无输入/无结果）与失败重试态。
- 分组渲染：`影单 / 书单 / 足迹 / 笔记`，空组不渲染，组头带总数；条目行＝封面（`photoUrl`，失败回退类型衬线字）+ 标题 + 类型徽标 + 年份/作者/状态等 meta + 评分；笔记行＝标题 + 摘要（`splitByKeyword` 切段 `<mark>` 高亮）+ `hitTotal`（「文中 N 处」）+ 时间。
- 跳转矩阵（复用各页既有的 query 深链）：

| 结果 | 跳转 |
| --- | --- |
| MOVIE / TV | `/movie?type=<itemType>&itemId=<id>` |
| BOOK | `/book?itemId=<id>` |
| PLACE | `/travel?itemId=<id>` |
| 笔记 | `/note/detail?noteId=<id>&keyword=<kw>`（复用既有命中高亮） |

- 路由：`/search` 挂 `FrontLayout`；`PUBLIC_ROUTES` 追加 `'/search'`，`publicRoutes.spec.ts` 同步断言。

## 5. 边界与降级矩阵

| 场景 | 行为 |
| --- | --- |
| 目标不存在 / 已删 / 回收站 | `NOT_FOUND`，降级样式，不可点 |
| 目标对查看者不可见 | 同上（匿名不区分「私密」与「不存在」） |
| 标题重名（可见范围内 ≥2） | `AMBIGUOUS`；该篇反链区块改显说明、不列来源 |
| token 在代码/链接文字/图片 alt/HTML 里 | 原样文本，不下发、不成链、不计反链 |
| 未闭合 / 空 / 嵌套 / 内夹格式 | 普通文本 |
| 笔记标题为空（存量脏数据） | links 正常、backlinks 空数组、不下发 sameTitleCount |
| 改标题保存 | 提醒一次，不自动改写正文（FR-005-6） |
| 编辑预览 / 后台 / 导出 | `[[...]]` 原样（预览有提示文案） |
| 搜索关键词 < 2 字 | 前端拦截 + 服务端兜底错误 |
| 搜索空组 | 服务端不下发，前端不渲染 |
