# 标签管理 - 设计方案

> 关联需求文档：[tag-management-requirements.md](./tag-management-requirements.md)
> 分支：`feature/add-tag-management`

## 1. 现状分析

- 标签为手输、英文逗号分隔字符串，直接存于 `sgj_item.tags` 和 `sgj_note.tags`（varchar(500)）；
- 查询用 LIKE 模糊匹配：`SgjItemMapper.xml` L124、`SgjNoteMapper.xml` L48（`tags like concat('%', #{tags}, '%')`），存在子串误匹配；
- 无标签表、无标签接口；前端输入组件为普通 `el-input`（`ItemEditDialog/index.vue` L16、`front/note/index.vue` L79 等）；
- 影视/书籍/旅行共用 `sgj_item`（`item_type` 区分），笔记独立 `sgj_note`；
- 后台菜单注册在 `sys_menu`（`sql/init_business.sql`），接口权限用 `@PreAuthorize("@ss.hasPermi('sgj:*:*')")`。

## 2. 方案选型

| 方案 | 说明 | 评估 |
|---|---|---|
| A. 标签字典表 + 保留逗号列（**采用**） | 新增 `sgj_tag` 表承载标签定义与模块归属；记录侧继续存逗号分隔文本；筛选用 `FIND_IN_SET` 精确匹配 | 改动最小：记录保存/展示/导出链路全部不动，仅新增字典 CRUD + 替换筛选 SQL + 前端换组件。缺点：无引用完整性，删/改标签不回写历史记录（需求已接受，见 FR-5） |
| B. 标签表 + 记录-标签关联表 | `sgj_tag` + 关联表（module + ref_id），记录列表 join 查询 | 引用完整、统计容易，但保存/查询/迁移/前端类型全链路改造，工作量为 A 的数倍，当前无统计需求，**不采用**。作为后续升级路径 |

选 A。边界（删标签后记录保留旧文字、重命名不同步历史记录）已在需求文档中声明为已知约束。

## 3. 数据库设计

新增表（增量脚本 `sql/update/20260906_tag_management.sql`，不动 init 脚本主体，init 由增量合并维护）：

```sql
create table sgj_tag (
  tag_id        bigint(20)   not null auto_increment comment '标签ID',
  module        varchar(20)  not null                comment '所属模块（MOVIE/TV/BOOK/PLACE/NOTE）',
  tag_name      varchar(50)  not null                comment '标签名称',
  sort          int(4)       default 0               comment '显示排序',
  status        char(1)      default '0'             comment '状态（0启用 1停用）',
  remark        varchar(500) default null            comment '备注',
  del_flag      char(1)      default '0'             comment '删除标志（0存在 2删除）',
  create_by     varchar(64)  default ''              comment '创建者',
  create_time   datetime                             comment '创建时间',
  update_by     varchar(64)  default ''              comment '更新者',
  update_time   datetime                             comment '更新时间',
  primary key (tag_id),
  unique key uk_module_tag (module, tag_name, del_flag)
) engine=innodb comment='标签表';
```

- `module` 直接复用 `sgj_item.item_type` 的取值（MOVIE/TV/BOOK/PLACE）并扩展 NOTE，前端新增字典类型 `sgj_tag_module`（沿用 `sgj_item_type` 的字典模式）；
- 唯一约束实现 FR-4（同模块同名唯一），Service 层同时做友好校验；逻辑删除（del_flag）沿用 RuoYi 惯例；
- 存量迁移：用 `JSON_TABLE` 拆分 `sgj_item.tags` / `sgj_note.tags`，去重后按记录模块写入 `sgj_tag`（见第 8 节脚本）。

## 4. 后端设计

### 4.1 新增文件（全部对照现有 SgjNote/SgjItem 模板）

| 层 | 文件 |
|---|---|
| 实体 | `shiguangji-business/.../domain/SgjTag.java` |
| Mapper | `shiguangji-business/.../mapper/SgjTagMapper.java` + `resources/mapper/business/SgjTagMapper.xml` |
| Service | `shiguangji-business/.../service/ISgjTagService.java` + `impl/SgjTagServiceImpl.java` |
| 后台 Controller | `shiguangji-admin/.../web/controller/business/SgjTagController.java`，路由 `/business/tag` |
| 用户端 Controller | 复用 `SgjTagController` 或在 `AppItemController` 同级新增 `AppTagController`，路由 `/app/tag/list?module=MOVIE`，`@Anonymous` 放行（标签定义非敏感数据，与现有 AppItemController 浏览接口一致） |

### 4.2 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/business/tag/list` | `sgj:tag:list` | 分页列表，参数：module、tagName(模糊)、status |
| GET | `/business/tag/{tagId}` | `sgj:tag:query` | 详情 |
| POST | `/business/tag` | `sgj:tag:add` | 新增，校验同模块重名 |
| PUT | `/business/tag` | `sgj:tag:edit` | 修改 |
| DELETE | `/business/tag/{tagIds}` | `sgj:tag:remove` | 逻辑删除（不回写记录） |
| GET | `/app/tag/list` | 匿名 | 参数 module（必填）+ status=0，按 sort 排序，返回启用标签，供前台下拉 |

### 4.3 筛选 SQL 改造（修复子串误匹配）

`SgjItemMapper.xml`、`SgjNoteMapper.xml` 的标签筛选由 LIKE 改为：

```sql
<if test="tags != null and tags != ''">
  and find_in_set(#{tags}, replace(i.tags, ' ', ''))
</if>
```

- `FIND_IN_SET` 按逗号分段完整匹配，消除「科幻」误命中「硬科幻」的问题；
- 后台 `SgjItemController`/`SgjNoteController` 与用户端 App 接口共用同一 Mapper 查询，一处修改两端生效；
- 同时清理 `SgjItemServiceImpl`/`SgjNoteServiceImpl` 中对 tags 的 500 字符长度校验逻辑——保留校验即可，无需改动（选择式输入不会超长）。

## 5. 前端设计

### 5.1 新增「标签管理」页面

- `src/views/business/tag/index.vue`：列表 + 模块下拉筛选 + 名称搜索 + 新增/编辑弹窗 + 删除，完全对照 `views/business/note/index.vue` 模板实现；
- `src/api/business/tag.ts` + `src/types/api/business/tag.ts`；
- 菜单 SQL 插入 `sys_menu`（目录「拾光记」下新增菜单项 component=`business/tag/index`，权限 `sgj:tag:list/query/add/edit/remove`）。

### 5.2 标签选择组件（核心改造点）

新增 `src/components/TagSelect/index.vue`：

- props：`modelValue`（逗号分隔字符串）、`module`（必填）；内部请求 `/app/tag/list` 获取该模块启用标签，转 multi-select（el-select multiple，`filterable` 但**不开启 allow-create**，落实「禁止手输」）；
- v-model 与逗号分隔字符串互转，保证与现有 `tags` 字段格式兼容，父组件零改动；
- 使用位置（替换现有 el-input / 补齐缺失字段）：
  1. `src/components/ItemEditDialog/index.vue`（影视/书籍/旅行编辑共用，module 随 item_type 传入）；
  2. `src/views/front/movie/index.vue`、`book/index.vue`、`travel/index.vue` 新增表单（补齐 tags 字段）；
  3. `src/views/front/note/index.vue` 新增/编辑表单；
  4. `src/views/business/note/index.vue`（后台笔记管理表单）及 `ItemManager.vue` 的 tags 字段配置改为 TagSelect；
- 列表筛选：前台四个模块页与 `ItemManager.vue` 的 tags 筛选输入改为 TagSelect（单选），提交值仍为标签名。

## 6. 权限与菜单

- 权限串：`sgj:tag:list` / `sgj:tag:query` / `sgj:tag:add` / `sgj:tag:edit` / `sgj:tag:remove`，后端 `@PreAuthorize` + `@Log(title = "标签管理", businessType = ...)`；
- 菜单/权限由增量 SQL 插入 `sys_menu`，管理员角色需在角色管理中勾选（或 SQL 同步给 admin 角色赋权）。

## 7. 迁移方案

增量脚本 `sql/update/20260906_tag_management.sql` 顺序执行：

1. 建 `sgj_tag` 表；
2. 插入字典类型 `sgj_tag_module` 与 5 个字典项；
3. 存量迁移（JSON_TABLE 拆分去重，示例）：

```sql
insert into sgj_tag (module, tag_name, create_by, create_time)
select distinct t.module, t.tag, 'admin', now()
from (
  select i.item_type as module, jt.tag
  from sgj_item i
  join json_table(
    concat('["', replace(i.tags, ',', '","'), '"]'),
    '$[*]' columns (tag varchar(50) path '$')
  ) jt where i.tags is not null and i.tags != ''
  union all
  select 'NOTE', jt.tag
  from sgj_note n
  join json_table(
    concat('["', replace(n.tags, ',', '","'), '"]'),
    '$[*]' columns (tag varchar(50) path '$')
  ) jt where n.tags is not null and n.tags != ''
) t
where t.tag != ''
on duplicate key update tag_name = values(tag_name);
```

4. 插入 `sys_menu` 菜单与按钮权限。

迁移后 `sgj_item.tags` / `sgj_note.tags` **原样保留**，不做清理，保证可回滚（drop 新表 + 菜单即回到现状）。

## 8. 测试计划

- 后端：`SgjTagServiceImpl` 新增单测（重名校验、FIND_IN_SET 筛选命中/不命中子串）；`mvn -pl shiguangji-admin -am test` 回归存量冒烟测试；
- 迁移脚本在 dev 库（localhost:13306）执行后人工核对 `sgj_tag` 与各记录 tags 的一致性（条数、无遗漏标签）；
- 前端：`npx vue-tsc --noEmit` + `npm run build:prod`；
- 手工验收：按需求文档第 6 节验收标准逐条过（后台 CRUD/模块筛选/重名拦截、前台五模块选择标签、按标签精确筛选、旧数据展示不回退）。

## 9. 实施顺序（小步提交）

1. `feat:` 增量 SQL（建表 + 字典 + 菜单）与实体/Mapper/Service/Controller，含单测；
2. `feat:` 筛选 SQL LIKE→FIND_IN_SET（后端回归）；
3. `feat:` 前端标签管理页 + api/types；
4. `feat:` TagSelect 组件并替换五处表单/筛选入口；
5. `feat:` 存量迁移脚本验证；
6. 合并回 develop。
