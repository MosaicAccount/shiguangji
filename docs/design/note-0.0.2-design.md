# 笔记收口（0.0.2） - 设计方案

> **关联文档**：[0.0.2 需求：收口笔记](../requirements/0.0.2-note.md)
> **文档状态**：讨论稿。本文记录 0.0.2 讨论中拍板的全部结论，评审通过后进入编码。
> **分支**：`feat/note-0.0.2`，完成后合回 `develop`。
> **表结构变更**：需新表 `sgj_note_draft`（FR-002-7 草稿箱）。

---

## 1. 结论清单

以下 19 条为本次讨论的最终结论，是本文其余章节的依据。

| # | 议题 | 结论 |
| --- | --- | --- |
| 1 | 命中片段由谁生成 | **后端**。前台列表接口不再下发全文，改下发摘要 |
| 2 | 摘要的作用范围 | 只改前台 `/app/note/list`；后台列表、回收站、导出均不动 |
| 3 | 摘要的显示时机 | **常显**。无关键词取正文开头，有关键词取命中窗口 |
| 4 | 高亮实现 | 前端按关键词**切片段渲染**，不产生 HTML 字符串，不走 `v-html` |
| 5 | 检索参数 | 新增 `keyword`；`title` 保留「仅标题」原义不动。回收站不纳入 |
| 6 | 前后台搜索框文案 | 后台笔记管理页 label 由「标题」改为「关键词」 |
| 7 | 防越权短路 | SQL 中的 `or` **必须加括号**，并补一条匿名 keyword 回归用例 |
| 8 | 草稿存储 | **服务端独立表** `sgj_note_draft`（不混入 `sgj_note` 加标志位），现有六处查询一行不改 |
| 9 | 草稿写入 | **本地缓冲 + 服务端同步**：停手 1s 写本地 `localStorage`，每 15s 与页面离开时推服务端 |
| 10 | 冲突规则 | 进编辑器时比对本地 `savedAt` 与服务端 `updateTime`，取较新者；身份不一致的本地缓冲不采用 |
| 11 | 草稿箱边界 | 只装**新笔记草稿**（`note_id` 为空）；编辑已有笔记的未保存改动也存，但不入箱，进那篇编辑页时静默恢复 |
| 12 | 草稿可见范围 | **只本人可见**：按当前登录用户过滤，管理员不可读他人草稿；接口不加 `@Anonymous` |
| 13 | 草稿箱入口 | 笔记列表页入口条（有草稿才显示）+ 独立页 `/note/draft`；删除为**硬删除** |
| 14 | 草稿上限 | 新笔记草稿最多 20 份，超出时新建被拒并提示先清理；**不做**自动过期清理任务 |
| 15 | 发布与草稿的关系 | 保存笔记时若带 `draftId`，服务端在**同一事务**内删除该草稿 |
| 16 | 离开确认 | 判据为「与打开时的服务器快照不同」；取消、返回、后退、关闭标签页均拦；确认离开后**保留**草稿 |
| 17 | 语法高亮 | `highlight.js` + `marked-highlight`；无语言标注的代码块不高亮 |
| 18 | 全局搜索（D-4） | **不纳入 0.0.2**，转 0.0.5（FR-005-5） |
| 19 | 交付方式 | 本文 + 两端测试；评审后开工 |

**对应 issue**：#30（FR-002-1 正文可检索）、#31（FR-002-2 命中摘要）、#32（FR-002-3 草稿自动保存）、#33（FR-002-4 离开确认）、#34（FR-002-5 代码高亮）、#35（FR-002-7 草稿箱，已拆子 issue #37 / #38 / #39）。FR-002-6 已移出本版，未建 issue。全部挂 milestone「0.0.2」。依赖关系（GitHub Relationships）：#31 ← #30、#32 ← #37、#33 ← #32、#39 ← #37 与 #32。

---

## 2. 现状分析与事实纠正

### 2.1 与本版相关的现状

| 事项 | 现状 | 位置 |
| --- | --- | --- |
| 列表检索 | 仅 `title like` | `SgjNoteMapper.xml:49-51` |
| 列表下发字段 | **含完整 `content`** | `SgjNoteMapper.xml:24` |
| 列表卡片渲染 | 每张卡片把正文渲染成 Markdown | `views/front/note/index.vue:61` |
| 前台列表的另一个消费者 | 条目详情的关联笔记列表，自己也有一份「Markdown 转纯文本」 | `components/ItemNotes/index.vue:13,61` |
| markdown 渲染入口 | 全仓库只有一处 import `marked` | `components/MarkdownViewer/index.vue:7` |
| 依赖 | marked 12.0.2 **已移除** `highlight` 选项 | 原需求文档写「唯一的新增前端依赖」，实际是**两个**包 |
| 草稿存储 | 无任何草稿结构；`sgj_note` 无草稿标志字段 | `sql/init_business.sql:135` |

### 2.2 原始路线图中的错误（均已更正）

| 位置 | 原文 | 实际 |
| --- | --- | --- |
| 现状盘点 | 「`HeaderSearch` 组件在代码中但无任何引用」 | 它被后台侧栏引用（`layout/components/Sidebar/index.vue:39`），是 Fuse.js 的**菜单**搜索，零后端调用 |
| [0.0.2-note.md](../requirements/0.0.2-note.md) 的 D-2 | 「避免前端把整篇正文拉到列表接口」 | 列表接口**本来就**返回完整正文；问题从来不是「前端还是后端算」 |
| 0.0.2 数据与兼容性 | 「唯一的新增前端依赖」 | 是**两个**包（`highlight.js` + `marked-highlight`） |
| 0.0.2 验收标准 | 「关闭标签页均有确认提示」 | iOS Safari 的 `beforeunload` 基本不触发，移动端做不到，须按平台拆分（见 §8） |
| README 版本总览 / 0.0.2 数据与兼容性 | 0.0.2「表结构变更：无」 | 草稿箱需新表 `sgj_note_draft` |

### 2.3 一条必须在实现时堵住的安全边界

`selectSgjNoteList` 的 where 段（`SgjNoteMapper.xml:38-56`）全是 AND 串联，**MyBatis 不保护 `or` 的优先级**。若写成：

```xml
and n.title like concat('%', #{keyword}, '%') or n.content like concat('%', #{keyword}, '%')
```

实际语义是 `((del_flag='0' AND create_by=? AND is_public='1' AND title LIKE) OR content LIKE)`：正文命中即返回，`del_flag`、`create_by`、`is_public` 全部被短路。匿名访客因此能搜到**博主私密笔记**与**回收站笔记**（`maskNotesForGuest` 只清 `remark`，正文照旧下发）。

**必须写成：**

```xml
<if test="keyword != null and keyword != ''">
    and (n.title like concat('%', #{keyword}, '%') or n.content like concat('%', #{keyword}, '%'))
</if>
```

注意：既有用例 `AppApiAuthIsolationSmokeTest.notePublicityVisibility` 不带关键词，**不会**发现这个缺陷。必须补一条带 `keyword` 的匿名用例（见 §7）。

**草稿箱引入了同类的风险面**：草稿是未完成的私密文字，一旦有任何一个查询忘掉过滤条件，它就出现在公开列表里。这正是选择「独立表」而非「同表加 `is_draft`」的原因——独立表让现有六处查询物理上不可能返回草稿（见 §4.1）。

---

## 3. 检索与摘要（后端）

### 3.1 参数与实体

`SgjNote` 域对象新增三个**非表字段**（与既有的 `itemName` 同类，无需 DDL）：

| 字段 | 用途 | 说明 |
| --- | --- | --- |
| `keyword` | 入参 | 标题 **OR** 正文模糊匹配；`title` 保持「仅标题」原义 |
| `excerpt` | 出参 | 前台列表摘要，纯文本 |
| `draftId` | 入参 | 保存笔记时携带，用于同事务删除对应草稿（见 §4.4） |

一个 `keyword` 同时服务前台（`AppNoteController.list`）与后台（`SgjNoteController.list:42` 直接透传同一 mapper），无需两套参数。

### 3.2 接口变更

| 接口 | 变更 |
| --- | --- |
| `GET /app/note/list` | 新增 `keyword` 入参；响应**不再含 `content`**，改为 `excerpt`；有命中时摘要以命中处为中心 |
| `GET /app/note/{noteId}` | 不变（详情页仍需要全文） |
| `POST` / `PUT /app/note` | 新增可选 `draftId`，保存成功后同事务删除该草稿 |
| `GET /business/note/list` | 新增 `keyword` 入参；响应**保持含 `content`**（后台摘要列与 CSV / JSON 导出依赖它） |
| 回收站两个接口 | 不变（表格无内容列，命中理由不可见，故不纳入正文检索） |

### 3.3 摘要生成规则

实现位置：`SgjNoteServiceImpl` 的静态方法（对齐既有先例 `SgjItemServiceImpl.parsePhotoUrls`，`shiguangji-business` 模块无 `utils` 包，不新开包）。调用点有两处：`AppNoteController.list`（列表摘要）与草稿列表（无标题草稿的展示行，见 §4.5）。

**第一步：块级剥离**（把 Markdown 源码变成可读纯文本）

| 语法 | 处理 |
| --- | --- |
| 围栏代码块 ` ```…``` ` | 整块移除 |
| 行内代码 `` `x` `` | 去反引号，保留内容 |
| 图片 `![alt](url)` | 移除 |
| 链接 `[text](url)` | 保留 `text` |
| 标题前缀 `#+ `、引用前缀 `> ` | 去前缀 |
| 列表前缀 `- ` `* ` `+ ` `1. `、任务列表 `- [ ]` | 去前缀 |
| 表格分隔行 `\|---\|---\|` | 移除 |
| 加粗 / 斜体 / 删除线标记 `**` `~~` | 去标记保留文字；`__` 仅在成对包裹时去除（避免破坏 `snake_case`） |
| HTML 标签 | 去标签保留文字 |
| 连续空白 | 折叠为单个空格 |

**第二步：截窗口**

| 情形 | 取值 |
| --- | --- |
| 有关键词且命中 | 以命中处为中心，前后各约 40 字，两端加省略号 |
| 有关键词但只命中标题 | 取正文**开头**约 120 字（标题本身由前端高亮） |
| 无关键词 | 取正文开头约 120 字 |
| 剥离后为空（如整篇只有代码块） | 回退为**原始文本**开头约 120 字，保证卡片不空白 |
| `content` 为空 | `excerpt` 为空串 |

边界若落在英文单词中间，最多向外扩 10 字符至最近的空白或标点（中文按字符切即可）。命中定位在**剥离后的纯文本**上做，大小写不敏感。

### 3.4 一处有意的取舍

列表 SQL 仍然 `select` 了 `content`，Java 侧算完摘要后把 `content` 置空再下发。收益在**响应层**（匿名访客的公网 payload、列表页不再渲染 Markdown），不在数据库读取。

**ponytail: 不为此改 SQL 列清单。** 若把 `content` 从 `selectSgjNoteVo` 里去掉，会连带打断共用该片段的 `selectSgjNoteById`（详情页与后台编辑弹窗），需要为列表单开一份列清单；而块级剥离本就无法用 SQL 表达，摘要在 Java 侧是必需的。笔记量上来后再评估「SQL 直接算摘要 + 列表不取全文」。

---

## 4. 草稿箱（FR-002-7）

### 4.1 数据模型：为什么用独立表

新建 `sgj_note_draft`，而不是给 `sgj_note` 加 `is_draft` 标志位。理由是失败模式不同：

- **同表加标志位**：草稿与正式笔记混在一张表，**每一处查询都必须记得排除草稿**。已核实的受影响查询有六处——`SgjNoteMapper` 的列表 / 详情 / 回收站，加 `SgjDashboardMapper` 的 `selectNoteMonthTrend` / `selectNoteTotal` / `selectNoteMonthCount` / `selectRecentNotes`（`:33/:43/:55/:152`）。今天都能改到，问题是今后每新增一个笔记查询都要再记得一次，而漏掉那次不报错，只让草稿出现在列表、首页统计或导出里；匿名路径漏掉即草稿公开可见。且现在没有单一收敛点可加保险：where 条件是逐查询内联的，`selectSgjNoteVo` 只管列不管过滤。
- **独立表**：现有查询物理上不可能返回草稿，泄露风险接近零。

代价是"发布"要跨表写（写入 `sgj_note` + 删除草稿行，见 §4.4），且 `title` / `content` 等字段在两处定义，将来改字段要改两边。这个代价是一次性的，而标志位方案的代价是长期的。

```sql
-- sql/update/20260917_note_draft.sql（同时并入 init_business.sql 供新库使用）
create table sgj_note_draft (
  draft_id    bigint(20)   not null auto_increment comment '草稿ID',
  note_id     bigint(20)   default null comment '编辑来源笔记ID；空=新笔记草稿（只有这类进草稿箱）',
  item_id     bigint(20)   default null comment '关联条目ID，空为独立笔记',
  title       varchar(200) default '' comment '标题（草稿可为空）',
  content     longtext     comment '正文（Markdown）',
  tags        varchar(500) default '' comment '标签，多个用英文逗号分隔',
  is_public   char(1)      default '0' comment '公开状态（保存后沿用到笔记）',
  create_by   varchar(64)  default '' comment '创建者',
  create_time datetime     comment '创建时间',
  update_time datetime     comment '更新时间',
  primary key (draft_id),
  key idx_sgj_note_draft_owner (create_by),
  key idx_sgj_note_draft_note (note_id)
) engine=innodb auto_increment=1 comment = '拾光记-笔记草稿表';
```

与 `sgj_note` 的两处差别及其理由：

1. **`title` 允许为空**：`sgj_note.title` 是 `not null`（`init_business.sql:138`），因为正式笔记必须有标题；草稿可以没有。
2. **不加 `item_id` 外键**：`sgj_note` 有 `fk_sgj_note_item … on delete cascade`。草稿是临时数据，条目物理删除时残留的 `item_id` 由前端回退为 `#id` 展示（与现有 `itemName` 缺失的处理一致），不必为此让删条目连带删草稿。同理**不加 `del_flag`**：草稿的删除是硬删除，不进回收站（避免出现第二个暂存区）。

### 4.2 接口

全部要求登录，**不加 `@Anonymous`**；一律按当前登录用户过滤，不使用 `resolveCreateBy()`（它对管理员与 `publicOwner` 返回 `null`，会让管理员读到所有人的半成品草稿，与待确认需求 D-18 的方向相反，见 [requirements/pending.md](../requirements/pending.md)）。这是本版唯一不沿用 `AppScopeHelper` 的接口组，需在代码注释中写明理由。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/note/draft/list` | 无参：当前用户的**新笔记草稿**列表（`note_id is null`），供草稿箱 |
| GET | `/app/note/draft/list?noteId=` | 有参：该篇笔记的未保存改动（供编辑页静默恢复） |
| PUT | `/app/note/draft` | upsert：带 `draftId` 则更新，不带则新建；返回 `draftId` 与 `updateTime` |
| DELETE | `/app/note/draft/{draftId}` | 硬删除 |

**不做**独立的"发布草稿"接口：发布仍走现有 `POST` / `PUT /app/note`，只是多带一个可选 `draftId`。

约束：

- `PUT` 新建时若该用户的**新笔记草稿**已达 20 份，拒绝并提示先清理；
- 按 `draftId` 更新 / 删除前校验 `create_by` 为当前用户，否则拒绝（不能删改他人草稿）；
- 发布时携带的 `draftId` 同样要校验归属，**且必须在保存成功的同一事务内删除**——若分成两次请求，中间失败会留下陈旧草稿，下次进编辑器静默恢复它就会覆盖已发布的正文。

### 4.3 写入与同步

打字时先写本地，再定期与服务端同步。本地缓冲是为了"断网、强杀、崩溃都不丢"，服务端才是跨设备的那一份。

| 时机 | 动作 |
| --- | --- |
| 表单变化后停手 1s | 写 `localStorage`（同步、瞬时成功） |
| 每 15s（有变化时） | `PUT /app/note/draft` |
| `visibilitychange`（转 hidden）、`pagehide`、路由离开 | 立即 `PUT` |
| 保存笔记成功 | 服务端同事务删除草稿（见 §4.4），前端同时清本地缓冲 |

**本地缓冲的结构与采用规则**

```
key : sgj:note:buf:{editorKey}      // editorKey = note:{noteId} | new
值  : { draftId, noteId, itemId, title, content, tags, isPublic, savedAt }
```

进入编辑器时：

1. 按身份拉服务端草稿（编辑态按 `noteId`；从草稿箱"继续写"按 `draftId`；全新按"无"）；
2. 取本地缓冲，**仅当 `draftId` 与 `noteId` 都与当前身份一致**时才参与比较（否则那是别的草稿的缓冲，不能套用）；
3. 两者取 `savedAt` / `updateTime` 较新者；本地较新时，立即把本地内容推一次服务端。

这样本地缓冲不会把"草稿 A 的最新内容"错用到"草稿 B"上。首次同步拿到 `draftId` 后，把它的值写回缓冲即可，无需迁移 key（`editorKey` 在新增态始终是 `new`）。

### 4.4 发布语义

| 动作 | 结果 |
| --- | --- |
| 新建笔记点「保存」 | `POST /app/note` + `draftId` → 插入 `sgj_note`，同事务删除草稿行 |
| 编辑已有笔记点「保存」 | `PUT /app/note` + `draftId` → 更新 `sgj_note`，同事务删除草稿行 |
| 草稿箱里点「删除」 | 硬删除该行 |
| 草稿箱里点「继续写」 | 进编辑页并带 `draftId`，与普通新增共用同一个编辑器 |

### 4.5 页面

**笔记列表页**（`views/front/note/index.vue`）：在筛选区上方插入一条入口条，仅当草稿数 > 0 时渲染。

**草稿箱页**（`views/front/note/draft.vue`，路由 `/note/draft`）：仅登录可进。每行显示

- 标题：为空时用 §3.3 的剥离工具取正文首行（与列表摘要同一套逻辑，不另写一份）；
- 关联条目名称（左联 `sgj_item`，与 `selectSgjNoteVo` 同一取法）、相对时间（如「3 分钟前」）；
- 操作：「继续写」「删除」。

### 4.6 边界与默认值

| 项 | 取值 | 说明 |
| --- | --- | --- |
| 草稿箱内容 | 仅 `note_id is null` | 编辑态草稿不进箱，只在进入那篇编辑页时静默恢复 |
| 编辑态恢复方式 | 静默恢复 + 页内状态条 | 「已恢复未保存的草稿 · 放弃草稿」——必须可见，否则用户会把草稿内容误认为已发布的正式内容 |
| 删除 | 硬删除 | 不进回收站 |
| 上限 | 新笔记草稿 20 份 | 超出时新建被拒并提示清理 |
| 过期清理 | **不做** | 不引入 quartz 任务；清理交给用户 |
| 后台 | 不做草稿页面 | 0.0.2 只有前台草稿箱 |

---

## 5. 前端设计

### 5.1 前台列表卡片（`views/front/note/index.vue`）

- 预览数据源由 `note.content` 改为 `note.excerpt`；
- 卡片不再渲染 Markdown，改为纯文本摘要，视觉保持不变（`max-height: 96px` + 底部渐隐）；
- 关键词高亮：把摘要按关键词切成片段数组，模板里用 `span` 循环渲染。**不拼 HTML 字符串、不走 `v-html`**，因此不产生 `escapeHtml` 需求，也不新增绕过 DOMPurify 的路径；
- 搜索框 placeholder 由「搜索笔记标题」改为「搜索标题与正文」；
- `previewContent()`（去首行 H1）随之删除——该职责已由后端摘要承担。

`components/ItemNotes/index.vue` 同源改造：直接使用 `excerpt`，删除本地 `noteSummary()`。至此前端三份重复的「Markdown 转纯文本」中，前台两份消失，仅后台摘要列保留一份（结论 2 的既定代价）。

### 5.2 编辑页（`views/front/note/edit.vue`）

- 草稿的写入、同步、恢复、放弃按 §4.3 / §4.6；
- 顶部状态条仅在"恢复了草稿"时出现，可关闭，关闭 ≠ 删除草稿（删除在草稿箱或状态条上的「放弃」）；
- 「取消」按钮走 §5.3 的同一道确认。

### 5.3 未保存离开确认

| 项 | 取值 |
| --- | --- |
| 判据 | 与**打开时的服务器快照**不同（新增态：任一字段非空即算有改动）。**不能**用「与草稿是否相同」——草稿总是最新的，那样写等于永不拦截 |
| 拦截出口 | 路由离开（`onBeforeRouteLeave`）、顶部「取消」、左上角返回、浏览器后退、关闭 / 刷新标签页（`beforeunload`） |
| 保存成功后 | 置 `dirty = false` 后跳转，**不得**弹确认 |
| 用户确认离开后 | 草稿**保留**（新增态可从草稿箱继续，编辑态下次静默恢复） |
| 文案 | 须说明内容已保存到草稿箱，而不是「未保存的修改将丢失」 |

### 5.4 代码语法高亮（`components/MarkdownViewer/index.vue`）

- 依赖：`highlight.js` + `marked-highlight`（`package.json` 新增两项，需在 PR 中说明理由）；
- 接入：`marked.use(markedHighlight({ hljs, langPrefix: 'hljs language-' }))`；
- 语言白名单：`highlight.js/lib/common`（约 35 种常用语言），不引全量；
- **只高亮显式标注且 `hljs.getLanguage()` 认识的语言**；无标注 / 不认识的原样输出。不启用 `highlightAuto`（长块明显变慢且易猜错）；
- 主题 CSS 全局引入（深色 token 主题，与现有 `pre` 的固定 `#282c34` 底色一致）。`pre` 的硬编码颜色本次不改，避免扩大 diff；
- 体积：前台列表改为纯文本摘要后，`views/front/note/index.vue` 不再 import `MarkdownViewer`，hljs 只落在「笔记详情」「笔记编辑」「后台笔记」三个懒加载路由，**首页与列表首屏不付这份体积**，因此静态引入即可，无需动态 import；
- 编辑页实时预览与详情页共用 `MarkdownViewer`，高亮自动同时生效。

---

## 6. 影响文件清单

**数据库**

| 文件 | 变更 |
| --- | --- |
| 新增 `sql/update/20260917_note_draft.sql` | 建表 `sgj_note_draft`，幂等脚本，参照 `update/20260909_item_photos.sql` 体例 |
| `sql/init_business.sql` | 同步加入建表语句供新库使用 |

**后端**

| 文件 | 变更 |
| --- | --- |
| `business/.../domain/SgjNote.java` | 新增 `keyword`、`excerpt`、`draftId` 三个非表字段 |
| 新增 `business/.../domain/SgjNoteDraft.java` | 草稿实体 |
| 新增 `business/.../mapper/SgjNoteDraftMapper.java` + `resources/mapper/business/SgjNoteDraftMapper.xml` | 草稿 CRUD，按 `create_by` 过滤 |
| `business/.../resources/mapper/business/SgjNoteMapper.xml` | `selectSgjNoteList` 增加括号包裹的 `keyword` 条件 |
| `business/.../service/impl/SgjNoteServiceImpl.java` | 新增静态摘要方法 |
| 新增 `business/.../service/ISgjNoteDraftService.java` + `impl/SgjNoteDraftServiceImpl.java` | 草稿服务，含上限与归属校验 |
| `admin/.../controller/business/AppNoteController.java` | `list` 生成 `excerpt` 并置空 `content`；`add` / `edit` 接受 `draftId` 并同事务删草稿 |
| 新增 `admin/.../controller/business/AppNoteDraftController.java` | 草稿箱四个接口，全部要求登录 |
| `admin/.../test/.../AppApiAuthIsolationSmokeTest.java` | 新增匿名 keyword 用例、草稿隔离用例 |
| 新增 `business/src/test/.../SgjNoteExcerptTest.java` | 摘要规则单测 |

**前端**

| 文件 | 变更 |
| --- | --- |
| `views/front/note/index.vue` | 改吃 `excerpt`、切片段高亮、删 `previewContent()`、改 placeholder、加草稿入口条 |
| 新增 `views/front/note/draft.vue` + `router/index.ts` 路由 | 草稿箱页 |
| 新增 `api/front/noteDraft.ts` + `types/api/front/noteDraft.ts` | 草稿接口与类型 |
| `components/ItemNotes/index.vue` | 改吃 `excerpt`，删 `noteSummary()` |
| `views/front/note/edit.vue` | 草稿（本地缓冲 + 同步 + 静默恢复）与离开确认 |
| `views/business/note/index.vue` | 搜索参数改 `keyword`，label 改「关键词」 |
| `types/api/front/note.ts` · `types/api/business/note.ts` | `keyword` 入参、`excerpt` / `draftId` 字段 |
| `components/MarkdownViewer/index.vue` | 接入 hljs + 主题 |
| 新增 `components/MarkdownViewer/__tests__/index.spec.ts` | 高亮与消毒断言 |

---

## 7. 测试计划

**后端**

| 测试 | 内容 |
| --- | --- |
| `SgjNoteExcerptTest`（新增，纯单测，无 Spring / 无 DB） | 剥离各类语法；窗口以命中处为中心；无命中取开头；命中标题时取开头；纯代码块笔记的回退；空正文；命中在首 / 尾 |
| `AppApiAuthIsolationSmokeTest`（补用例） | ①私密笔记正文含独有标记 → 匿名带 `keyword` 请求 → **0 命中**；回收站笔记同样不可命中。②草稿隔离：A 用户草稿对 B 不可见；**匿名访问草稿接口返回 401**（不是 200 空列表）；按他人 `draftId` 更新 / 删除被拒；带他人 `draftId` 发布被拒。③发布后草稿行消失 |

**前端**

| 测试 | 内容 |
| --- | --- |
| `MarkdownViewer` spec（新增，vitest + jsdom） | ` ```java ` 渲染后含 `span.hljs-*`（证明 class 未被 DOMPurify 误伤）；无语言标注不报错且不高亮；`<script>` / `onerror` 仍被消毒 |

**回归**

- 前台：标签筛选、条目筛选（`itemId`）、分页与「加载更多」、匿名仅见 `is_public='1'`、回收站恢复与彻底删除；
- 后台：笔记管理页列表与摘要列、CSV / JSON 导出（`content` 列必须仍有值）、条目详情的关联笔记列表；
- 详情页：正文全文、大纲锚点、编辑入口；
- 首页：`noteTotal` 与「最近笔记」不受草稿影响（独立表的直接收益，需实测确认）。

---

## 8. 验收标准

**检索与摘要**

- [ ] 搜索一个只存在于正文、标题不含的词，能命中该笔记；
- [ ] 命中摘要常显且以命中处为中心，关键词高亮，且高亮不经 `v-html`；
- [ ] 无关键词时卡片显示正文开头摘要，纯代码块笔记不出现空白卡片；
- [ ] 匿名带 `keyword` 搜索博主私密笔记的正文 → 0 命中（回归用例固化）；
- [ ] 后台笔记管理页用同一关键词可搜到正文命中项，label 显示「关键词」；回收站仍只搜标题。

**草稿与草稿箱**

- [ ] 新增笔记写一半后离开，草稿出现在草稿箱；从草稿箱「继续写」能接着写，关联条目与离开前一致；
- [ ] 编辑已有笔记写一半后离开，该篇**不出现**在草稿箱；再次进入该篇编辑页时静默恢复并显示状态条；
- [ ] 草稿箱点「删除」后该草稿消失；删除不进回收站；
- [ ] 保存笔记成功后，草稿从草稿箱消失，且再次进入编辑页不会恢复出已发布内容；
- [ ] 跨设备：甲设备写一半（≤15s），乙设备进草稿箱可见并能继续；
- [ ] 断网时继续输入（本地缓冲承接），恢复网络后草稿同步到服务端；
- [ ] 匿名请求草稿接口返回 401；甲用户看不到乙用户的草稿；
- [ ] 达到 20 份新笔记草稿时新建被拒并提示清理。

**离开确认**

- [ ] 有未保存改动时，「取消」、返回、后退、关闭标签页均有确认；保存成功后跳转不弹确认；
- [ ] 桌面端：关闭标签页弹浏览器原生确认；
- [ ] 移动端：返回有确认；**不要求**关闭页面弹确认，改为要求「重新进入时草稿可恢复」；
- [ ] 确认离开后草稿仍在（新增态可从草稿箱继续）。

**高亮**

- [ ] 笔记正文中的代码块有语法高亮，渲染结果未被消毒误伤（spec 固化）；
- [ ] 无语言标注与未知语言的代码块原样显示，不报错；
- [ ] 详情页与编辑页实时预览均生效，暗色主题下配色协调。

**不可回归**

- [ ] 标签筛选、条目筛选、分页、公开性过滤、回收站恢复 / 彻底删除全流程无回归；
- [ ] 后台导出 CSV / JSON 的「内容」列仍有值；
- [ ] 首页 `noteTotal` 统计与「最近笔记」卡片不受草稿影响。

---

## 9. 实施顺序（小步提交）

1. `docs:` 本文 + 需求文档重组（路线图拆分）；
2. `feat:` 建表脚本（`sql/update/20260917_note_draft.sql` + `init_business.sql`）与草稿实体 / Mapper / Service；
3. `feat:` 后端 `keyword` 检索（含括号写法）→ 补匿名 keyword 回归用例，`mvn -pl shiguangji-admin -am test`；
4. `feat:` 摘要生成静态方法 + `SgjNoteExcerptTest`，手工查库校验若干真实笔记的摘要输出；
5. `feat:` 前台列表改吃 `excerpt` + 切片段高亮；`ItemNotes` 同步；
6. `feat:` 后台搜索参数与 label；
7. `feat:` 草稿接口（四个端点 + 上限与归属校验）与隔离回归用例；
8. `feat:` 编辑页草稿：本地缓冲 + 定时同步 + 静默恢复 + 状态条；
9. `feat:` 发布时同事务删草稿（含归属校验）；
10. `feat:` 草稿箱页面与笔记列表页入口条；
11. `feat:` 未保存离开确认（路由 + `beforeunload` + 取消按钮）；
12. `feat:` 语法高亮 + `MarkdownViewer` spec；
13. 提交前运行：前端 `npx vue-tsc --noEmit`、`npm run build:prod`、`npm run test:unit`；后端 `mvn -pl shiguangji-admin -am test`。

---

## 10. 非目标

- 不做笔记版本历史、分类 / 文件夹、导入 / 导出（原 §3.4 不变）；
- 不做全文索引调优（个人规模下 LIKE 足够，接口形状已为将来换 FULLTEXT + ngram 留好）；
- 不做前台全局搜索（转 0.0.5，见 §1 结论 18）；
- 不做「想法」等新记录类型；
- **不做草稿的自动过期清理任务**（不引入 quartz 定时）；
- **不做后台草稿页面**（草稿只在正文侧）；
- 不在本版把后台摘要列与导出改为后端摘要（前台优先，后台保持现状）。
