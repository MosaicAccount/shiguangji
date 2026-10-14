# 拾光记

记录电影、电视剧、书籍、旅行地点和笔记的个人内容管理系统。前台是匿名可浏览的「一人公开博客」，登录用户在后台按账号隔离管理自己的数据。

## 功能

四个内容模块共用一套工作流：**想看 → 看过 / 读完 / 去过（评分、短评、日期）→ 首页时间线**。

- **影单 / 书单**：电影、电视剧、书籍的想看与看过管理，封面、评分、短评
- **足迹**：地图选点、足迹地图（想去 / 去过）、旅行照片
- **笔记**：Markdown 分屏编辑与实时预览、大纲导航、代码块语法高亮；标题 + 正文全文检索（命中片段高亮）；`.md` 导入导出；正文插图（工具栏按钮、粘贴截图），导入时本地图片与外链图片自动搬运，外链图可选转存到本站；草稿箱（编辑自动保存、离开提示，发布即清理）
- **通用**：统一标签体系（五模块共享、仅可选择）、软删除回收站、夜间模式、移动端适配；文件存储本地磁盘与 S3 协议（MinIO / 阿里云 OSS）可切换，孤儿文件自动清理；基于 RuoYi 的用户 / 角色 / 菜单 / 操作日志后台

## 产品形态与数据边界

- **前台内容站**：匿名访客可浏览 `publicOwner` 配置账号（默认 `admin`）公开的影单、书单、足迹与公开笔记；私密短评、备注和笔记不对访客下发。
- **后台管理端**：登录用户维护自己的条目与笔记，按角色权限访问系统管理功能。
- **明确不做**：多租户、移动端 App、第三方元数据自动抓取。

## 版本与路线

当前版本 **v0.0.2「收口笔记」**：笔记从「能写」变成「写不丢、找得回」——草稿箱、全文检索、md 导入导出、图片搬运与外链转存。

后续版本（0.0.3 计划、0.0.4 回顾、0.0.5 链接）与待评估方向见 [docs/requirements/README.md](docs/requirements/README.md) 的版本总览，需求文档是唯一事实来源。

## 快速开始

后端（需要 MySQL 8 与 Redis 7，先按 [`shiguangji-server/README.md`](shiguangji-server/README.md) 初始化数据库，并导出 [`shiguangji-server/.env.example`](shiguangji-server/.env.example) 中的变量）：

```bash
cd shiguangji-server
mvn clean package -DskipTests
java -jar shiguangji-admin/target/shiguangji-admin.jar   # 默认端口 18080
```

前端（Node.js 22）：

```bash
cd shiguangji-web
npm ci
npm run dev        # 默认端口 5173，/dev-api 代理到 18080
npm run test:unit  # 前端单元测试（vitest）
```

数据库连接、Redis、JWT 密钥等一律通过 `MYSQL_URL`、`REDIS_HOST`、`JWT_SECRET` 等环境变量注入，不要写入仓库。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| [`shiguangji-web/`](shiguangji-web) | 前端：Vue 3、TypeScript、Vite、Pinia、Element Plus |
| [`shiguangji-server/`](shiguangji-server) | 后端：Java 17、Spring Boot、MyBatis、Maven 多模块（基于 RuoYi 改造） |
| [`docs/`](docs) | 版本需求（`requirements/`，未拍板的集中在 `pending.md`）与设计方案（`design/`） |

协作约定见 [AGENTS.md](AGENTS.md)；CI 按 `shiguangji-web/`、`shiguangji-server/` 路径变更分别触发（见 `.github/workflows/`）。

## License

[MIT](LICENSE)（含 RuoYi 原始版权声明）
