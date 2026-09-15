-- ----------------------------
-- 孤儿文件清理增量脚本（issue #9）
-- 功能：注册每周一次的孤儿文件清理定时任务
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（任务按 invoke_target 先删后插；建表用 if-not-exists 不丢数据）
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 文件台账表：仅兜底未执行过 20260907_sys_file.sql 的历史库，
-- init_system.sql 已含本表。刻意不用 drop，避免清空已有台账数据
-- ----------------------------
create table if not exists sys_file (
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

-- ----------------------------
-- 孤儿文件清理任务
-- 每周一 03:00 对账存储与台账，回收无人引用的上传文件；
-- 保留 7 天内的文件与回收站（del_flag='2'，可恢复）中的文件。
-- invoke_target 指向 com.shiguangji.quartz.task.FileCleanupTask#cleanupOrphanFiles
-- ----------------------------
delete from sys_job where invoke_target = 'fileCleanupTask.cleanupOrphanFiles';
insert into sys_job (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values ('孤儿文件清理', 'DEFAULT', 'fileCleanupTask.cleanupOrphanFiles', '0 0 3 ? * MON', '3', '1', '0', 'admin', sysdate(),
        '每周一 03:00 回收无人引用的上传文件（保留 7 天内与回收站中的文件）');
