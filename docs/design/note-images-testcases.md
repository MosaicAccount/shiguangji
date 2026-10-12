# 图片搬运 —— 测试用例（0.0.2 FR-002-9，Issue #56）

> 配套：[需求 §3.3 FR-002-9](../requirements/0.0.2-note.md)、[详细设计](note-images-design.md)、[架构](note-images-architecture.md)。
> 「对应」列指向详细设计的结论编号（设计 结论 N）或需求 §3.3 的边界序号。
> 用例编号 T-xx 是稳定标识：实施与 PR 描述里引用它，回归时按它点名。

## 1. 怎么跑

| 层 | 命令 | 依赖 |
| --- | --- | --- |
| 前端纯逻辑 + 渲染 | `cd shiguangji-web && npx vitest run` | 无 |
| 前端类型与构建 | `npx vue-tsc --noEmit && npm run build:prod` | 无 |
| 后端单测（纯逻辑 / mock） | `mvn -pl shiguangji-file,shiguangji-quartz test` | 无 |
| 后端 smoke（MockMvc + dev） | `mvn -pl shiguangji-admin -am test` | MySQL `localhost:13306`、Redis `localhost:16379` |
| 人工验收 | 见 §4 | 跑起来的前后端 + 一个真实的 Obsidian vault |

`mvn` 必须跑在 JDK 17 上（macOS：`JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...`），否则报「无效的目标发行版: 17」。

## 2. 前端

### 2.1 引用收集（`utils/noteImages.ts`，`__tests__/noteImages.spec.ts`）

| 编号 | 用例 | 输入 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-01 | 四类 markdown 形状都取得出目标 | `![](./a.png)`、`![](attachments/a.png)`、`![alt](a.png "标题")`、`![](<a b.png>)` | `target` 依次为 `a.png`、`attachments/a.png`、`a.png`、`a b.png`（`"标题"` 与 `<>` 剥离） | 设计 50 |
| T-02 | wikilink 三种附加语法 | `![[a.png]]`、`![[attachments/a.png\|200]]`、`![[a.png#^block]]` | `target` 依次为 `a.png`、`attachments/a.png`、`a.png` | 设计 50 |
| T-03 | 代码块与行内代码不收集 | 围栏（``` 与 ~~~）内、行内 `` ` `` 内的 `![](./a.png)` | 收集结果为空 | 设计 50 |
| T-04 | 四类排除项 | `![](https://x/a.png)`、`![](data:image/png;base64,...)`、`![](//cdn/a.png)`、`![](/profile/upload/2026/10/10/blog.png)` | 收集结果为空（**最后一条是往返无损的前提**） | 设计 50、结论 47 |
| T-05 | URL 编码解码 | `![](./%E5%9B%BE%20a.png)` | `target` 为 `图 a.png` | 设计 50 |
| T-06 | 重复引用计数与去重 | 同一文件被两处引用 | `localImageRefs.length === 2`；去重后的候选文件数为 1 | 设计 60 |
| T-07 | 围栏未闭合不吞正文 | 一个只有开头 ```` ``` ```` 的文件 | 后续行里的引用仍按代码处理（不收集），且解析不抛异常 | 设计 50 |

### 2.2 匹配（同文件）

| 编号 | 用例 | 输入 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-08 | 同目录精确相对路径 | md 名 `note.md` 在候选树中位于 `vault/note.md`，候选含 `vault/attachments/a.png`，引用 `attachments/a.png` | 命中该候选（优先级 1） | 设计 50 |
| T-09 | md 不在候选树里 → 退到相对根 | 候选根下有 `attachments/a.png`，同上引用 | 命中 | 设计 50 |
| T-10 | basename 唯一匹配（大小写不敏感） | 候选只有 `vault/attachments/A.PNG`，引用 `![[a.png]]` | 命中 | 设计 50 |
| T-11 | basename 歧义不猜 | 候选含 `x/a.png` 与 `y/a.png`，引用 `![[a.png]]` | 未命中（保留原引用） | 设计 50、51 |
| T-12 | 完全不匹配 | 候选为空 | 未命中，不抛异常 | 设计 51 |

### 2.3 改写（同文件）

| 编号 | 用例 | 步骤 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-13 | 只换命中的 | 3 处引用，命中 2 处 | 2 处变成 `/profile/...`，未命中的一字不动，顺序与其余正文不变 | 设计 51 |
| T-14 | 重复引用只上传一次 | 同一文件两处引用，桩 `upload` 计数 | `upload` 被调用 1 次，两处都指向同一个新路径 | 设计 60 |
| T-15 | 改写后超长 | 改写后正文 > 100000 字 | 返回「超长」信号（含实际字数），调用方据此提示且**照常预填** | 结论 59 |
| T-16 | 上限过滤 | 候选 60 个 / 其中一个 11MB | 被忽略数与原因（数量 / 体积）正确计数 | 设计 §3.2 上限 |

### 2.4 渲染（`components/MarkdownViewer/__tests__/index.spec.ts` 增补）

| 编号 | 用例 | 输入 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-17 | 站内相对路径补前缀 | `![x](/profile/upload/a.png)` | 渲染出的 `img[src]` 以 `VITE_APP_BASE_API` 开头 | 结论 54、I-3 |
| T-18 | 外链与内嵌不动 | `![](https://x/a.png)`、`![](data:image/png;base64,...)` | `src` 原样 | 结论 54 |
| T-19 | 钩子只注册一次 | 连续两次导入该模块（模拟 HMR）后渲染同一内容 | `src` 只出现一次前缀（不是 `/dev-api/dev-api/...`） | 结论 54 |
| T-20 | 消毒未误伤 | 含 `<script>` 的正文 | 脚本仍被过滤掉，图片行为不受影响 | 结论 54（回归） |

## 3. 后端

### 3.1 引用抽取（`ProfileRefExtractorTest`，无 Spring / 无 DB）

| 编号 | 用例 | 输入 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-21 | 括号终止 | 正文 `![](/profile/upload/2026/10/10/abc.png)`，候选含该键 | 命中 | 结论 56 |
| T-22 | 引用后紧跟中文 / 全角括号 | `见 /profile/upload/2026/10/10/abc.png（见图）` | 命中（宽 token + 前缀匹配，这是正则猜边界会漏的形状） | 结论 56 |
| T-23 | URL 编码引用 | `![](/profile/upload/2026/10/10/%E5%9B%BE.png)`，候选为解码后的键 | 命中（解码后再试一次） | 结论 56 |
| T-24 | 伪引用无副作用 | 正文含 `/profile/nothing/xx.png`，候选集里没有它 | 不进结果集 | 结论 56 |
| T-25 | 不越界 | 含 `../` 的路径、超 200 字符的 token | 不进结果集，不抛异常 | 结论 56 |
| T-26 | 空输入 | `null` / 空串 / 无 `/profile/` 的正文 | 返回空集，不抛异常 | 结论 56 |
| T-27 | 同一键多处出现 | 正文里同一路径出现 3 次 | 结果集里 1 个键 | 结论 56 |

### 3.2 清理任务（`FileCleanupTaskTest` 增补，mock 协作方）

| 编号 | 用例 | 步骤 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-28 | 笔记正文引用保住文件 | 候选文件在笔记 `content` 里被引用（三列都不引用它），已过保护期 | 不删文件、不删台账 | 结论 55 |
| T-29 | 草稿正文引用同样保住 | 同上，引用只出现在 `sgj_note_draft.content` | 不删 | 结论 55 |
| T-30 | 抽取查询失败即中止 | mock 让 `selectContentsWithProfileRefs()` 抛异常 | 抛出中止异常，**一个文件都没删**、台账未动 | 结论 55 |
| T-31 | 无人引用的照旧回收（回归） | 既不在三列、也不在正文里 | 正常回收 | 结论 57 |
| T-32 | 比例兜底仍生效（回归） | 待回收 > 50% 且候选 ≥ 20 | 中止本轮并记 error 日志 | 结论 57 |
| T-33 | 没有正文引用时不额外误伤 | 内容抽取返回空集，三列照旧 | 行为与改动前逐字一致 | 结论 57 |

### 3.3 上传端点（`NoteImageUploadSmokeTest`，`dev` profile + MockMvc）

| 编号 | 用例 | 步骤 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-34 | 匿名必须被拒 | 不带 token POST | 业务码 401（`ServletUtils.renderString` 写死 HTTP 200，断言 body 里的 `code`） | 结论 52 |
| T-35 | 登录上传 png 成功 | 带 token 传 1×1 png | `code === 200` 且 `path` 匹配 `^/profile/upload/\d{4}/\d{2}/\d{2}/[0-9a-f]{32}\.png$`；文件能在存储里读回；`sys_file` 恰好多一行且 `storage_key` 等于 `path` | 结论 52、I-1 |
| T-36 | 非图片被拒 | 传 `.txt` | 业务码非 200，磁盘不落文件、台账不增行 | 结论 52 |
| T-37 | svg 被拒（与既有白名单一致） | 传 `.svg` | 业务码非 200 | 结论 52 |
| T-38 | webp 放行 | 传 `.webp` | `code === 200` 且 `path` 以 `.webp` 结尾 | 结论 52 |
| T-39 | 文件名为中文 / 含空格也得到安全路径 | 传 `图 a.png` | `path` 里不含中文与空格（uuid 命名） | 结论 52、I-1 |

## 4. 人工验收（只有人能跑，PR 描述里如实说明跑没跑）

| 编号 | 场景 | 步骤 | 期望 |
| --- | --- | --- | --- |
| T-40 | 真实 vault 搬运 | 取一个含 `note.md` + `attachments/` 的 Obsidian 目录（混用 `![[a.png]]` 与 `![](...)`，共 9 张图）：先选 md，再选整个 vault 文件夹 | 报告「已搬运 9 张」；编辑页正文里全部是 `/profile/...`；右侧预览与保存后的详情页**都能看到图** |
| T-41 | 同名歧义 | vault 里放 `x/a.png` 与 `y/a.png`，正文引用 `![[a.png]]` | 报告「1 处没找到对应文件，保留了原引用」，原引用一字不动 |
| T-42 | 跳过搬运 | 选 md 后在弹窗点「跳过，只导入文字」 | 全程没有上传请求（Network 面板）；正文引用保留；列表页提示「正文里有 N 处图片引用未搬运」 |
| T-43 | 放弃导入 | 搬运完成后在编辑页直接返回、不进保存 | 草稿箱里有这份内容（回归结论 41）；一周后那些图被回收（抽查存储目录） |
| T-44 | 往返无损 | 导出 T-40 那篇笔记 → 立刻重新导入同一个文件 | **不弹**搬运框、**不出现**「本地图片引用」提示；选「更新那篇」后正文与导出前一致 |
| T-45 | 编辑器插图 | 编辑页点「插图」选一张图 | 光标处插入 `![](/profile/...)`，预览立即显示；保存后详情页显示 |
| T-46 | 粘贴截图 | 在编辑区 Ctrl+V 粘贴一张截图 | 自动上传并插入；同一位置粘贴纯文本不受影响 |
| T-47 | 上传失败可读 | 断网或传一个后端不认的格式 | 报出可读错误，正文与预览都不变 |
| T-48 | 移动端 | 手机视口走一遍 T-40 / T-45 | 弹窗与工具栏可用，不被底部标签栏遮挡 |

## 5. 回归清单（改动碰到的既有能力）

| 编号 | 回归项 | 跑法 |
| --- | --- | --- |
| T-49 | 无图笔记的导入全流程（前台跳编辑页 + 后台弹窗）不变 | 人工 + `npx vitest run`（`noteImport.spec.ts` 既有断言必须原样通过） |
| T-50 | 封面 / 相册 / 头像上传与显示无回归（`/common/upload` 未改） | `mvn -pl shiguangji-admin -am test`（`ItemCoverApiSmokeTest`、`SgjItemPhotoApiSmokeTest`）+ 人工上传一次封面 |
| T-51 | 孤儿清理既有用例全绿（保护期 / 比例兜底 / 列举失败中止） | `mvn -pl shiguangji-quartz test`（`FileCleanupTaskTest`）、`mvn -pl shiguangji-file test`（`OrphanFileReconcilerTest`、`LocalFileStorageTest`） |
| T-52 | 笔记检索 / 摘要 / 草稿箱 / 回收站 / 导出全绿 | `mvn -pl shiguangji-admin -am test`（`AppApiAuthIsolationSmokeTest`、`AppNoteDraftSmokeTest`、`AppNoteExportSmokeTest`） |
| T-53 | 前台详情页代码高亮与大纲导航不受渲染钩子影响 | 人工打开一篇带代码块的笔记 |

## 6. 外链图片（结论 62）

| 编号 | 用例 | 输入 / 步骤 | 期望 | 对应 |
| --- | --- | --- | --- | --- |
| T-54 | 外链计数（纯逻辑） | 正文含 `![](https://a/x.png)`、`![](//cdn/b.png)`、`![](data:image/png;base64,…)`、`![](.//c.png)` | `local` 只含真正本地的引用；`externalCount === 2`（`http(s):` 与 `//` 计，`data:` 不计）；外链不改写 | 结论 62、需求边界 18 |
| T-55 | 弹窗明说（人工） | 导入一篇 7 张本地 + 3 张外链的笔记 | 弹窗默认态出现「另有 3 处是外链图片，不参与搬运…」；搬运后正文里 3 处外链一字未改；对方允许直链时详情页正常显示 | 结论 62、需求边界 18 |
| T-56 | 全外链（人工） | 正文只有 3 张外链图 | 不弹搬运框（没有本地引用）、不产生任何上传请求，外链引用原样保留。此场景**不出现**外链说明——提示语的作用是解释搬运计数，没有计数就没有误读（结论 62 的文案只在弹窗里） | 结论 62 |
