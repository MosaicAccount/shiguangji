-- ----------------------------
-- 标签管理增量脚本
-- 功能：后台标签管理（按模块区分）+ 记录按标签精确筛选
-- 前置：已执行 init_system.sql 与 init_business.sql
-- 幂等性：可重复执行（建表用 drop-if-exists，菜单/字典/迁移均先清理或防重）
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 1、标签表
-- module 复用 sgj_item.item_type 取值（MOVIE/TV/BOOK/PLACE）并扩展 NOTE
-- 同模块同名的唯一性由服务层校验（RuoYi 惯例）：逻辑删除行会与唯一约束冲突，
-- 故仅建普通索引，不建 unique key
-- ----------------------------
drop table if exists sgj_tag;
create table sgj_tag (
  tag_id       bigint(20)   not null auto_increment    comment '标签ID',
  module       varchar(20)  not null                   comment '所属模块（MOVIE/TV/BOOK/PLACE/NOTE）',
  tag_name     varchar(50)  not null                   comment '标签名称',
  sort         int(4)       default 0                  comment '显示排序',
  status       char(1)      default '0'                comment '状态（0启用 1停用）',
  del_flag     char(1)      default '0'                comment '删除标志（0存在 2删除）',
  create_by    varchar(64)  default ''                 comment '创建者',
  create_time  datetime                                comment '创建时间',
  update_by    varchar(64)  default ''                 comment '更新者',
  update_time  datetime                                comment '更新时间',
  remark       varchar(500) default null               comment '备注',
  primary key (tag_id),
  key idx_sgj_tag_module (module, del_flag)
) engine=innodb auto_increment=1 comment = '拾光记-标签表';

-- ----------------------------
-- 2、菜单与按钮权限（拾光记目录 2000 下，排序 6，回收站为 5）
-- ----------------------------
delete from sys_menu where menu_id between 2016 and 2020;
insert into sys_menu values('2016', '标签管理', '2000', '6', 'tag', 'business/tag/index', '', '', 1, 0, 'C', '0', '0', 'sgj:tag:list', 'component', 'admin', sysdate(), '', null, '按模块管理标签');
insert into sys_menu values('2017', '标签查询', '2016', '1', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:tag:query',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2018', '标签新增', '2016', '2', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:tag:add',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2019', '标签修改', '2016', '3', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:tag:edit',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2020', '标签删除', '2016', '4', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:tag:remove', '#', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 3、标签模块字典
-- ----------------------------
delete from sys_dict_data where dict_type = 'sgj_tag_module';
delete from sys_dict_type where dict_type = 'sgj_tag_module';
insert into sys_dict_type values(40, '标签模块', 'sgj_tag_module', '0', 'admin', sysdate(), '', null, '标签所属业务模块');
insert into sys_dict_data values(220, 1, '电影',   'MOVIE', 'sgj_tag_module', '', 'primary', 'N', '0', 'admin', sysdate(), '', null, '电影');
insert into sys_dict_data values(221, 2, '电视剧', 'TV',    'sgj_tag_module', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '电视剧');
insert into sys_dict_data values(222, 3, '书籍',   'BOOK',  'sgj_tag_module', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '书籍');
insert into sys_dict_data values(223, 4, '地点',   'PLACE', 'sgj_tag_module', '', 'info',    'N', '0', 'admin', sysdate(), '', null, '地点');
insert into sys_dict_data values(224, 5, '笔记',   'NOTE',  'sgj_tag_module', '', 'default', 'N', '0', 'admin', sysdate(), '', null, '笔记');

-- ----------------------------
-- 4、存量标签迁移（幂等）
-- 拆分 sgj_item.tags / sgj_note.tags 逗号分隔文本，按记录所属模块写入 sgj_tag；
-- 业务表 tags 列原样保留，不做清理（可回滚：drop sgj_tag 即回到现状）
-- 注意：同一 INSERT...SELECT 内 not exists 看不到本语句已插入的行，
-- 语句内去重必须由 distinct 完成，not exists 仅负责跨语句幂等（脚本重复执行不重复插入）
-- ----------------------------
insert into sgj_tag (module, tag_name, create_by, create_time, remark)
select distinct t.module, t.tag, 'admin', now(), '存量迁移'
from (
  select i.item_type as module, trim(jt.tag) as tag
  from sgj_item i, json_table(
    concat('["', replace(replace(i.tags, ' ', ''), ',', '","'), '"]'),
    '$[*]' columns (tag varchar(50) path '$')
  ) jt
  where i.del_flag = '0' and i.tags is not null and i.tags != ''
  union all
  select 'NOTE', trim(jt.tag)
  from sgj_note n, json_table(
    concat('["', replace(replace(n.tags, ' ', ''), ',', '","'), '"]'),
    '$[*]' columns (tag varchar(50) path '$')
  ) jt
  where n.del_flag = '0' and n.tags is not null and n.tags != ''
) t
where t.tag != ''
  and not exists (
    select 1 from sgj_tag g
    where g.module = t.module and g.tag_name = t.tag and g.del_flag = '0'
  );
