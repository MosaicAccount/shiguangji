-- ----------------------------
-- 笔记草稿箱增量脚本（issue #35 / #37：服务端草稿箱）
-- 功能：新建草稿表 sgj_note_draft（新笔记草稿 + 编辑态未保存改动）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（create table if not exists，不 drop——重跑不应清掉用户已有的草稿）
-- 说明：独立表而非给 sgj_note 加 is_draft 标志位——草稿与正式笔记同表时，
--       现有六处查笔记的地方都得记得排除草稿，漏一处即草稿静默进入列表 / 统计 / 导出；
--       独立表让这些查询物理上查不到草稿。详见 docs/design/note-0.0.2-design.md §2.4
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 笔记草稿表
-- note_id 为空表示「新笔记草稿」（只有这类进草稿箱）；不为空表示某篇笔记的编辑态草稿。
-- 不加 item_id / note_id 外键：草稿是临时数据，条目或笔记删除时由代码显式清理
-- （SgjNoteDraftMapper.deleteByNoteIds / deleteByItemIds），不做隐式级联。
-- 不加 del_flag：草稿删除即物理删除，不进回收站，避免出现第二个暂存区。
-- unique key (create_by, note_id)：每篇笔记每人最多一份编辑态草稿的并发兜底；
-- note_id 为 NULL 时唯一索引互不相等，故「新笔记草稿不限量」是该约束自带的语义
-- ----------------------------
create table if not exists sgj_note_draft (
  draft_id     bigint(20)      not null auto_increment    comment '草稿ID',
  note_id      bigint(20)      default null               comment '编辑来源笔记ID；空=新笔记草稿（只有这类进草稿箱）',
  item_id      bigint(20)      default null               comment '关联条目ID，空为独立笔记',
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
