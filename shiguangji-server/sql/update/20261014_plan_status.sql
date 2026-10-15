-- ----------------------------
-- 0.0.3 计划：状态字典加 DOING（issue #76 / #74）
-- 功能：影视 / 阅读两套状态字典各新增一条 DOING（在看 / 在读），既有 DONE 改序为 3，
--       让状态胶囊的顺序稳定为 想看 / 在看 / 看过（想读 / 在读 / 已读）。
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行——DOING 行按 dict_value 先删后插（照 20260906_tag_management.sql 的约定），
--       DONE 改序为无条件的 update，重复执行结果不变
-- 说明：地点字典 sgj_place_status 不加 DOING（0.0.3 D-5：中间态对地点语义别扭，
--       足迹以「想去 + 计划日期」表达计划）；无表结构变更，status 为自由文本列。
-- ----------------------------

set names utf8mb4;

-- 影视：在看
delete from sys_dict_data where dict_type = 'sgj_movie_status' and dict_value = 'DOING';
insert into sys_dict_data values(112, 2, '在看', 'DOING', 'sgj_movie_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '在看');

-- 阅读：在读
delete from sys_dict_data where dict_type = 'sgj_book_status' and dict_value = 'DOING';
insert into sys_dict_data values(122, 2, '在读', 'DOING', 'sgj_book_status', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '在读');

-- 既有 DONE 行排序 2 -> 3（DOING 插在中间）
update sys_dict_data set dict_sort = 3
 where dict_type in ('sgj_movie_status', 'sgj_book_status') and dict_value = 'DONE';

-- 字典类型描述同步（幂等：重复执行覆盖为同一文案）
update sys_dict_type set remark = '影视条目状态（想看/在看/看过）' where dict_type = 'sgj_movie_status';
update sys_dict_type set remark = '书籍条目状态（想读/在读/已读）' where dict_type = 'sgj_book_status';
