# 拾光记

拾光记是一个用于记录电影、电视剧、书籍、旅行地点和笔记的个人内容管理系统。前台可匿名浏览站长公开的内容，登录用户在后台按账号隔离管理自己的数据。

## 产品形态

- **前台内容站**：匿名访客可浏览公开的电影、电视剧、书籍、旅行足迹和公开笔记；私密短评、备注和笔记不对访客下发。
- **后台管理端**：登录用户维护自己的条目与笔记（评分、标签、封面、Markdown 笔记、地图选点、软删除回收站），并按角色权限访问系统管理功能。
- **公开数据边界**：前台匿名接口仅展示 `publicOwner` 配置账号（默认 `admin`）的公开数据，即"一人公开博客"模型。
- **明确不做**：部门 / 岗位 / 角色数据权限（已从代码和 SQL 移除）、多租户、移动端 App、第三方元数据自动抓取。

## 仓库结构

本仓库为单一 Git 仓库（monorepo），包含前后端两个应用目录：

| 目录 | 说明 |
| --- | --- |
| [`shiguangji-web/`](shiguangji-web) | 前端：Vue 3、TypeScript、Vite、Pinia、Element Plus |
| [`shiguangji-server/`](shiguangji-server) | 后端：Java 17、Spring Boot、MyBatis、Maven 多模块（基于 RuoYi 3.9.2 改造） |

`docs/` 存放工作区级梳理文档；协作约定见 [AGENTS.md](AGENTS.md)。CI 按 `shiguangji-web/`、`shiguangji-server/` 路径变更分别触发（见 `.github/workflows/`）。

## 快速开始

后端（需要 MySQL 8 与 Redis 7，先按 `shiguangji-server/README.md` 初始化数据库并导出 `.env.example` 中的变量）：

```bash
cd shiguangji-server
mvn clean package -DskipTests
java -jar shiguangji-admin/target/shiguangji-admin.jar   # 默认端口 18080
```

前端（Node.js 22）：

```bash
cd shiguangji-web
npm ci
npm run dev   # 默认端口 5173，/dev-api 代理到 18080
```

数据库连接、Redis、JWT 密钥等一律通过 `MYSQL_URL`、`REDIS_HOST`、`JWT_SECRET` 等环境变量注入，不要写入仓库。
