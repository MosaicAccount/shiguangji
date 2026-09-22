-- ----------------------------
-- 评分精度增量脚本
-- 功能：sgj_item.rating 由 decimal(2,1) 放宽到 decimal(3,1)，让满分 10.0 能落库
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（按 information_schema 判断精度仍是 2 才改）
--
-- 背景：后端 SgjItemServiceImpl 校验的是 0 <= rating <= 10、前端输入框也是 max: 10，
-- 但 decimal(2,1) 总共只有两位数字，最大只到 9.9——界面上填 10 能过校验，
-- 落库时撞 ERROR 1264 (22003) Out of range value for column 'rating' at row 1。
-- decimal(3,1) 上限 99.9，业务上限仍由前后端校验守住 10。
-- 列只放宽精度，不改数据，不需要回填。
-- ----------------------------

set names utf8mb4;

set @still_narrow := (
  select count(*) from information_schema.columns
  where table_schema = database()
    and table_name = 'sgj_item'
    and column_name = 'rating'
    and numeric_precision = 2
);
set @widen := if(
  @still_narrow > 0,
  'alter table sgj_item modify column rating decimal(3,1) default null comment ''评分（0-10，保留一位小数）''',
  'select ''rating 已是 decimal(3,1)，跳过'' as note'
);
prepare stmt from @widen;
execute stmt;
deallocate prepare stmt;
