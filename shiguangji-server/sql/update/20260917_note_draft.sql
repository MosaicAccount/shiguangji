-- ----------------------------
-- 笔记草稿箱增量脚本（issue #35 / #37：服务端草稿箱）
-- 功能：新建草稿表 sgj_note_draft（新建笔记草稿 + 编辑态未保存改动）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（建表用 create table if not exists，不 drop——重跑不应清掉用户已有的草稿；
--        补列 / 补唯一键 / 去重按 information_schema 判断后跳过）
-- 说明：独立表而非给 sgj_note 加 is_draft 标志位——草稿与正式笔记同表时，
--       现有六处查笔记的地方都得记得排除草稿，漏一处即草稿静默进入列表 / 统计 / 导出；
--       独立表让这些查询物理上查不到草稿。详见 docs/design/note-0.0.2-design.md §2.4
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 笔记草稿表
-- 草稿与「写作对象」一对一：编辑已有笔记时对象是那篇笔记（note_id），新建笔记时对象是关联条目
-- （item_id；没选条目就是独立的那一份）。draft_scope 把两种身份统一成一个**非空**列，
-- 唯一键 (create_by, draft_scope) 因此对两类草稿都生效。
-- 不能直接拿 (create_by, note_id) 当唯一键：MySQL 唯一索引把 NULL 视为互不相等，
-- 那样只能管住编辑态草稿，新建态（note_id 为空）可以无限增长。
-- 不加 note_id / item_id 外键：草稿是临时数据，条目或笔记删除时由代码显式清理
-- （SgjNoteDraftMapper.deleteByNoteIds / deleteByItemIds），不做隐式级联。
-- 不加 del_flag：草稿删除即物理删除，不进回收站，避免出现第二个暂存区。
-- ----------------------------
create table if not exists sgj_note_draft (
  draft_id     bigint(20)      not null auto_increment    comment '草稿ID',
  note_id      bigint(20)      default null               comment '编辑来源笔记ID；空=新建笔记草稿',
  item_id      bigint(20)      default null               comment '关联条目ID，空为独立笔记',
  title        varchar(200)    default ''                 comment '标题（草稿可为空）',
  content      longtext                                   comment '正文（Markdown）',
  tags         varchar(500)    default ''                 comment '标签，多个用英文逗号分隔',
  is_public    char(1)         default '0'                comment '公开状态（保存后沿用到笔记）',
  create_by    varchar(64)     default ''                 comment '创建者',
  create_time  datetime                                   comment '创建时间',
  update_time  datetime                                   comment '更新时间',
  draft_scope  bigint(20) generated always as
                 (if(note_id is null, -ifnull(item_id, 0) - 1, note_id)) stored
                                                          comment '写作对象身份：笔记ID为正；新建笔记为其条目的负数（未选条目 = -1）',
  primary key (draft_id),
  unique key uk_sgj_note_draft_owner_scope (create_by, draft_scope),
  key idx_sgj_note_draft_owner (create_by),
  key idx_sgj_note_draft_note (note_id)
) engine=innodb auto_increment=1 comment = '拾光记-笔记草稿表';

-- ----------------------------
-- 升级：早期版本（只有 uk_sgj_note_draft_owner_note，没有 draft_scope）建过的表
-- 顺序：先去重（同一写作对象只留最新一行），再补列 / 补唯一键 / 去掉旧的 (create_by, note_id) 唯一键
-- ----------------------------
delete d from sgj_note_draft d
  join sgj_note_draft keep
    on keep.create_by = d.create_by
   and ifnull(keep.note_id, -ifnull(keep.item_id, 0) - 1) = ifnull(d.note_id, -ifnull(d.item_id, 0) - 1)
   and (keep.update_time > d.update_time
        or (keep.update_time = d.update_time and keep.draft_id > d.draft_id));

set @scope_exists := (
  select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'sgj_note_draft' and column_name = 'draft_scope'
);
set @add_scope := if(
  @scope_exists = 0,
  'alter table sgj_note_draft add column draft_scope bigint(20) generated always as (if(note_id is null, -ifnull(item_id, 0) - 1, note_id)) stored comment ''写作对象身份：笔记ID为正；新建笔记为其条目的负数（未选条目 = -1）'' after update_time',
  'select ''draft_scope 已存在，跳过'' as note'
);
prepare stmt from @add_scope;
execute stmt;
deallocate prepare stmt;

set @scope_uk_exists := (
  select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'sgj_note_draft' and index_name = 'uk_sgj_note_draft_owner_scope'
);
set @add_scope_uk := if(
  @scope_uk_exists = 0,
  'alter table sgj_note_draft add unique key uk_sgj_note_draft_owner_scope (create_by, draft_scope)',
  'select ''uk_sgj_note_draft_owner_scope 已存在，跳过'' as note'
);
prepare stmt from @add_scope_uk;
execute stmt;
deallocate prepare stmt;

set @old_uk_exists := (
  select count(*) from information_schema.statistics
  where table_schema = database() and table_name = 'sgj_note_draft' and index_name = 'uk_sgj_note_draft_owner_note'
);
set @drop_old_uk := if(
  @old_uk_exists > 0,
  'alter table sgj_note_draft drop index uk_sgj_note_draft_owner_note',
  'select ''uk_sgj_note_draft_owner_note 已不存在，跳过'' as note'
);
prepare stmt from @drop_old_uk;
execute stmt;
deallocate prepare stmt;
