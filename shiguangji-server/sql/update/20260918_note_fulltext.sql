-- ----------------------------
-- 笔记全文检索增量脚本（issue #30：笔记正文可检索，标题 + 正文）
-- 功能：sgj_note 复合全文索引（ngram 分词器，中文按 2 字切 token）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（按 information_schema 判断索引存在则跳过）
-- 注意：ADD FULLTEXT 会锁表重建索引并为存量行建倒排，数据量大时挑低峰执行
-- 约束：ngram_token_size（默认 2）是只读启动变量，改动须重建本索引；
--       单个汉字切不出 token 搜不到，前端搜索框须有最小长度提示
-- ----------------------------

set names utf8mb4;

set @fts_exists := (
  select count(*) from information_schema.statistics
  where table_schema = database()
    and table_name = 'sgj_note'
    and index_name = 'ft_note_title_content'
);
set @add_fts := if(
  @fts_exists = 0,
  'alter table sgj_note add fulltext key ft_note_title_content (title, content) with parser ngram',
  'select ''ft_note_title_content 已存在，跳过'' as note'
);
prepare stmt from @add_fts;
execute stmt;
deallocate prepare stmt;
