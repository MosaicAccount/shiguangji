-- ----------------------------
-- 笔记草稿箱增量脚本（issue #35 / #37：服务端草稿箱）
-- 功能：新建草稿表 sgj_note_draft（空白草稿 + 编辑态未保存改动）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（建表 create table if not exists，不 drop；补列 / 改列定义 / 去重 / 换唯一键
--        都按 information_schema 判断后跳过）
-- 说明：独立表而非给 sgj_note 加 is_draft 标志位——草稿与正式笔记同表时，
--       现有六处查笔记的地方都得记得排除草稿，漏一处即草稿静默进入列表 / 统计 / 导出；
--       独立表让这些查询物理上查不到草稿。详见 docs/design/note-0.0.2-design.md §2.4
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 笔记草稿表
-- 身份就是 (create_by, note_id)：note_id = 0 表示**空白草稿**（还没成为笔记的那一份，每人一份，
-- 由唯一键保证）；不为 0 表示某篇笔记的编辑态草稿（每篇一份）。
-- 为什么不直接用 NULL 表示「空白草稿」：MySQL 唯一索引把 NULL 视为互不相等，
-- (create_by, note_id) 里 note_id 为 NULL 时唯一键整条失效——那样空白草稿可以无限长出来。
-- item_id 只是数据（这份草稿打算挂哪个条目 / 已挂哪个条目），**不参与身份**：
-- 所以同一个人写第二篇新笔记时是覆盖掉上一份空白草稿，而不是多出一行。
-- 不加 note_id / item_id 外键：草稿是临时数据，条目或笔记删除时由代码显式清理
-- （SgjNoteDraftMapper.deleteByNoteIds / deleteByItemIds），不做隐式级联。
-- 不加 del_flag：草稿删除即物理删除，不进回收站，避免出现第二个暂存区。
-- ----------------------------
create table if not exists sgj_note_draft (
  draft_id     bigint(20)      not null auto_increment    comment '草稿ID',
  note_id      bigint(20)      not null default 0         comment '编辑来源笔记ID；0=空白草稿（每人一份）',
  item_id      bigint(20)      not null default 0         comment '关联条目ID（仅数据，不参与身份）；0=不挂条目',
  title        varchar(200)    default ''                 comment '标题（草稿可为空）',
  content      longtext                                   comment '正文（Markdown）',
  tags         varchar(500)    default ''                 comment '标签，多个用英文逗号分隔',
  is_public    char(1)         default '0'                comment '公开状态（保存后沿用到笔记）',
  create_by    varchar(64)     default ''                 comment '创建者',
  create_time  datetime                                   comment '创建时间',
  update_time  datetime                                   comment '更新时间',
  primary key (draft_id),
  unique key uk_sgj_note_draft_owner_note (create_by, note_id),
  key idx_sgj_note_draft_owner (create_by),
  key idx_sgj_note_draft_note (note_id)
) engine=innodb auto_increment=1 comment = '拾光记-笔记草稿表';

-- ----------------------------
-- 升级 1：清掉早期版本的身份写法——生成列 draft_scope 与两个旧唯一键
-- （旧键 (create_by, note_id, item_id) 比新键宽，留着会让「每人一份空白草稿」失效）
-- ----------------------------
set @uk_target := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'sgj_note_draft' and index_name = 'uk_sgj_note_draft_owner_target');
set @uk_scope := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'sgj_note_draft' and index_name = 'uk_sgj_note_draft_owner_scope');
set @drop_old_uk := if(@uk_target > 0 and @uk_scope > 0,
  'alter table sgj_note_draft drop index uk_sgj_note_draft_owner_target, drop index uk_sgj_note_draft_owner_scope',
  if(@uk_target > 0, 'alter table sgj_note_draft drop index uk_sgj_note_draft_owner_target',
  if(@uk_scope > 0, 'alter table sgj_note_draft drop index uk_sgj_note_draft_owner_scope',
  'select ''旧唯一键都不存在，跳过'' as note')));
prepare stmt from @drop_old_uk;
execute stmt;
deallocate prepare stmt;

set @scope_col := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'sgj_note_draft' and column_name = 'draft_scope');
set @drop_scope_col := if(@scope_col > 0,
  'alter table sgj_note_draft drop column draft_scope',
  'select ''draft_scope 不存在，跳过'' as note');
prepare stmt from @drop_scope_col;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 升级 2：把 NULL 收敛成 0，并把两列收紧成 not null default 0
-- （先改数据再改列定义，否则 not null 会建不上）
-- ----------------------------
update sgj_note_draft set note_id = ifnull(note_id, 0), item_id = ifnull(item_id, 0)
 where note_id is null or item_id is null;

set @note_nullable := (select is_nullable from information_schema.columns
  where table_schema = database() and table_name = 'sgj_note_draft' and column_name = 'note_id');
set @tighten := if(@note_nullable = 'YES',
  'alter table sgj_note_draft modify note_id bigint(20) not null default 0 comment ''编辑来源笔记ID；0=空白草稿（每人一份）'', modify item_id bigint(20) not null default 0 comment ''关联条目ID（仅数据，不参与身份）；0=不挂条目''',
  'select ''note_id/item_id 已是 not null，跳过'' as note');
prepare stmt from @tighten;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 升级 3：空白草稿收敛成一份（同一用户只留最新），再补上 (create_by, note_id) 唯一键
-- 「留最新、其余删掉」是「每人一份空白草稿」的代价，只在升级时执行一次
-- ----------------------------
delete d from sgj_note_draft d
  join sgj_note_draft keep
    on keep.create_by = d.create_by
   and keep.note_id = d.note_id
   and (keep.update_time > d.update_time
        or (keep.update_time = d.update_time and keep.draft_id > d.draft_id));

set @uk_owner_note := (select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'sgj_note_draft' and index_name = 'uk_sgj_note_draft_owner_note');
set @add_owner_note_uk := if(@uk_owner_note = 0,
  'alter table sgj_note_draft add unique key uk_sgj_note_draft_owner_note (create_by, note_id)',
  'select ''uk_sgj_note_draft_owner_note 已存在，跳过'' as note');
prepare stmt from @add_owner_note_uk;
execute stmt;
deallocate prepare stmt;
