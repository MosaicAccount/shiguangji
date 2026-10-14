-- ----------------------------
-- 笔记更新时间增量脚本（issue #31 / #39：新建笔记沉底）
-- 功能：给 sgj_note.update_time 补默认值，并回填历史空值
-- 前置：已执行 init_system.sql 与 init_business.sql（或本目录更早的增量脚本）
-- 幂等性：可重复执行（改列定义前按 information_schema 判断；回填只碰 update_time is null 的行）
-- 说明：SgjNoteMapper.insertSgjNote 一直只写 create_time，update_time 留空；而列表排序是
--       `order by update_time desc`，MySQL 的 NULL 在 desc 里排最后 —— 于是**新写的笔记沉在
--       列表底部**（前台 / 后台 / 回收站三处同一种排序）。修法分两侧，本脚本管存量与列默认值：
--       ① 写入侧：insert 显式写 update_time = sysdate()，DDL 再给默认值兜住漏写与手工 SQL；
--       ② 排序侧：改为 `order by coalesce(update_time, create_time) desc`，历史空值也不沉底。
--       ①② 在 SgjNoteMapper.xml，本脚本只负责 ③ 存量数据回填 ④ 列默认值。
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 升级 1：update_time 补默认值（已有的库）
-- ----------------------------
set @needs_default := (select count(*) from information_schema.columns
  where table_schema = database() and table_name = 'sgj_note' and column_name = 'update_time'
    and column_default is null);
set @ddl := if(@needs_default > 0,
  'alter table sgj_note modify update_time datetime default current_timestamp comment ''更新时间（插入时显式写入；默认值兜底）''',
  'select ''sgj_note.update_time 已有默认值，跳过'' as note');
prepare stmt from @ddl;
execute stmt;
deallocate prepare stmt;

-- ----------------------------
-- 升级 2：回填历史空值（新建 / 导入时没写的那批）
-- 按 create_time 补：它一直是显式写入的，对该行「最后修改时间」是最贴近事实的近似
-- ----------------------------
update sgj_note set update_time = create_time where update_time is null;
