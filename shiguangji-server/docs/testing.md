# 测试说明

后端测试使用 JUnit 5、Spring Boot Test、MockMvc 和 AssertJ。测试类位于各模块的 `src/test/java`。

## 环境准备

测试使用 `dev` profile，并需要一个已初始化的 MySQL 数据库和可连接的 Redis。环境地址可通过 `.env.example` 中的变量覆盖。

全新数据库必须依次执行：

1. `sql/init_system.sql`
2. `sql/quartz.sql`
3. `sql/init_business.sql`

## 执行测试

```bash
mvn -pl shiguangji-admin -am test
```

现有冒烟测试覆盖匿名只读、写接口鉴权、用户数据隔离、笔记公开范围、访客字段脱敏、分页筛选、回收站恢复和首页公开统计。

GitHub Actions 会启动空的 MySQL 8.4 和 Redis 7.4 服务，执行全部初始化 SQL 后运行同一测试命令。
