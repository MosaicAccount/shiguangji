# 手绘（0.0.6）—— 架构

> 需求：[0.0.6-handdrawn.md](../requirements/0.0.6-handdrawn.md) · 设计图（唯一视觉事实源）：`docs/design/mockups/handdrawn-0.0.6/`
> 纯前端视觉改版：**无接口、无表结构、无业务逻辑变更**；DOM 语义与可访问性（标题层级 / focus 顺序 / 对比度）维持既有标准。

## 1. 令牌分层

| 层 | 令牌 | 归属 | 说明 |
| --- | --- | --- | --- |
| 色彩层 | `--sgj-*` | `front-tokens.scss` | 整体换为「暖忆」值：主色 `#96503F`、moss `#5A6350`、amber `#8A5C31`（文本用）、bg `#F5F2EC`、card `#FFFDF9`、text `#37322C`；暗色为中性暖炭（bg `#1B1B1C`、card `#242426`、text `#ECEBE8`），不带棕黄罩层 |
| 形制层 | `--hd-*` | `handdrawn-site.css` | 纸 / 墨 / 线 / 胶带 / 印章 / 抖线 / 封面等形制令牌，全部定义在 `.hd-app` 作用域，暗色由 `html.dark .hd-app` 重定义 |

映射关系（前台与后台共用一套，后台换肤只做变量映射，不再定第二套色板）见设计说明 `index.html` §全局令牌映射。

## 2. 样式装载与作用域

- `handdrawn-site.css` 落到 `shiguangji-web/src/assets/styles/handdrawn/handdrawn-site.css`，由**前台布局 `layout/front/index.vue` 统一 import**（替代 `views/front/plan/handdrawn.css` 的 `.plan-page` 页面级作用域）。
- 布局根节点挂 `.hd-app`；`--hd-*` 只在 `.hd-app` 内生效，不泄漏到后台。
- 页面**只拼装共享类，不各画一遍**：纸片（`.hd-paper`）、抖线框（`.hd-rough` / `.hd-frame`）、胶带、按钮、胶囊、状态、标签、印章、表单、浮层、`.hd-summary` 小结条、`.hd-fallback` 默认封面均来自共享 CSS。
- 心愿页旧 `handdrawn.css` 随令牌升格退役，视觉不回归（类名映射见设计文档 §心愿）。

## 3. 字体（FR-007-4 / D-44）

- 自托管霞鹜文楷分片 CSS（`src/assets/fonts/handdrawn/lxgw-wenkai/lxgw-wenkai.css`）从心愿页路由加载改为**前台布局全局加载**。
- 字体栈保持自包含（`'LXGW WenKai','Kaiti SC',…`），不得引用站点变量作回退。

**核心片复核（#108，实测）**：`scripts/check-handdrawn-font-coverage.py` 把 shiguangji-web/src 里用到的汉字与字体 CSS 的 unicode-range 比对——

| 指标 | 实测 |
| --- | --- |
| 源码汉字种类 | 1267 |
| 字体分片 | 42 片，共覆盖 18,941 码位 |
| 核心片 `lw-core.woff2` | 1120 码位 / 178 KB（首屏只加载这一片） |
| 缺字 | **0** |

结论：现字体已覆盖全站文案（含 0.0.6 新增页面），**无需重跑 `gen-handdrawn-font.py`**；以后加了带新字的内容，先跑复核脚本，有缺字再重跑子集（脚本头有语料维护命令）。

## 4. Element Plus 组件（FR-007-3 / D-43）

| EP 组件 | 做法 |
| --- | --- |
| `el-input / textarea / checkbox / radio / switch / pagination / empty / skeleton / upload / image` | 换肤（EP 变量映射到暖忆值 + `hd-*` 形制覆盖） |
| `el-select / el-date-picker` | 触发器换肤，浮层自绘（纸片 + 手写星期；不做时分秒） |
| `el-dialog / el-drawer / el-message / el-message-box` | 自绘（纸片 + 胶带 + 墨色薄雾；标题即动作） |

映射表见设计图 `components.html` §Element Plus 映射。

## 5. 默认封面（A + C，已定稿）

- 组件 `hd-fallback`（结构：`.fc-band` 类型签 + `.fc-body > .fc-title/.fc-meta` + `.fc-motif` 照片层）：
  - **A 影像封面**：模块网格（影单 / 书单 / 足迹）用——照片打底、标题压底部暗角；
  - **C 手账拼贴**：`.sm` 小尺寸（首页时间线、回顾）用——纸卡 + 胶带照片、不放字；
  - `.xs` 极小尺寸（搜索、关联条目）只留画面。
- 素材 `docs/design/mockups/handdrawn-0.0.6/assets/covers/`（公有领域 / CC0，含 `SOURCES.md`）随包分发到前端静态资源，不依赖外链；网格内按 `nth-child` 轮换底图与裁切。

## 6. 页面宽度口径

前台外壳内容区 1248px，模块页统一 **1120** 内容宽（`--hd-max`）。心愿（860）、回顾（900）为设计稿屏幕宽的历史遗留，统一归位 1120：心愿两栏（清单 760 + 侧栏 300），回顾多栏报告（地图 2/3 + 评分榜 1/3，标签 + 年度笔记并排）。

## 7. 暗色与动效

- 暗色 = 中性暖炭 + 纸色字，非反相；每页随换装同步校准。
- 主题切换沿用 `animateThemeToggle` 圆形扩散；悬停微抬 + 轻旋转（≤0.6°）；`prefers-reduced-motion` 下全部关闭。

## 8. 兼容与回归

- 视觉切换不得改变 DOM 语义与可访问性。
- 回归清单：增删改查、筛选、地图、笔记读写（草稿 / 互链 / 搬运）、主题切换、移动端；见测试用例文档 §回归。
