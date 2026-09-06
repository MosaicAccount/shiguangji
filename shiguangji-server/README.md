# 拾光记 Server

拾光记后端 API。前台匿名接口仅公开站长（`publicOwner` 配置的账号）的公开数据；登录用户按创建者隔离管理自己的条目与笔记。基于 Java 17、Spring Boot、MyBatis 和 RuoYi 3.9.2 的多模块结构开发。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 / 运行时 | Java 17 |
| 框架 | Spring Boot（多模块 Maven 工程） |
| ORM | MyBatis + PageHelper |
| 数据库 | MySQL 8.0+ |
| 缓存 | Redis 7+（登录态、验证码、缓存） |
| 安全 | Spring Security + JWT |
| 任务调度 | Quartz |

## 模块结构

| 模块 | 职责 |
| --- | --- |
| `shiguangji-admin` | 应用入口、HTTP 接口与集成测试 |
| `shiguangji-business` | 电影、电视剧、书籍、地点、笔记等业务能力 |
| `shiguangji-system` | 用户、角色、菜单、字典等系统能力 |
| `shiguangji-framework` | 安全、数据源等基础设施 |
| `shiguangji-common` | 通用工具与常量 |
| `shiguangji-quartz` | 定时任务 |
| `shiguangji-generator` | 代码生成 |

MyBatis XML 位于各模块 `src/main/resources/mapper/`。

## 环境要求

- JDK 17（机器默认 JDK 为 8 时，需通过 `JAVA_HOME` 指向 JDK 17）
- Maven 3.9+
- MySQL 8.0+（开发默认 `localhost:13306`）
- Redis 7+（开发默认 `localhost:16379`）

## 配置

复制 `.env.example` 并按本机环境导出变量（Spring Boot 不自动读取该文件，需由 shell、IDE 或部署平台注入）：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 激活的 profile | `dev` |
| `SERVER_PORT` | HTTP 端口 | `18080` |
| `MYSQL_URL` | JDBC 连接串 | `localhost:13306/shiguangji` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号 | `root` / `123456` |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost:16379` |
| `REDIS_DATABASE` / `REDIS_PASSWORD` | Redis 库号与密码 | `0` / 空 |
| `JWT_SECRET` | JWT 签名密钥，不少于 32 字符 | 无，必须注入 |
| `SGJ_UPLOAD_PATH` | 上传文件目录 | `/tmp/shiguangji/uploadPath` |

`application.yml` 中的 `shiguangji.publicOwner` 决定匿名前台展示的公开博主账号（默认 `admin`）。

## 数据库初始化

**三个脚本均为破坏性初始化脚本（含 `DROP TABLE IF EXISTS`），只能用于全新数据库，不能在已有数据的环境重复执行。** 按以下顺序执行：

```text
sql/init_system.sql
sql/quartz.sql
sql/init_business.sql
```

## 构建与运行

```bash
mvn clean package -DskipTests
java -jar shiguangji-admin/target/shiguangji-admin.jar
```

接口默认监听 `18080`。开发环境 Swagger UI 地址为 `http://localhost:18080/swagger-ui.html`（`prod` profile 下关闭）。生产部署要求见 [部署说明](docs/deployment.md)。

与前端联调：前端开发服务器会把 `/dev-api` 前缀代理到本服务，无需额外跨域配置。运行日志由 Logback 写入运行目录下的 `logs/`（`sys-info.log`、`sys-error.log`、`sys-user.log`，按天滚动归档）。

## 测试

```bash
mvn -pl shiguangji-admin -am test
```

测试使用 `dev` profile，需要已按上述顺序初始化的 MySQL 和可连接的 Redis（地址可用环境变量覆盖），详细步骤见[测试说明](docs/testing.md)。

`AppApiAuthIsolationSmokeTest` 基于 MockMvc 覆盖匿名浏览、写接口鉴权、用户数据隔离、公开笔记可见性、访客脱敏、分页上限、软删除 / 回收站恢复、首页公开统计等 10 条端到端冒烟用例。

## 持续集成

`.github/workflows/ci.yml` 在 push 和 pull request 时使用 Java 17，启动 MySQL 8.4 与 Redis 7.4 服务容器，按顺序执行三个 SQL 脚本后运行 `mvn -pl shiguangji-admin -am test`。

## 开源许可

项目基于 RuoYi 的 MIT 许可代码开发，版权信息见 [LICENSE](LICENSE)。
