-- ----------------------------
-- 文件存储服务增量脚本
-- 功能：文件台账表（登记 /common/upload 与头像上传记录，供审计/清理/迁移使用）
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（建表用 drop-if-exists）
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 文件台账表
-- storage_key 与业务表（如 sgj_item.cover_url）保存的 /profile 前缀相对路径一致，
-- 孤儿清理/迁移时可直接字符串比对；不记录业务关联，引用关系由业务表持有
-- ----------------------------
drop table if exists sys_file;
create table sys_file (
  file_id        bigint(20)      not null auto_increment    comment '文件ID',
  file_name      varchar(255)    default ''                 comment '原始文件名',
  storage_key    varchar(500)    not null                   comment '存储key（/profile前缀相对路径）',
  storage_type   varchar(20)     not null default 'local'   comment '存储类型（local/s3）',
  file_size      bigint(20)      default 0                  comment '文件大小（字节）',
  content_type   varchar(100)    default ''                 comment 'MIME类型',
  create_by      varchar(64)     default ''                 comment '上传者',
  create_time    datetime                                   comment '上传时间',
  primary key (file_id),
  unique key uk_sys_file_storage_key (storage_key)
) engine=innodb auto_increment=1 comment = '系统-文件台账表';
