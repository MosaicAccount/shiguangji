-- ----------------------------
-- 地点照片增量脚本（issue #6：旅行支持添加照片）
-- 功能：条目照片表（地点详情抽屉/后台地点管理维护，逗号顺序即展示顺序）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（建表用 drop-if-exists；仅新库使用，存量库执行会清空照片数据）
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 条目照片表
-- url 与 sgj_item.cover_url 一致，保存 /profile 前缀相对路径；
-- 保存策略为整体替换（按 item_id 删后按顺序重插），sort_order 由当前顺序生成；
-- 随 sgj_item 物理删除由外键级联清理，回收站软删/恢复不影响照片
-- ----------------------------
drop table if exists sgj_item_photo;
create table sgj_item_photo (
  photo_id     bigint(20)      not null auto_increment    comment '照片ID',
  item_id      bigint(20)      not null                   comment '关联条目ID',
  url          varchar(500)    not null                   comment '照片URL（/profile前缀相对路径）',
  sort_order   int(4)          default 0                  comment '排序号（小在前）',
  create_by    varchar(64)     default ''                 comment '创建者',
  create_time  datetime                                   comment '创建时间',
  primary key (photo_id),
  key idx_sgj_photo_item (item_id),
  constraint fk_sgj_photo_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb auto_increment=1 comment = '拾光记-条目照片表';
