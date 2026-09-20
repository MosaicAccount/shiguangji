# 笔记收口（0.0.2） - 技术决策与设计图

> **关联文档**：[0.0.2 需求：收口笔记](../requirements/0.0.2-note.md)（详细需求分析、边界与验收清单在需求文档）
> **文档状态**：详细设计。§1 现状 → §2 技术决策 → §3 设计图，评审通过后进入编码。
> **分支**：`feat/note-0.0.2`，完成后合回 `develop`。
> **表结构变更**：新表 `sgj_note_draft`（FR-002-7 草稿箱）+ `sgj_note` 新增全文索引（FR-002-1，见结论 33）。

---

## 1. 现状与前提

### 1.1 与本版相关的现状

| 事项 | 现状 | 位置 |
| --- | --- | --- |
| 列表检索 | 仅 `title like` | `SgjNoteMapper.xml:49-51` |
| 列表下发字段 | **含完整 `content`** | `SgjNoteMapper.xml:24` |
| 列表卡片渲染 | 每张卡片把正文渲染成 Markdown；另有 `previewContent()` 剥掉与标题相同的首个 H1，避免卡片标题重复 | `views/front/note/index.vue:61,176-181` |
| 前台列表的另一个消费者 | 条目详情的关联笔记列表，自己也有一份「Markdown 转纯文本」`noteSummary()` | `components/ItemNotes/index.vue:13,62-71` |
| markdown 渲染入口 | 全仓库只有一处 import `marked` | `components/MarkdownViewer/index.vue:7` |
| Markdown 样式写法 | 全部走 `:deep()`，因此 scoped 样式能作用到 `v-html` 内容（新增高亮规则必须沿用同一写法） | `components/MarkdownViewer/index.vue:30-178` |
| 依赖 | marked 12.0.2 **已移除** `highlight` 选项 | 原需求文档写「唯一的新增前端依赖」，实际是**两个**包 |
| 日期序列化 | `yyyy-MM-dd HH:mm:ss`，`time-zone: GMT+8`，`BaseEntity` 上另有 `@JsonFormat` | `application.yml:96-98`、`BaseEntity.java:28,35` |
| 请求错误处理 | **每个**失败请求都会弹全局提示；401 会弹「登录状态已过期」确认框；PUT 有 1s 防重复提交 | `utils/request.ts:41-68,86-125` |
| 相对时间工具 | 已有 `formatTime()`（刚刚 / N 分钟前 / N 小时前），但只接受时间戳或数字串，喂 `yyyy-MM-dd HH:mm:ss` 会得到 `NaN月NaN日` | `utils/index.ts:30-66` |
| 前台布局 | 移动端有固定底部标签栏（60px，z-index 200），页面需自留 `padding-bottom` | `layout/front/index.vue:42-46,308-352` |
| 免登录白名单 | `/note` 与 `/note/detail` 公开只读，`/note/edit` 不在白名单 | `permission.ts:22` |
| 草稿存储 | 无任何草稿结构；`sgj_note` 无草稿标志字段 | `sql/init_business.sql:135` |

### 1.2 几个容易走错的前提

| 容易踩的前提 | 实际是什么 |
| --- | --- |
| 「`HeaderSearch` 组件没人用，接上前台就能做全局搜索」 | 它被后台侧栏引用（`layout/components/Sidebar/index.vue:39`），是 Fuse.js 的**菜单**搜索、零后端调用，而且前台没有搜索位 |
| 「列表检索放后端是为了省流量」 | 列表接口**本来就**返回完整正文；重点不是「前端算还是后端算」，是顺手停掉全文下发 |
| 「语法高亮是唯一新增的前端依赖」 | 是**两个**包（`highlight.js` + `marked-highlight`）；文件导入只做 md，不引入解析依赖 |
| 「关闭标签页也能弹自定义确认」 | iOS Safari 的 `beforeunload` 基本不触发，移动端做不到，验收要按平台拆开 |
| 「正文可以随便长」 | 服务端有 20000 字上限（`CONTENT_MAX_LENGTH`），而且列表与首页会把全文读出来（结论 32） |

---

## 2. 技术决策

### 2.1 结论（1~19）：检索、摘要、草稿、高亮

| # | 议题 | 结论 |
| --- | --- | --- |
| 1 | 命中片段由谁生成 | **后端**。前台列表接口不再下发全文，改下发摘要 |
| 2 | 摘要的作用范围 | 只改前台 `/app/note/list`；后台列表、回收站、导出均不动 |
| 3 | 摘要的显示时机 | **常显**。无关键词取正文开头，有关键词取命中窗口 |
| 4 | 高亮实现 | 前端按关键词**切片段渲染**，不产生 HTML 字符串，不走 `v-html` |
| 5 | 检索参数 | 新增 `keyword`；`title` 保留「仅标题」原义不动。回收站不纳入 |
| 6 | 前后台搜索框文案 | 后台笔记管理页 label 由「标题」改为「关键词」 |
| 7 | 防越权短路 | 搜索条件不能绕过权限过滤；必须补一条匿名 + keyword 的回归用例。用 FULLTEXT 后不需要 `or`，见 §2.3 |
| 8 | 草稿存储 | **服务端独立表** `sgj_note_draft`（不混入 `sgj_note` 加标志位），现有六处查询一行不改 |
| 9 | 草稿写入 | **本地缓冲 + 服务端同步**：停手 1s 写本地 `localStorage`，每 15s 与页面离开时推服务端 |
| 10 | 冲突规则 | **时间戳只由服务端给**：本地缓冲存 `baseUpdateTime`（最近一次推送成功时服务端返回的 `updateTime`）+ `dirty` + 本地递增 `seq`，**不再存客户端 `savedAt`**。进编辑器时三态：`dirty` 为真 → 用本地并立即推送；`dirty` 为假且 `baseUpdateTime` 与服务端 `updateTime` 相等 → 两份等价；不等 → 用服务端。身份不一致的本地缓冲不采用（结论 26） |
| 11 | 草稿箱边界 | 只装**新笔记草稿**（`note_id` 为空）；编辑已有笔记的未保存改动也存，但不入箱，进那篇编辑页时静默恢复 |
| 12 | 草稿可见范围 | **只本人可见**：按当前登录用户过滤，管理员不可读他人草稿；接口不加 `@Anonymous` |
| 13 | 草稿箱入口 | 笔记列表页入口条（有草稿才显示，**数量取草稿列表长度**）+ 独立页 `/note/draft`；删除为**硬删除**；未登录访问 `/note/draft` **沿用现有路由守卫**（`isPathMatch` 是锚定正则，`/note` 匹配不到 `/note/draft`，会跳到 `/login?redirect=/note/draft`），不另做「提示后回列表页」 |
| 14 | 草稿数量 | **不设数量上限**：过去那个 20 份上限只挡得住「身份丢失（退出登录 / 换设备 / 清浏览器数据）后缓慢增长」的行数，却会在满额后把用户**当下正在写的内容**挡在本地（服务端拒绝，前端只会显示「同步失败（可重试）」，用户在编辑页也没有出口）；草稿的出口只有「手动删」「保存成功后删」「笔记被删时一并删」，**不做**自动过期清理任务。**空表单不建草稿的判据只看 `title` 与 `content`**（两者都空即不建；此时只改了条目 / 标签 / 公开状态就离开，元数据不保留，是这一版接受的代价——离开确认文案须随之调整，结论 20 下方的 §2.9 表）。**草稿正文复用 `CONTENT_MAX_LENGTH`（100000）**，编辑器内提前拦截并明确提示，避免「自动保存被静默拒绝、状态条永远停在『仅本地待同步』」这种哑失败 |
| 15 | 发布与草稿的关系 | 保存笔记时若带 `draftId`，服务端在**同一事务**内删除该草稿；带的是**他人的** `draftId` 则整个请求失败（不是忽略）。删除动作放在 **service 层**（`SgjNoteServiceImpl`）而非控制器，否则后台 `SgjNoteController` 的软删路径会漏（见结论 25） |
| 16 | 离开确认 | 判据为「与打开时的服务器快照不同」；取消、返回、后退、关闭标签页均拦；确认离开后**保留**草稿 |
| 17 | 语法高亮 | `highlight.js` + `marked-highlight`；无语言标注的代码块不高亮 |
| 18 | 全局搜索（D-4） | **不纳入 0.0.2**，转 0.0.5（FR-005-5） |
| 19 | 交付方式 | 本文 + 两端测试；评审后开工 |

### 2.2 结论（20~40）：正文存储、上限、全文索引、文件导入导出、草稿写入边界

> 「已拍板」= 已定的做法；「缺陷」= 代码里已有的问题，修法唯一；「约束」= 实现时必须遵守的边界，无替代方案；「收益」= 某个决定带来的附带好处；「已废弃」= 方案调整后不再适用。各条对应的验收项见需求文档 §3.3、§5。

| # | 性质 | 议题 | 结论 |
| --- | --- | --- | --- |
| 20 | 缺陷 | 摘要与标题去重 | 后端摘要须承接 `previewContent()` 的职责：在剥离后、截窗前，移除与 `title` 相同的首个一级标题（仅当其为正文首个 H1 且文本 trim 后与标题全等）。否则改吃 `excerpt` 后每张卡片标题重复两次。同批处理卡片的底部渐隐（改为仅在溢出时显示） |
| 21 | 缺陷 | 草稿请求对匿名 / 失效 token 静默 | `/note` 是免登录路由，且 `isLogin` 只判断 token 是否存在（过期 token 同样为真）。入口条与草稿请求必须：`isLogin` 为假时不发；失败也不触发全局提示与「登录状态已过期」弹窗 |
| 22 | 已拍板 | 同步静默 + 重推 + 关防重 | 后台自动同步（15s 定时、`pagehide`、`online`）带静默标记与 `repeatSubmit: false`；`online` 事件立即重推一次；离开时的推送为**尽力而为**，先同步写本地缓冲，未送达由下次进编辑器补推兜底 |
| 23 | 已拍板 | 编辑页同步状态条三态 | 三态：**已同步到草稿箱** / **仅本地待同步**（离线或失败）/ **已恢复未保存的草稿**。恢复提示优先且可关闭，关闭后显示同步态 |
| 24 | 已拍板 | 跨设备并发 | 接受**后写覆盖**（LWW），不加乐观锁。`baseUpdateTime` 与服务端 `updateTime` 不等时**仍然以本地为准推上去**——否则离线写的内容会在进编辑器时被丢弃，与 #32 的验收「断网时接着写、网络恢复后自动同步」直接冲突。升级路径因此已半就绪：`baseUpdateTime` 本来就在本地（结论 10），只需在 PUT 里带上、服务端发现版本已变则返回业务码，编辑页提示冲突 |
| 25 | 已拍板 | 编辑态草稿生命周期 | 不设数量上限。清理点共 **三处**，全部走同一个 service 方法 `deleteByNoteIds`：① 笔记软删（`SgjNoteServiceImpl.deleteSgjNoteByIds`，一处即可覆盖前台 `AppNoteController` 与后台 `SgjNoteController` 两条路径）；② 笔记彻底删除；③ **条目彻底删除**（`SgjItemServiceImpl.purgeSgjItemByIds`，该方法当前**没有** `@Transactional`，需一并补上）。第 ③ 处**必须先删草稿再删条目**——反过来 `delete from sgj_item` 会先经 `fk_sgj_note_item` 级联删掉 `sgj_note`，此时按 `item_id` 反查 `note_id` 已查不到任何行，草稿一行都不会删，而且不报错。**不加 `note_id` 外键**：外键管不到软删这种 UPDATE，仍需结论 40 的存活校验兜底，故不引入级联耦合，改为显式清理 |
| 26 | 缺陷 | 本地缓冲按用户隔离 | key 由 `sgj:note:buf:{editorKey}` 改为 `sgj:note:buf:{username}:{editorKey}`；登出时清理；仅 `username`/`draftId`/`noteId` 三者都与当前身份一致时才参与比对。缓冲值增加 `baseUpdateTime` 与 `dirty`，**不再存客户端 `savedAt`**（结论 10）；另需一个本地递增 `seq`（内存计数器，不是时钟）——推送时记下 seq，响应回来**只有 seq 未变才允许清 `dirty`**，否则会把「推送在飞行中用户又打的字」误判成已同步 |
| 27 | 缺陷 | 日期字符串解析 | 后端 `yyyy-MM-dd HH:mm:ss`（GMT+8）。新增解析函数转 epoch 后**仅用于展示**（草稿箱相对时间）；**不得**把该字符串直接交给 `utils/index.ts:formatTime()`（会得到 `NaN月NaN日`），也不得直接 `new Date(str)`（Safari 得 Invalid Date）。恢复比对路径**不再解析日期**（结论 10 已改为版本 + `dirty`） |
| 28 | 缺陷 | 列表页移动端断点失效 | `index.vue:453-466` 的媒体查询选择器是 `.toolbar`，而元素类名是 `.filter-bar`（`:13,335`），该规则从未生效（移动端搜索框仍为 260px 右对齐）。本版要改这块 CSS，顺手把选择器改对 |
| 29 | 已废弃 | LIKE 通配符不转义 | 改用 FULLTEXT 后不再走 LIKE，此项不再适用（见结论 33） |
| 30 | 约束 | `beforeunload` 文案不可自定义 | 浏览器原生确认框只显示通用文案，所以文案要求只针对站内弹窗 |
| 31 | 已拍板 | **正文存储定案** | 正文继续以 Markdown 存 `sgj_note.content`（`longtext`）；文件只作**导入源与导出产物**，二进制一律走 `FileStorageService`。理由：本版的写入模式（每 15s 双端自动保存）、读模式（检索 + 摘要）、元数据（草稿 / 回收站 / 公开性）都是数据库主场；文件化要重建检索、事务、权限、备份四项能力，而收益只有「数据可携带」一项，用导入导出即可获得。**文件是门，不是仓库** |
| 32 | 已拍板 | 正文上限与列表瘦身 | `CONTENT_MAX_LENGTH` 20000 → **100000**（#14 的前置条件：用户从 Obsidian 之类导入的 md 常常是长篇，几万字很常见）。同时**前台列表不再取全文**，改由 SQL 算出「命中窗口 + 无命中前缀」两段短文本（`substring(content, greatest(1, locate(kw, content) - 40), 200)` / `left(content, 400)`），回传几百字符而非十万字；扫描仍在引擎内发生（LIKE/FTS 本来就要扫）。命中落在窗口外时降级为取开头。**同批**：首页 `selectRecentNotes` 也在 select `content`（`SgjDashboardMapper.xml:144`）却从不显示，一并去掉该列 |
| 33 | 已拍板 | 检索改用 FULLTEXT + ngram | 索引 `FULLTEXT KEY ft_note_title_content (title, content) WITH PARSER ngram`，查询 `match(title, content) against(#{keyword} in boolean mode)`。**必须用布尔模式**：实测自然语言模式会把 `mysql` 命中到只含 `sql` 的行（ngram 把查询词也切词 + OR 语义）。`ngram_token_size=2` 是**只读启动变量**（实测 `ERROR 1238`），改动必须重建索引；token 大小为 2 导致**单个汉字搜不到**，搜索框需最小长度约束与提示 |
| 34 | 已拍板 | #14 纳入 0.0.2（**仅 md**） | 范围：**导入 md + 导出 md**；**Word（docx）转后续版本**（见 §2.13）。流程：前端 `FileReader` 本地读文件 → 解析并剥离 front-matter → **跳编辑页预填** → 用户补完关联条目 / 标签 / 公开状态 → **点保存时才上传原件**（结论 37）。选「跳编辑页确认」是因为：不新增预览界面、复用编辑页与其草稿机制（导入后未保存离开不会丢）、解析失败不会在库里留下垃圾笔记、元数据在保存前可设（这正是「纳入系统管理」的动作）。导出写 front-matter（含 `noteId`），重新导入时若识别到 `noteId` 则问「更新《X》/ 新建一篇」 |
| 35 | 约束 | 导入产物必须干净 | md 源文件普遍带 **YAML front-matter**（Obsidian / Jekyll / Hugo 都写），必须在入库前剥离：否则摘要首行会显示成 `---`，且 `title:` / `tags:` 这些键会进 ngram 索引变成垃圾 token。摘要剥离规则相应增加「YAML front-matter 整块移除」。md 里的图片引用**无法转存**（系统拿不到相对路径指向的文件）：相对路径 `![](./a.png)` 与 Obsidian 的 `![[a.png]]` 必须提示而不静默丢失；`data:` base64 内嵌图片本版不处理 |
| 36 | 收益 | 草稿独立表挡住索引重写 | 草稿表**没有**全文索引，所以每 15s 的自动保存不触发任何 FTS token 维护，只有真正「保存」发布时才写 `sgj_note.content`。如果用「同表加 `is_draft`」，十万字笔记每 15s 要重写约十万个 token。这是独立表（结论 8）的附带好处 |
| 37 | 已拍板 | 原件留存与孤儿对账 | 原件在**用户点保存时**上传（不是导入时），路径写入 `sgj_note.source_file`；**关键：必须同步给 `FileReferenceMapper.selectReferencedStorageKeys()` 加一行 `union select source_file from sgj_note where source_file like '/profile/%'`**——该 mapper 的注释已写明「新增引用列时必须同步扩展，否则对应文件会被误判为孤儿」。这个机制白送三条正确行为：① 7 天 GRACE（`FileCleanupTask:47`）覆盖上传与入库之间的窗口；② 保存失败或用户放弃时，原件由清理任务回收；③ 笔记彻底删除后行消失，原件自动成为孤儿被回收。上传失败**只放弃原件、不阻断保存**。已知代价：导入后未保存就离开、事后从草稿恢复再保存时没有原件（`File` 对象已随页面消失） |
| 38 | 已拍板 | 导入的标签怎么对接 | front-matter 的 `tags` 里，**在标签表里的预填进编辑页；不在表里的忽略并提示**「N 个标签不在标签表中，已忽略」。尊重「词表由运维维护、仅可选择不可手输」的既有约束（README 的跨版本原则）；用户在编辑页可自行补选已知标签。不自动建标签 |
| 39 | 已拍板 | 两个下载入口的分工 | 后台笔记管理页每行给两个动作：**「下载原件」**（取回当初上传的那份，`source_file` 为空时禁用）与**「导出 Markdown」**（用当前正文 + front-matter 现生成，含 `noteId`）。**往返必须用「导出 Markdown」**——原件不含 `noteId`、也不含之后在网页上的修改，拿它重新导入会新建一篇重复笔记，UI 文案须写明这一点。前台（详情页）**不做**下载 / 导出入口 |
| 40 | 缺陷 | 草稿写入的存活校验与归属覆盖 | `PUT /app/note/draft` 带 `noteId` 时，必须先校验该笔记**存在且 `del_flag='0'`**，否则拒绝并返回可识别错误（编辑页提示「笔记已被删除」）。缺了它，编辑页在笔记被删后仍会继续写入：软删后写回一行挂在回收站笔记上的草稿，彻底删除后写回一行永久孤儿（清理发生在删除那一刻，写入发生在之后，窗口随编辑页那个标签页存活而敞开）。另外 `SgjNoteDraft` 若继承 `BaseEntity`，`createBy` 是**客户端可绑定**字段，控制器必须**无条件覆盖** `draft.setCreateBy(SecurityUtils.getUsername())`——写成「为空才填」则 `?createBy=他人` 即可读他人草稿；改 / 删的归属判断也要拿库里那行的 `create_by` 比，不能信请求体 |

### 2.3 检索的两条安全与语义边界

**（1）匿名越权短路——采用 FULLTEXT 后风险自然消失，但回归用例保留**

`selectSgjNoteList` 的 where 段（`SgjNoteMapper.xml:38-56`）全是 AND 串联，**MyBatis 不保护 `or` 的优先级**。若回退到 LIKE 方案并写成：

```xml
and n.title like concat('%', #{keyword}, '%') or n.content like concat('%', #{keyword}, '%')
```

实际语义是 `((del_flag='0' AND create_by=? AND is_public='1' AND title LIKE) OR content LIKE)`：正文命中即返回，`del_flag`、`create_by`、`is_public` 全部被短路。匿名访客因此能搜到**博主私密笔记**与**回收站笔记**（`maskNotesForGuest` 只清 `remark`，正文照旧下发）。若用 LIKE，必须写成 `and (n.title like ... or n.content like ...)`。

**采用 FULLTEXT 后不再需要 `or`**——标题与正文在同一个 `match(title, content)` 里，短路风险从根上消失。但匿名 keyword 的回归用例仍须保留：它守的是「匿名不得命中私密与回收站」这个不变量，与实现方式无关（见 §2.11）。

**（2）布尔模式的运算符会被解释（实测）**

用 `IN BOOLEAN MODE` 才拿得到接近 LIKE 的字面语义（见结论 33），但布尔模式自带运算符。在本地 MySQL 9.7.1 上实测：

| 输入 | 实测结果 |
| --- | --- |
| `+测试` | 被当作「必须包含」，而非字面加号 |
| `-测试` | 前导减号使整个查询**返回 0 行**（最危险的用户可见行为：搜含减号的文本得到空白） |
| `"笔记`（未闭合引号） | 不报错，正常返回 |
| `笔记*` | 不报错，正常返回 |
| `"+测试"`（想用引号取字面量） | **无效**——ngram 会把符号丢掉，等价于搜 `测试` |

结论：**包引号屏蔽不了运算符**。要在传参前把布尔运算符字符（`+ - * " ( ) ~ < > @`）从用户输入里剔除或替换为空格——ngram 本来就忽略这些符号，剔除不损失匹配能力，但能避免 `-` 造成空结果、`+` 改变语义。

**草稿箱引入了同类的风险面**：草稿是未完成的私密文字，一旦有任何一个查询忘掉过滤条件，它就出现在公开列表里。这正是选择「独立表」而非「同表加 `is_draft`」的原因——独立表让现有六处查询物理上不可能返回草稿（见 §2.4）。

### 2.4 数据模型：为什么用独立表

新建 `sgj_note_draft`，而不是给 `sgj_note` 加 `is_draft` 标志位。理由是失败模式不同：

- **同表加标志位**：草稿与正式笔记混在一张表，**每一处查询都必须记得排除草稿**。已核实的受影响查询有六处——`SgjNoteMapper` 的列表 / 详情 / 回收站，加 `SgjDashboardMapper` 的 `selectNoteMonthTrend` / `selectNoteTotal` / `selectNoteMonthCount` / `selectRecentNotes`（`:33/:43/:55/:152`）。今天都能改到，问题是今后每新增一个笔记查询都要再记得一次，而漏掉那次不报错，只让草稿出现在列表、首页统计或导出里；匿名路径漏掉即草稿公开可见。且现在没有单一收敛点可加保险：where 条件是逐查询内联的，`selectSgjNoteVo` 只管列不管过滤。
- **独立表**：现有查询物理上不可能返回草稿，泄露风险接近零。

代价是"发布"要跨表写（写入 `sgj_note` + 删除草稿行，见 §2.8），且 `title` / `content` 等字段在两处定义，将来改字段要改两边。这个代价是一次性的，而标志位方案的代价是长期的。

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
  -- 每篇笔记最多一份编辑态草稿。note_id 为空时 NULL 在唯一索引里互不相等，
  -- 所以「新笔记草稿不限量」是这个约束自带的语义，不是特例（见 §2.5 的 upsert 规则）
  unique key uk_sgj_note_draft_owner_note (create_by, note_id),
  key idx_sgj_note_draft_owner (create_by),
  key idx_sgj_note_draft_note (note_id)
) engine=innodb auto_increment=1 comment = '拾光记-笔记草稿表';
```

与 `sgj_note` 的三处差别及其理由：

1. **`title` 允许为空**：`sgj_note.title` 是 `not null`（`init_business.sql:138`），因为正式笔记必须有标题；草稿可以没有。
2. **不加 `item_id` 外键**：`sgj_note` 有 `fk_sgj_note_item … on delete cascade`。草稿是临时数据，条目物理删除时残留的 `item_id` 由前端回退为 `#id` 展示（与现有 `itemName` 缺失的处理一致），不必为此让删条目连带删草稿。同理**不加 `del_flag`**：草稿的删除是硬删除，不进回收站（避免出现第二个暂存区）。
3. **`note_id` 也不加外键**：`on delete cascade` 覆盖得了物理删除，但覆盖不了软删（那是 UPDATE），所以仍然必须靠结论 40 的存活校验兜底——两个机制职责重叠却谁都不完整。改为**显式清理**（结论 25 的三处清理点，共用一个 `deleteByNoteIds`）：少一处隐式级联，多一处能读的代码，代价是清理点从一处变三处，必须一起改。

索引方面：`unique key uk_sgj_note_draft_owner_note (create_by, note_id)` 是**并发兜底**，写入路径仍是 §2.5 的「先查后写」——但要 catch 唯一键冲突后转为更新，否则两台设备同时推同一篇会产生第二行。`create_by` 单列索引与 `note_id` 单列索引都保留：前者支撑草稿箱列表，后者供按笔记反查。

### 2.5 接口

全部要求登录，**不加 `@Anonymous`**；一律按当前登录用户过滤，不使用 `resolveCreateBy()`（理由见需求 FR-002-7 边界 2）。这是本版唯一不沿用 `AppScopeHelper` 的接口组，需在代码注释中写明理由。

**`createBy` 必须无条件覆盖**：`SgjNoteDraft` 若继承 `BaseEntity`，`createBy` 就是客户端可绑定字段。控制器只能写 `draft.setCreateBy(SecurityUtils.getUsername())`，**不能**写成「为空才填」——否则 `?createBy=他人` 就能读到别人的草稿。改 / 删的归属判断要拿库里那行的 `create_by` 比，不信请求体（结论 40）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/app/note/draft/list` | 无参：当前用户的**新笔记草稿**列表（`note_id is null`），供草稿箱。**只回 `excerpt`，不回 `content`**；`itemName` 左联 `sgj_item` 带出（查不到回退 `#id`，与笔记列表一致） |
| GET | `/app/note/draft/list?noteId=` | 有参：该篇笔记的未保存改动（供编辑页静默恢复），取最新一行 |
| GET | `/app/note/draft/{draftId}` | 按 id 取单条草稿（**含完整 `content`**），供草稿箱「继续写」与冷启动的 `/note/edit?draftId=`。字面量 `/list` 优先于 `/{draftId}` 匹配，不冲突 |
| PUT | `/app/note/draft` | upsert：带 `draftId` 则更新，不带则新建。带 `noteId` 时按 owner + noteId 先查后写，**撞唯一键冲突则 catch 后转为更新**；**带 `noteId` 时必须先校验该笔记存在且 `del_flag='0'`**，否则拒绝并返回可识别错误（编辑页提示「笔记已被删除」，结论 40）。返回 `draftId` 与 `updateTime`——**`updateTime` 必须回查数据库取 `update_time`，不能用 Java 的 `new Date()`**，否则客户端存进 `baseUpdateTime` 的就是第三个钟，结论 10 白修 |
| DELETE | `/app/note/draft/{draftId}` | 硬删除 |

草稿 upsert **不加 `@Transactional`**：单条 insert/update 本就不需要，而「先查后写 + catch 冲突转更新」这个 catch 一旦跨 `@Transactional` 边界，Spring 会把外层事务标记为 rollback-only，反而失败。

**不做**独立的"发布草稿"接口：发布仍走现有 `POST` / `PUT /app/note`，只是多带一个可选 `draftId`。

其余接口变更：

| 接口 | 变更 |
| --- | --- |
| `GET /app/note/list` | 新增 `keyword` 入参；响应**不再含 `content`**，改为 `excerpt`（只回传几百字符的短文本：命中窗口或前缀，结论 32）；有命中时摘要以命中处为中心 |
| `GET /app/note/{noteId}` | 不变（详情页仍需要全文） |
| `POST` / `PUT /app/note` | 新增可选 `draftId`：保存成功后**同事务**删除该草稿；带**他人的** `draftId` 则整个请求失败（不是忽略）。删除动作放 **service 层**（`SgjNoteServiceImpl`，需加 `@Transactional`），不放控制器 |
| `DELETE /app/note/{noteId}`、回收站彻底删除、**后台 `DELETE /business/note/{noteIds}`** | 删除该笔记的草稿行（结论 25）。清理写在 `SgjNoteServiceImpl.deleteSgjNoteByIds` / `purgeSgjNoteByIds` 里，两个控制器自动覆盖 |
| `DELETE /app/item/{itemId}` 的彻底删除（`purgeSgjItemByIds`） | **先删该条目下所有笔记的草稿，再删条目**——顺序不能反（结论 25）；该方法当前无 `@Transactional`，需一并补 |
| `GET /business/note/list` | 新增 `keyword` 入参；响应**保持含 `content`**（后台摘要列与 CSV / JSON 导出依赖它） |
| 回收站两个接口 | 不变（表格无内容列，命中理由不可见，故不纳入正文检索） |

`SgjNote` 域对象新增四个**非表字段**（与既有的 `itemName` 同类，无需 DDL）：

| 字段 | 用途 | 说明 |
| --- | --- | --- |
| `keyword` | 入参 | 标题 **OR** 正文模糊匹配；`title` 保持「仅标题」原义 |
| `excerpt` | 出参 | 前台列表摘要，纯文本（**草稿列表也用它**） |
| `hitTotal` | 出参 | 出现次数，供「文中出现 N 次」小字（仅 >1 处时显示，评审结论见 §2.9：多命中不轮播）。统计口径 = 标题 + 剥离前言后的正文、并去除与标题重复的首个一级标题行——与详情页「标题 + 正文」高亮口径一致，列表次数 = 详情可跳转处数 |
| `draftId` | 入参 | 保存笔记时携带，用于同事务删除对应草稿 |

`SgjNoteDraft` 域对象同样需要 `itemName`（左联 `sgj_item` 带出）与 `excerpt`（无标题草稿展示首行）两个**非表字段**，复用 §2.6 的静态摘要方法，不另写一份。

### 2.6 摘要生成规则

实现位置：`SgjNoteServiceImpl` 的静态方法（对齐既有先例 `SgjItemServiceImpl.parsePhotoUrls`，`shiguangji-business` 模块无 `utils` 包，不新开包）。调用点有两处：`AppNoteController.list`（列表摘要）与草稿列表（无标题草稿的展示行）。

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
| YAML front-matter（`---` 包裹的头部） | **整块移除**（结论 35）：导出的 .md 带 front-matter，往返导入时必须剥离，否则摘要首行会显示成 `---` |
| 加粗 / 斜体 / 删除线标记 `**` `~~` | 去标记保留文字；`__` 仅在成对包裹时去除（避免破坏 `snake_case`） |
| HTML 标签 | 去标签保留文字 |
| 连续空白 | 折叠为单个空格 |

**第一步之后、第二步之前：标题去重（结论 20）**——若剥离结果的首行是 `<title>` 本身的纯文本（即原文以同级 H1 开头），移除该行。等价于现有 `previewContent()` 的语义，但作用在剥离后的文本上。

**第二步：截窗口**

| 情形 | 取值 |
| --- | --- |
| 有关键词且命中 | 以命中处为中心，前后各约 40 字，两端加省略号 |
| 有关键词但只命中标题 | 取正文**开头**约 120 字（标题本身由前端高亮） |
| 无关键词 | 取正文开头约 120 字 |
| 剥离后为空（如整篇只有代码块） | 回退为**原始文本**开头约 120 字，保证卡片不空白 |
| `content` 为空 | `excerpt` 为空串 |

边界若落在英文单词中间，最多向外扩 10 字符至最近的空白或标点（中文按字符切即可）。命中定位在**剥离后的纯文本**上做，大小写不敏感。

**命中位置怎么算**：`MATCH ... AGAINST` 只回答「哪一行命中」，不回答「在第几个字」——所以命中窗口仍由 `locate()` + `substring()` 在 SQL 里算（结论 32），`MATCH` 只负责筛选出行。两件事互相独立，实测可组合使用。命中总数 `hitTotal` 由同一趟 `locate()` 循环向后数出，不额外查库。

**列表查询取哪一段（结论 32 已定）**

前台列表**不再取全文**，改由 SQL 算出「命中窗口 + 无命中前缀」两段短文本，回传几百字符。这与上面的摘要在 Java 侧剥离并不矛盾：**SQL 负责定位与截取，Java 负责剥离 Markdown 标记**（块级剥离无法用 SQL 表达，摘要在 Java 侧仍是必需的）。

实现上要注意：`selectSgjNoteVo` 是列表 / 详情 / 后台共用的片段，而**后台列表与导出仍需要全文**，所以前台列表要单开一份列清单。

**草稿列表返回什么**（§2.7 也用）：草稿列表**只回 `excerpt`**（标题为空时显示正文首行）与 `itemName`，**不回 `content`**；「继续写」与冷启动的 `/note/edit?draftId=` 改由 `GET /app/note/draft/{draftId}` 取全文。

**已采纳的原升级路径（原 ponytail 取舍作废）**：原方案让草稿列表顺带返回完整 `content`，省掉一个按 id 取单条草稿的接口；代价是每份草稿 × 单篇 100,000 字（原来的 20 份上限已取消，见结论 14）≈ 最坏 6MB，而这个 6MB 会落在**笔记列表页每次加载**上——入口条要知道数量就得先拉列表，且该页免登录、是访客最常打开的页面。现改为：列表只回 `excerpt`（约 2KB 量级），另加按 id 取单条。

### 2.7 草稿写入与同步

打字时先写本地，再定期与服务端同步。本地缓冲是为了"断网、强杀、崩溃都不丢"，服务端才是跨设备的那一份。

| 时机 | 动作 |
| --- | --- |
| 表单变化后停手 1s | 写 `localStorage`（同步、瞬时成功） |
| 每 15s（有变化时） | `PUT /app/note/draft`（静默 + `repeatSubmit: false`） |
| `visibilitychange`（转 hidden）、`pagehide`、路由离开 | 先同步写本地缓冲，再立即 `PUT`（尽力而为） |
| `online` 事件 | 立即重推一次 |
| 每次推送成功 | 用响应里的 `updateTime` 覆盖本地 `baseUpdateTime`；**只有 `seq` 未变才清 `dirty`**——推送在飞时用户又打了字，响应回来不能当作"当前内容已同步" |
| 保存笔记成功 | 服务端同事务删除草稿（§2.5），前端同时清本地缓冲 |

**本地缓冲的结构与采用规则**

```
key : sgj:note:buf:{username}:{editorKey}   // editorKey = note:{noteId} | draft:{draftId} | new
值  : { draftId, noteId, itemId, title, content, tags, isPublic, baseUpdateTime, dirty, seq }
```

**时间戳一律由服务端给。** `baseUpdateTime` = 最近一次推送成功时服务端返回的 `updateTime`；`dirty` = 之后又改过；`seq` 是本地递增计数器（内存里，不是时钟），只用来判断推送响应回来时内容有没有再变过。**不存客户端 `savedAt`**——本地钟与服务端钟比不出"谁的内容新"，只能比出"谁的钟快"：设备钟快 6 分钟时，本地永远赢，会拿一份旧内容盖掉另一台设备刚写的东西（B 写了 5 分钟，A 一进编辑器就被自己的旧版本覆盖并推上去）；钟慢时本地永远输，离线写的内容联网后就没了。

进入编辑器时：

1. 按身份拉服务端草稿（编辑态按 `noteId`；从草稿箱"继续写"按 `draftId`；全新按"无"）；
2. 取本地缓冲，**仅当 `username`、`draftId`、`noteId` 三者都与当前身份一致**时才参与比对（否则那是别的用户或别的草稿的缓冲，不能套用，结论 26）；
3. 三条判断，**只用服务端时间**：
   - `dirty` 为真 → 这些改动服务端从没见过 → 用本地，并立即推一次；
   - `dirty` 为假且 `baseUpdateTime` 与服务端 `updateTime` 相等 → 两份等价，用谁都一样；
   - `dirty` 为假且两者不等 → 本地是旧快照、且没有未推送的改动 → 用服务端。

`baseUpdateTime` 不等时**仍然以本地为准推上去**（LWW，见结论 24），因为一份有 `dirty` 的本地内容如果被丢弃，就等于"离线写了半天，一联网进编辑器就没了"，与 #32 的验收冲突。

这样本地缓冲不会把"草稿 A 的最新内容"错用到"草稿 B"上，也不会把"用户 A 的未保存内容"交给用户 B；更要紧的是**任何未推送过的内容都不会丢**（这正是 `dirty` 分支的定义，不需要任何时钟比较）。首次同步拿到 `draftId` 后，把它的值写回缓冲即可，无需迁移 key。

### 2.8 发布语义

| 动作 | 结果 |
| --- | --- |
| 新建笔记点「保存」 | `POST /app/note` + `draftId` → 插入 `sgj_note`，同事务删除草稿行 |
| 编辑已有笔记点「保存」 | `PUT /app/note` + `draftId` → 更新 `sgj_note`，同事务删除草稿行 |
| 保存时带的 `draftId` 是别人的 | **整个请求失败**，不写任何数据（不能"忽略它、照常保存"） |
| 草稿箱里点「删除」 | 硬删除该行 |
| 草稿箱里点「继续写」 | 进编辑页并带 `draftId`，与普通新增共用同一个编辑器 |
| 笔记软删 / 彻底删除 / 它所属的条目被彻底删除 | 该笔记的草稿行一并消失（三处清理点，见结论 25）；编辑页若还开着，后续自动保存会被结论 40 的存活校验拒绝 |

### 2.9 前端设计

**前台列表卡片**（`views/front/note/index.vue`）

- 预览数据源由 `note.content` 改为 `note.excerpt`；
- 卡片不再渲染 Markdown，改为纯文本摘要；关键词高亮：把摘要按关键词切成片段数组，模板里用 `span` 循环渲染。**不拼 HTML 字符串、不走 `v-html`**，因此不产生 `escapeHtml` 需求，也不新增绕过 DOMPurify 的路径；
- **多命中轮播已评审不采用**（2026-09-18）：文字的阅读节奏与自动播放冲突，动的卡片还会干扰相邻卡片的阅读；多命中场景以摘要下方的「文中出现 N 次」小字提示（统一自然语言表述，不用「命中」这类实现术语；仅 >1 处时显示），查看全部进详情页。完整规格留档于 `docs/design/mockups/note-0.0.2/note-search-carousel.html`，落地样式见同目录 `note-search-excerpt.html`；
- 底部渐隐改为**仅在内容溢出时**显示（需求 FR-002-2 边界 6）；
- 搜索框 placeholder 由「搜索笔记标题」改为「搜索标题与正文」；传参由 `title` 改为 `keyword`；
- 删除 `previewContent()`——该职责已由后端摘要承担（结论 20）；
- 修复移动端断点选择器 `.toolbar` → `.filter-bar`（结论 28）；
- 插入草稿入口条，仅登录且草稿数 > 0 时渲染；**数量取草稿列表长度**（列表只回 `excerpt`，不再为这个数字拉下全部草稿正文，§2.6）。

> 上面这一条「改吃 `excerpt`」在本版是**半成品状态**：develop 上 `SgjNote` 还没有 `excerpt` 字段，`SgjNoteMapper.xml` 的 `selectAppNoteList` 把短文本塞进了 `content`，而本页仍在用 `MarkdownViewer` + `previewContent()` 渲染。这段迁移**算在 #39 范围内**（不另开 issue），与入口条一起改到干净，否则会出现「草稿箱用 `excerpt`、笔记列表用 `content`」的同一字段两种语义并存。

`components/ItemNotes/index.vue` 同源改造：直接使用 `excerpt`，删除本地 `noteSummary()`。至此前端三份重复的「Markdown 转纯文本」中，前台两份消失，仅后台摘要列保留一份（结论 2 的既定代价）。

**编辑页**（`views/front/note/edit.vue`）

- 草稿的写入、同步、恢复、放弃按 §2.7 与需求 FR-002-3；
- 状态条按结论 23 三态，恢复提示优先且可关闭；关闭 ≠ 删除草稿（删除在草稿箱或状态条上的「放弃」）；
- 「取消」按钮走下方「未保存离开确认」的同一道确认；
- 三种入口：`?noteId=`（编辑已有）、`?draftId=`（草稿箱「继续写」，冷启动时调 `GET /app/note/draft/{draftId}` 取全文）、`?itemId=`（条目页带过来预填）；
- **自动保存要静默**（结论 22）：请求带静默开关，401 与其它失败都不弹全局提示与「登录状态已过期」；失败时状态条转「仅本地待同步」；
- **正文超 `CONTENT_MAX_LENGTH` 时编辑器内提前拦住**并明确提示，不进入"每次推送都被拒、状态条一直停在仅本地待同步"的哑失败；
- **草稿 PUT 被存活校验拒绝**（笔记已被删除）时，明确提示「这篇笔记已被删除」并给出回列表页的出口，不要当作普通同步失败反复重试。

**未保存离开确认**

| 项 | 取值 |
| --- | --- |
| 判据 | 与**打开时的服务器快照**不同（新增态：`title`/`content` 非空，或 `itemId`/`tags`/`isPublic` 偏离初始值）。**不能**用「与草稿是否相同」——草稿总是最新的，那样写等于永不拦截 |
| 拦截出口 | 路由离开（`onBeforeRouteLeave`）、顶部「取消」、左上角返回、浏览器后退、关闭 / 刷新标签页（`beforeunload`） |
| 保存成功后 | 清掉本地缓冲（含 `dirty`）后跳转，**不得**弹确认 |
| 用户确认离开后 | 草稿**保留**（新增态可从草稿箱继续，编辑态下次静默恢复） |
| 应用内文案 | 须说明内容已保存到草稿箱，而不是「未保存的修改将丢失」；**但这句话要由「草稿是否真的存在」决定**——空表单（`title` 与 `content` 都为空）不建草稿（结论 14），此时若只改了条目 / 标签 / 公开状态就离开，文案不能说「已保存到草稿箱」，否则与实际不符；浏览器原生确认框文案不可自定义（结论 30） |

**代码语法高亮**（`components/MarkdownViewer/index.vue`）

- 依赖：`highlight.js` + `marked-highlight`（`package.json` 新增两项，需在 PR 中说明理由）；
- 接入：`marked.use(markedHighlight({ hljs, langPrefix: 'hljs language-' }))`；
- 语言白名单：`highlight.js/lib/common`（约 35 种常用语言），不引全量；
- **只高亮显式标注且 `hljs.getLanguage()` 认识的语言**；无标注 / 不认识的原样输出。不启用 `highlightAuto`（长块明显变慢且易猜错）；
- 主题：引 `highlight.js/styles/atom-one-dark.css`，其底色与现有 `pre` 的固定 `#282c34` 同源，两种主题下代码块都保持深色（属需求 FR-002-5 边界 5 的实现取舍）；
- **高亮规则的作用域**：`MarkdownViewer` 的样式全部写在 `<style scoped>` + `:deep()`，新增的 token 规则必须沿用 `:deep()` 或全局引入；**新开一段不带 `:deep()` 的 scoped 规则不会生效**（本版最容易踩的坑）；
- 既有 `:deep(pre) code { background: transparent; color: inherit }` 带 data-v 属性，特异性高于主题的单类选择器，会覆盖主题对 `code` 的底色 → 行为与现状一致，无需改 `pre` 配色（不扩大 diff）；
- 体积：前台列表改为纯文本摘要后，`views/front/note/index.vue` 不再 import `MarkdownViewer`，hljs 只落在「笔记详情」「笔记编辑」「后台笔记」三个懒加载路由，**首页与列表首屏不付这份体积**，因此静态引入即可，无需动态 import；
- 编辑页实时预览与详情页共用 `MarkdownViewer`，高亮自动同时生效。

### 2.10 影响文件清单

**数据库**

| 文件 | 变更 |
| --- | --- |
| 新增 `sql/update/20260917_note_draft.sql` | 建表 `sgj_note_draft`（含 `unique key uk_sgj_note_draft_owner_note (create_by, note_id)`），幂等脚本，参照 `update/20260909_item_photos.sql` 体例 |
| 新增 `sql/update/20260918_note_fulltext.sql` | 给 `sgj_note` 加 `FULLTEXT KEY ft_note_title_content (title, content) WITH PARSER ngram`（结论 33）。`ADD FULLTEXT` 会为存量行建索引，**不需要回填脚本**；注意它锁表重建，量大时挑低峰 |
| 新增 `sql/update/20260918_note_source_file.sql` | 给 `sgj_note` 加 `source_file varchar(500) default ''`（导入原件的路径，结论 37），幂等脚本 |
| `sql/init_business.sql` | 同步加入建表与建全文索引语句供新库使用 |

**后端**

| 文件 | 变更 |
| --- | --- |
| `business/.../domain/SgjNote.java` | 新增 `keyword`、`excerpt`、`draftId` 三个非表字段 |
| 新增 `business/.../domain/SgjNoteDraft.java` | 草稿实体（另需 `itemName`、`excerpt` 两个非表字段） |
| `business/.../resources/mapper/business/FileReferenceMapper.xml` | **新增一行 union**：`select source_file from sgj_note where source_file like '/profile/%'`（结论 37）。漏掉它，导入的原件会在下一轮清理中被当孤儿误删——这是本版最容易被遗忘的一处 |
| 新增 `business/.../mapper/SgjNoteDraftMapper.java` + `resources/mapper/business/SgjNoteDraftMapper.xml` | 草稿 CRUD，按 `create_by` 过滤；列表只取 `excerpt` 所需列（不取 `content`）、左联 `sgj_item` 带 `item_name`；含 `deleteByNoteIds` 与「按 item_id 反查 note_id 删草稿」 |
| `business/.../resources/mapper/business/SgjNoteMapper.xml` | `selectSgjNoteList` 的检索条件改为 `match(title, content) against(#{keyword} in boolean mode)`；列表列清单改为只取「命中窗口 / 前缀」短文本（结论 32）；新增按 `noteId` 判断存活 / 按 `itemId` 取 `note_id` 的查询 |
| `business/.../service/impl/SgjNoteServiceImpl.java` | 新增静态摘要方法（含标题去重与 front-matter 剥离）；`CONTENT_MAX_LENGTH` 20000 → 100000（结论 32）；检索前剔除布尔运算符字符（结论 33）；**`deleteSgjNoteByIds` / `purgeSgjNoteByIds` 加 `@Transactional` 并调 `deleteByNoteIds`**（结论 25）；`add` / `edit` 的 `draftId` 归属校验与同事务删除（结论 15） |
| `business/.../service/impl/SgjItemServiceImpl.java` | **`purgeSgjItemByIds` 加 `@Transactional`，并在删除条目前先删该条目下所有笔记的草稿**（结论 25，顺序不能反）。原文件清单漏了此文件 |
| 新增 `business/.../service/ISgjNoteDraftService.java` + `impl/SgjNoteDraftServiceImpl.java` | 草稿服务：归属校验、存活校验（结论 40）、按 owner + noteId 的 upsert 与唯一键冲突转更新、`deleteByNoteIds` 共用清理；**不加 `@Transactional`** |
| `admin/.../controller/business/AppNoteController.java` | `list` 生成 `excerpt` 并置空 `content`；`add` / `edit` 接受 `draftId`（归属与删除下沉到 service）；删除与彻底删除的草稿清理**下沉到 service**，控制器不写 |
| `admin/.../controller/business/SgjNoteController.java` | 无需改动——软删清理在 service 里，该控制器的 `remove` 自动覆盖（结论 25） |
| 新增 `admin/.../controller/business/AppNoteDraftController.java` | 草稿箱五个接口（含按 id 取单条），全部要求登录；`createBy` 一律用 `SecurityUtils.getUsername()` **覆盖**（结论 40） |
| `admin/.../test/.../AppApiAuthIsolationSmokeTest.java` | 新增匿名 keyword 用例、草稿隔离用例、三条清理路径用例、存活校验用例 |
| 新增 `business/src/test/.../SgjNoteExcerptTest.java` | 摘要规则单测（含标题去重） |

**前端**

| 文件 | 变更 |
| --- | --- |
| `views/front/note/index.vue` | 改吃 `excerpt`、切片段高亮、删 `previewContent()`、改 placeholder 与传参、加草稿入口条、修移动端断点、渐隐仅在溢出时显示（属 #39 范围） |
| 新增 `views/front/note/draft.vue` + `router/index.ts` 路由 | 草稿箱页（已登录才能进，走现有守卫） |
| 新增 `api/front/noteDraft.ts` + `types/api/front/noteDraft.ts` | 草稿接口与类型 |
| `components/ItemNotes/index.vue` | 改吃 `excerpt`，删 `noteSummary()` |
| `views/front/note/edit.vue` | 草稿（本地缓冲 `baseUpdateTime`/`dirty`/`seq` + 同步 + 静默恢复 + 三态状态条）、`?draftId=` 冷启动取全文、笔记被删提示、正文超限拦截、离开确认 |
| `utils/request.ts` | **新增 per-request 静默开关**（结论 22）：401 分支与两个 error 分支都要认它，只压提示、promise 照旧 reject。动的是全站共用文件，改动需回归其它请求的错误提示 |
| `utils/sgj.ts` | 新增日期解析函数（结论 27，**仅用于展示**）与按关键词切片段函数（供高亮，纯逻辑可单测） |
| `views/business/note/index.vue` | 搜索参数改 `keyword`，label 改「关键词」 |
| `types/api/front/note.ts` · `types/api/business/note.ts` | `keyword` 入参、`excerpt` / `hitTotal` / `draftId` 字段 |
| `components/MarkdownViewer/index.vue` | 接入 hljs + 主题 |
| 新增 `components/MarkdownViewer/__tests__/index.spec.ts` | 高亮与消毒断言 |
| 列表页 / 条目页导入入口、后台笔记管理页的两个下载动作（文件待定） | #14 的前端部分：导入**不新增预览界面**（跳编辑页）；后台每行加「下载原件」「导出 Markdown」（结论 39）。详细交互**待专项设计**（需求 FR-002-8） |

### 2.11 测试计划

**后端**

| 测试 | 内容 |
| --- | --- |
| `SgjNoteExcerptTest`（新增，纯单测，无 Spring / 无 DB） | 剥离各类语法；**正文以同级 H1 开头时摘要不重复标题**；**YAML front-matter 整块移除**；窗口以命中处为中心；无命中取开头；命中标题时取开头；纯代码块笔记的回退；空正文；命中在首 / 尾 |
| `AppApiAuthIsolationSmokeTest`（补用例） | ①私密笔记正文含独有标记 → 匿名带 `keyword` 请求 → **0 命中**；回收站笔记同样不可命中。②检索语义：`mysql` 不命中只含 `sql` 的笔记（布尔模式）；**单字查询返回 0 行**（ngram 约束）；含 `-` / `+` 的输入不报错且不产生空结果（运算符已被剔除）。③**导入的原件路径出现在 `FileReferenceMapper.selectReferencedStorageKeys()` 的结果里**（结论 37）——漏了它原件会被每周的清理任务误删，而这类缺陷不会报错、只会静默丢文件 |
| `AppNoteDraftSmokeTest`（新增，#37/#38 的用例单独成类，不挤进上面那个带 `@Order` 的类） | ①**匿名访问四个草稿端点返回 401 业务码**（`ServletUtils.renderString` 写死 HTTP 200，断言要断 body 里的 `code`，不是 HTTP 状态；否则「不是 200 空列表」这句会被误读成 HTTP 断言）。②新建返回 `draftId` + **与库里那行一致的 `updateTime`**（不能用 JVM 时钟，结论 10）；草稿箱列表只回 `excerpt` 不回 `content`；按 id 取单条含正文；带 `draftId` 更新不新建行；删除即时消失。③无标题草稿用正文首行兜底。④草稿隔离：A 用户草稿对 B 不可见；按他人 `draftId` 更新 / 删除被拒；**管理员也读不到他人草稿**（不沿用 `AppScopeHelper`）。⑤首页 `noteTotal` 与最近笔记不受草稿影响。⑥**并发 upsert**：同一 `(create_by, note_id)` 连续两次不带 `draftId` 的 PUT，不产生第二行；**存活校验（结论 40）**：往软删掉的、以及已物理删除的笔记写草稿都被拒。⑦发布语义：带自己的 `draftId` 保存后草稿行消失、不带 `draftId` 行为不变、**带他人 `draftId` 整个请求失败且没有写入任何数据**。⑧**三条清理路径各一条用例**：笔记软删 / 笔记彻底删除 / **条目彻底删除**（结论 25 第 ③ 处，原清单整个没有这条）后，该笔记的草稿行都消失 |

**前端**

| 测试 | 内容 |
| --- | --- |
| `MarkdownViewer` spec（新增，vitest + jsdom） | ` ```java ` 渲染后含 `span.hljs-*`（证明 class 未被 DOMPurify 误伤）；无语言标注不报错且不高亮；`<script>` / `onerror` 仍被消毒 |
| `utils/sgj` spec（补用例） | 日期解析：`yyyy-MM-dd HH:mm:ss` → 正确 epoch（不得返回 NaN）；切片段：大小写不敏感、关键词含正则元字符（`(` `[` `*`）不抛错、命中多次全部切出 |
| 导入解析 spec（新增，纯逻辑） | front-matter 解析：有 / 无 front-matter 都要正确；`tags` 里混有表内与表外标签时**只保留表内的**，并给出被忽略的数量（结论 38）；标题取值顺序 front-matter → 首个 H1 → 文件名去扩展名 |
| 草稿恢复判断 spec（新增，纯逻辑，`utils/__tests__/noteDraftBuffer.spec.ts`） | 结论 10 的三条分支各一例：`dirty` 为真 → 取本地；`dirty` 为假且 `baseUpdateTime` 与 `updateTime` 相等 → 等价；不等 → 取服务端。另加：身份（`username`/`draftId`/`noteId`）任一不匹配的本地缓冲一律不采用（结论 26）；按用户隔离的读写清与登出清理 |
| 推送竞态 spec（新增，纯逻辑，同 `noteDraftBuffer.spec.ts`） | 推送在飞时内容又变（`seq` 变化）→ 响应回来**不清 `dirty`**；`seq` 未变 → 清 `dirty` 并把响应里的 `updateTime` 写进 `baseUpdateTime`（结论 26） |
| `utils/request` 静默开关 spec（补用例） | 带静默开关的请求在 401 / 500 时**不触发** `ElMessageBox` / `ElMessage` / `ElNotification`，但 promise 仍 reject（调用方靠它把状态条转「仅本地待同步」）；**不带开关的请求行为不变**（这条是回归，动的是全站共用文件） |

**回归**

- 前台：标签筛选、条目筛选（`itemId`）、分页与「加载更多」、匿名仅见 `is_public='1'`、回收站恢复与彻底删除；
- 后台：笔记管理页列表与摘要列、CSV / JSON 导出（`content` 列必须仍有值）、条目详情的关联笔记列表；
- 详情页：正文全文、大纲锚点、编辑入口；
- 首页：`noteTotal` 与「最近笔记」不受草稿影响（独立表的直接收益，需实测确认）。

### 2.12 实施顺序（小步提交）

1. `docs:` 本文 + 需求文档；
2. `feat:` 建表脚本（`sql/update/20260917_note_draft.sql` + `init_business.sql`，含 `unique key uk_sgj_note_draft_owner_note`）与草稿实体 / Mapper / Service；
3. `feat:` 全文索引脚本（`FULLTEXT ... WITH PARSER ngram`，结论 33）+ 正文上限提到 10 万字 + 后端 `keyword` 检索（布尔模式、剔除运算符）+ 列表与首页改取短文本（结论 32）→ 补匿名 keyword 回归用例，`mvn -pl shiguangji-admin -am test`；
4. `feat:` 摘要生成静态方法（含标题去重）+ `SgjNoteExcerptTest`，手工查库校验若干真实笔记的摘要输出；
5. `feat:` 草稿接口（**五个端点**（含按 id 取单条）+ 归属校验、`createBy` 覆盖、**存活校验**、按 owner + noteId 的 upsert 与**唯一键冲突转更新**）与隔离回归用例；
6. `feat:` 发布时同事务删草稿（归属校验在 service）；**三处清理点**用同一个 `deleteByNoteIds` —— 笔记软删 / 笔记彻底删除 / **条目彻底删除（`purgeSgjItemByIds` 加 `@Transactional`，先删草稿再删条目）**；
7. `feat:` 前端工具（日期解析、切片段）+ `utils/sgj` spec；
8. `feat:` 前台列表改吃 `excerpt` + 切片段高亮 + 草稿入口条 + 移动端断点修复（**属 #39 范围**）；`ItemNotes` 同步；
9. `feat:` 后台搜索参数与 label；
10. `feat:` 编辑页草稿：本地缓冲（按用户隔离 + `baseUpdateTime`/`dirty`/`seq`）+ 定时静默同步 + 三态状态条 + 静默恢复 + `?draftId=` 冷启动取全文 + 正文超限拦截；**同时给 `utils/request.ts` 加 per-request 静默开关**（要回归其它请求的错误提示）；
11. `feat:` 未保存离开确认（路由 + `beforeunload` + 取消按钮；文案由"草稿是否真的存在"决定）；
12. `feat:` 草稿箱页面与路由（未登录走现有守卫跳登录页）；
13. `feat:` 语法高亮 + `MarkdownViewer` spec；
14. `feat:` #14 导入 / 导出（**仅 md**）——拆成两个子 issue 实施：
    - [#40 导入链路](https://github.com/MosaicAccount/shiguangji/issues/40)：前端解析 → 跳编辑页预填 → 保存时上传原件；含 `source_file` 列与 `FileReferenceMapper` 的 union；
    - [#41 导出与两个下载入口](https://github.com/MosaicAccount/shiguangji/issues/41)：后台行内的「导出 Markdown」与「下载原件」；

    两者都依赖 [#42 正文上限提升与列表瘦身](https://github.com/MosaicAccount/shiguangji/issues/42)（与第 3 步同批，因为它改的是同一个 mapper）；
15. 提交前运行：前端 `npx vue-tsc --noEmit`、`npm run build:prod`、`npm run test:unit`；后端 `mvn -pl shiguangji-admin -am test`。

### 2.13 非目标

- 不做笔记版本历史、分类 / 文件夹、导入 / 导出之外的文件能力；
- **本版导入只支持 md**：Word（docx）解析与转换（含内嵌图片转存）转后续版本。docx 解析要新增依赖、还要做结构与图片转存，而它的验收与 md 完全独立，适合单独排期；
- 不做 md 里的 base64 内嵌图片转存、不做相对路径图片的自动匹配；
- 前台不做下载 / 导出入口——出口都在后台笔记管理页（结论 39）；
- 不做批量导入（目录 / vault）与批量导出打包；
- 不做全文索引的深度调优（分词器定制、相关度调参），**不引入独立检索服务**（Elasticsearch / Meilisearch 之类），全文检索用 MySQL 原生 FULLTEXT + ngram（结论 33）。到需要相关度排序、跨类型聚合时再评估；
- 不做前台全局搜索（转 0.0.5，见结论 18）；
- 不做「想法」等新记录类型；
- **不做草稿的自动过期清理任务**（不引入 quartz 定时）；
- **不做后台草稿页面**（草稿只在正文侧）；
- 不在本版把后台摘要列与导出改为后端摘要（前台优先，后台保持现状）；
- **不做跨设备并发冲突检测**（接受后写覆盖，见结论 24）。

---

## 3. 设计图

已产出，落在 `docs/design/mockups/note-0.0.2/`。**直接打开 HTML 即可查看**——自包含、无构建、无网络依赖。

### 3.1 五屏与状态

| 文件 | 屏 | 路由 | 覆盖的状态 |
| --- | --- | --- | --- |
| `index.html` | 索引 | — | 五屏入口、出图口径、静态边界说明 |
| `note-list.html` | 笔记列表 | `/note` | 默认（含草稿入口条）/ 关键词命中 / 无结果 / 加载失败 / 访客未登录 |
| `note-draft.html` | 草稿箱 | `/note/draft` | 有草稿 / 空态 / 删除确认 |
| `note-edit.html` | 编辑 | `/note/edit` | 已同步 / 恢复草稿 / 仅本地 / 同步失败 / 离开确认 / 导入预填 / 保存中 |
| `note-detail.html` | 详情 | `/note/detail` | 代码高亮 / 无标注与未知语言 / 移动端大纲浮层 |
| `admin-note.html` | 后台笔记管理 | `/business/note` | 关键词命中正文 / 无结果 |

条目详情的关联笔记列表沿用列表页的摘要样式，不单独出图；后台那屏纳入是因为它有新增的两个下载动作。

### 3.2 出图约定

- **每屏一页**，页内用顶部工具条切换 **状态 / 深色模式 / 桌面与移动端 / 标注**——四屏 × 两主题 × 两视口 × 多状态，不拆成十几个文件，评审时直接切。
- 橙色数字气泡与页脚「本屏标注」一一对应；关掉「标注」可看无打扰的观感。
- 设备框是**可滚动的真视口**：顶栏与移动端底部标签栏像真机一样吸住，据此判断遮挡与留白（底部标签栏遮挡是本版验收项之一）。
- 视觉基线：`assets/styles/front-tokens.scss` 的 `--sgj-*` 令牌（冷雾胶片 v3.3）与既有卡片 / 圆角 / 阴影；**后台页沿用既有 admin / Element Plus 主题**，只标注改动、不重设计。
- 深浅两套都出，是因为代码块在两种主题下都保持深色（需求 FR-002-5 边界 5），需要确认它在浅色页面里的对比是否协调。
- 移动端单独出，是因为本版有三处只在移动端成立的差异：底部标签栏遮挡、关闭页面不承诺 `beforeunload`、编辑页预览走弹窗。
- **静态保真**：不连真实接口。表单可聚焦、开关可切换、弹窗可关闭，但保存 / 上传 / 检索均为示意——这些是刻意的边界，不是缺失。
