# 拾光记 Web

拾光记的前台内容站与后台管理界面。前台供匿名访客浏览站长公开的电影、电视剧、书籍、旅行足迹和公开笔记；登录用户在后台维护自己的条目与笔记，并按权限访问系统管理功能。

## 功能总览

- **前台内容站**（匿名可访问，仅展示公开数据）：
  - 首页：内容统计、已完成时间线、心愿单（登录后可快速标记完成）、最近公开笔记、旅行地图入口。
  - 电影 / 电视剧：分类型浏览、搜索、分页加载；登录后新增、编辑、删除、标记看过，支持评分、短评、标签、封面、导演、主演等扩展字段。
  - 书籍：想读 / 读过浏览与管理，支持作者、出版社、ISBN、页数等字段。
  - 旅行：地点列表与地图轨迹双模式，地图选点、经纬度、去过 / 想去状态、足迹轨迹（ECharts）。
  - 笔记：Markdown 编辑与预览（marked + DOMPurify 安全渲染）、公开 / 私密、关联条目。
- **后台管理**（需登录）：
  - 业务管理：仪表盘、四类条目管理、笔记管理、JSON/CSV 导出、图片上传。
  - 回收站：软删除条目与笔记的恢复、彻底删除。
  - 系统管理：用户、角色、菜单、字典、参数、通知公告、日志、在线用户、缓存监控、定时任务等（继承自 RuoYi 管理基座）。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 框架 | Vue 3.5（`<script setup>` + TypeScript 5.6） |
| 构建 | Vite 6.4 |
| 状态管理 | Pinia 3 |
| 路由 | Vue Router 4 |
| UI 组件 | Element Plus 2.13 |
| 图表 | ECharts 5.6（旅行地图） |
| Markdown | marked + DOMPurify |
| HTTP | Axios |

## 环境要求

- Node.js 22
- npm 10+
- 本地开发需要后端服务运行在 `http://localhost:18080`（见工作区根目录的后端说明）

## 快速开始

```bash
npm ci
npm run dev
```

开发服务器默认监听 `5173`，并把 `/dev-api` 前缀的请求代理到 `http://localhost:18080`。代理目标与端口可通过 `.env.development` 中的 `VITE_DEV_PORT`、`VITE_DEV_PROXY_TARGET` 调整。`/v3/api-docs` 也会代理到后端，便于在本地联调 Springdoc 接口文档。

## 环境变量

| 文件 | 用途 | 关键变量 |
| --- | --- | --- |
| `.env.development` | 本地开发 | `VITE_APP_BASE_API=/dev-api`、`VITE_DEV_PORT`、`VITE_DEV_PROXY_TARGET` |
| `.env.staging` | 预发布构建 | `VITE_APP_BASE_API=/stage-api`、`VITE_BUILD_COMPRESS=gzip` |
| `.env.production` | 生产构建 | `VITE_APP_BASE_API=/prod-api`、`VITE_BUILD_COMPRESS=gzip` |

`VITE_APP_BASE_API` 只是请求前缀，生产与预发布环境需要由网关（Nginx 等）把该前缀反向代理到后端服务。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动本地开发服务器 |
| `npm run typecheck` | TypeScript 源码检查（`tsc --noEmit`） |
| `npm run build:prod` | 生产构建，产物输出到 `dist/` |
| `npm run build:stage` | 预发布构建 |
| `npm run preview` | 本地预览构建产物 |

## 目录结构

```text
src/
  views/front/      # 前台内容站页面（home、movie、book、travel、note）
  views/business/   # 拾光记后台业务页面（电影、电视剧、书籍、地点、条目、笔记、回收站）
  views/system/     # 系统管理页面（用户、角色、菜单、字典等）
  views/monitor/    # 监控页面（在线用户、日志、缓存等）
  views/tool/       # 系统工具页面（代码生成、系统接口等）
  components/       # 可复用组件
  api/              # HTTP 客户端（按模块划分：business、front、system、monitor 等）
  store/            # Pinia 状态模块（user、permission、dict 等）
  types/api/        # 接口类型定义（与 api/ 客户端对齐）
  assets/           # 静态资源
public/             # 原样拷贝的静态资源
vite/               # Vite 构建插件与代理配置
```

## 持续集成

`.github/workflows/ci.yml` 在 push 和 pull request 时使用 Node.js 22 依次执行 `npm ci`、`npm run typecheck`（TypeScript 源码检查）与 `npm run build:prod`（生产构建）。

## 已知限制

- 旅行地图的 GeoJSON 优先读取本地 `public/map/china.json`，当前仓库未内置该文件，缺失时回退到阿里 DataV CDN（`geo.datav.aliyun.com`），生产环境依赖外网可达。
- `vue-tsc` 对旧管理基座模板的严格检查仍存在历史类型债务，CI 以 `tsc --noEmit` 源码检查 + 生产构建为准；模板类型治理列入后续版本。
- 前端目前没有单元测试框架，回归依赖人工清单与后端接口测试。

## 开源许可

项目基于 RuoYi 的 MIT 许可代码开发，版权信息见 [LICENSE](LICENSE)。
