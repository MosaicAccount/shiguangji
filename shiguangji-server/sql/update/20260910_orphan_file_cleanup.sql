-- ----------------------------
-- 孤儿文件清理增量脚本（issue #9）
-- 功能：注册每周一次的孤儿文件清理定时任务
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（任务按 invoke_target 先删后插，job_id 固定 100 与 init_system.sql 一致）
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 孤儿文件清理任务
-- 每周一 03:00 对账存储与台账，回收无人引用的上传文件；
-- 保留 7 天内的文件与回收站（del_flag='2'，可恢复）中的文件。
-- invoke_target 指向 com.shiguangji.quartz.task.FileCleanupTask#cleanupOrphanFiles
-- ----------------------------
delete from sys_job where invoke_target = 'fileCleanupTask.cleanupOrphanFiles';
insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
values (100, '孤儿文件清理', 'DEFAULT', 'fileCleanupTask.cleanupOrphanFiles', '0 0 3 ? * MON', '3', '1', '0', 'admin', sysdate(),
        '每周一 03:00 回收无人引用的上传文件（保留 7 天内与回收站中的文件）');
