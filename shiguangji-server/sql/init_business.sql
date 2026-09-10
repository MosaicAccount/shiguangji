-- ----------------------------
-- 拾光记业务表结构（主表 + 扩展表）
-- 适用于：电影 / 电视剧 / 书籍 / 地点 / 学习笔记
-- 使用前请按顺序执行 init_system.sql 与 quartz.sql
-- ----------------------------

-- 统一连接字符集，避免客户端默认 latin1 导入导致中文双重编码乱码
set names utf8mb4;

-- ----------------------------
-- 1、内容条目主表
-- 统一存放四类条目的公共字段
-- item_type: MOVIE=电影 TV=电视剧 BOOK=书籍 PLACE=地点
-- status: 按类型使用统一枚举，例如 WANT=想看/想读/想去 DONE=看过/读过/去过
-- ----------------------------
drop table if exists sgj_item;
create table sgj_item (
  item_id        bigint(20)      not null auto_increment    comment '条目ID',
  item_type      varchar(20)     not null                   comment '条目类型（MOVIE/TV/BOOK/PLACE）',
  title          varchar(200)    not null                   comment '标题/名称',
  status         varchar(20)     not null default 'WANT'    comment '状态（WANT/DONE）',
  rating         decimal(2,1)    default null               comment '评分（0-10，可自行决定精度）',
  comment        text                                       comment '个人短评',
  tags           varchar(500)    default ''                 comment '标签，多个用英文逗号分隔',
  cover_url      varchar(500)    default ''                 comment '封面图/图片地址',
  start_date     date            default null               comment '开始日期（观看/阅读/计划时间）',
  finish_date    date            default null               comment '完成日期',
  del_flag       char(1)         default '0'                comment '删除标志（0存在 2删除）',
  create_by      varchar(64)     default ''                 comment '创建者',
  create_time    datetime                                   comment '创建时间',
  update_by      varchar(64)     default ''                 comment '更新者',
  update_time    datetime                                   comment '更新时间',
  remark         varchar(500)    default null               comment '备注',
  primary key (item_id),
  key idx_sgj_item_type_status (item_type, status),
  key idx_sgj_item_title (title)
) engine=innodb auto_increment=1 comment = '拾光记-内容条目主表';

-- ----------------------------
-- 2、电影扩展表
-- ----------------------------
drop table if exists sgj_item_movie;
create table sgj_item_movie (
  item_id          bigint(20)      not null                comment '关联主表ID',
  director         varchar(200)    default ''              comment '导演',
  actors           varchar(500)    default ''              comment '主演',
  genre            varchar(200)    default ''              comment '类型/题材',
  region           varchar(100)    default ''              comment '地区',
  language         varchar(100)    default ''              comment '语言',
  release_year     int(4)          default null            comment '上映年份',
  duration_minutes int(4)          default null            comment '片长（分钟）',
  imdb_id          varchar(50)     default ''              comment 'IMDb编号',
  douban_id        varchar(50)     default ''              comment '豆瓣编号',
  primary key (item_id),
  constraint fk_sgj_movie_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb comment = '拾光记-电影扩展表';

-- ----------------------------
-- 3、电视剧扩展表
-- ----------------------------
drop table if exists sgj_item_tv;
create table sgj_item_tv (
  item_id          bigint(20)      not null                comment '关联主表ID',
  director         varchar(200)    default ''              comment '导演',
  actors           varchar(500)    default ''              comment '主演',
  genre            varchar(200)    default ''              comment '类型/题材',
  region           varchar(100)    default ''              comment '地区',
  language         varchar(100)    default ''              comment '语言',
  start_year       int(4)          default null            comment '开播年份',
  end_year         int(4)          default null            comment '完结年份',
  season_count     int(4)          default null            comment '季数',
  episode_count    int(4)          default null            comment '总集数',
  imdb_id          varchar(50)     default ''              comment 'IMDb编号',
  douban_id        varchar(50)     default ''              comment '豆瓣编号',
  primary key (item_id),
  constraint fk_sgj_tv_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb comment = '拾光记-电视剧扩展表';

-- ----------------------------
-- 4、书籍扩展表
-- ----------------------------
drop table if exists sgj_item_book;
create table sgj_item_book (
  item_id        bigint(20)      not null                comment '关联主表ID',
  author         varchar(200)    default ''              comment '作者',
  publisher      varchar(200)    default ''              comment '出版社',
  publish_date   date            default null            comment '出版日期',
  isbn           varchar(50)     default ''              comment 'ISBN',
  pages          int(6)          default null            comment '页数',
  genre          varchar(200)    default ''              comment '分类/题材',
  primary key (item_id),
  constraint fk_sgj_book_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb comment = '拾光记-书籍扩展表';

-- ----------------------------
-- 5、地点扩展表
-- ----------------------------
drop table if exists sgj_item_place;
create table sgj_item_place (
  item_id        bigint(20)       not null               comment '关联主表ID',
  address        varchar(300)     default ''             comment '详细地址',
  city           varchar(100)     default ''             comment '城市',
  province       varchar(100)     default ''             comment '省/州',
  country        varchar(100)     default ''             comment '国家',
  latitude       decimal(10,7)    default null           comment '纬度',
  longitude      decimal(10,7)    default null           comment '经度',
  best_season    varchar(100)     default ''             comment '最佳季节',
  place_category varchar(100)     default ''             comment '地点分类（自然/人文/美食等）',
  primary key (item_id),
  constraint fk_sgj_place_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb comment = '拾光记-地点扩展表';

-- ----------------------------
-- 6、条目照片表
-- 保存策略为整体替换（按 item_id 删后按顺序重插）；随 sgj_item 物理删除由外键级联清理
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

-- ----------------------------
-- 7、学习笔记表
-- item_id 为空表示独立笔记；不为空表示关联到某个条目
-- ----------------------------
drop table if exists sgj_note;
create table sgj_note (
  note_id      bigint(20)      not null auto_increment    comment '笔记ID',
  item_id      bigint(20)      default null               comment '关联条目ID，空为独立笔记',
  title        varchar(200)    not null                   comment '笔记标题',
  content      longtext                                   comment '笔记内容（Markdown）',
  tags         varchar(500)    default ''                 comment '标签，多个用英文逗号分隔',
  is_public    char(1)         default '0'                comment '是否公开（0私密 1公开，访客仅可见公开笔记）',
  del_flag     char(1)         default '0'                comment '删除标志（0存在 2删除）',
  create_by    varchar(64)     default ''                 comment '创建者',
  create_time  datetime                                   comment '创建时间',
  update_by    varchar(64)     default ''                 comment '更新者',
  update_time  datetime                                   comment '更新时间',
  remark       varchar(500)    default null               comment '备注',
  primary key (note_id),
  key idx_sgj_note_item (item_id),
  key idx_sgj_note_title (title),
  constraint fk_sgj_note_item foreign key (item_id) references sgj_item (item_id) on delete cascade
) engine=innodb auto_increment=1 comment = '拾光记-学习笔记表';

-- ----------------------------
-- 8、菜单初始化
-- 权限标识：sgj:item:*、sgj:note:* 和 sgj:recycle:list
-- ----------------------------
insert into sys_menu values('2000', '拾光记', '0', '4', 'sgj',             null, '', '', 1, 0, 'M', '0', '0', '',             'education', 'admin', sysdate(), '', null, '拾光记目录');
insert into sys_menu values('2001', '影视',   '2000', '1', 'media',         null, '', '', 1, 0, 'M', '0', '0', '',             'star', 'admin', sysdate(), '', null, '影视目录');
insert into sys_menu values('2002', '电影',   '2001', '1', 'movie',         'business/movie/index', '', '', 1, 0, 'C', '0', '0', 'sgj:item:list', 'list', 'admin', sysdate(), '', null, '电影菜单');
insert into sys_menu values('2003', '电视剧', '2001', '2', 'tv',            'business/tv/index',    '', '', 1, 0, 'C', '0', '0', 'sgj:item:list', 'list', 'admin', sysdate(), '', null, '电视剧菜单');
insert into sys_menu values('2004', '书籍',   '2000', '2', 'book',          'business/book/index',  '', '', 1, 0, 'C', '0', '0', 'sgj:item:list', 'education', 'admin', sysdate(), '', null, '书籍菜单');
insert into sys_menu values('2005', '地点',   '2000', '3', 'place',         'business/place/index', '', '', 1, 0, 'C', '0', '0', 'sgj:item:list', 'star', 'admin', sysdate(), '', null, '地点菜单');
insert into sys_menu values('2006', '笔记',   '2000', '4', 'note',          'business/note/index',  '', '', 1, 0, 'C', '0', '0', 'sgj:note:list', 'form', 'admin', sysdate(), '', null, '笔记菜单');
-- 按钮权限（供后续细粒度控制使用）
insert into sys_menu values('2007', '条目查询', '2002', '1', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:item:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2008', '条目新增', '2002', '2', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:item:add',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2009', '条目修改', '2002', '3', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:item:edit',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2010', '条目删除', '2002', '4', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:item:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2011', '笔记查询', '2006', '1', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:note:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2012', '笔记新增', '2006', '2', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:note:add',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2013', '笔记修改', '2006', '3', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:note:edit',    '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2014', '笔记删除', '2006', '4', '', '', '', '', 1, 0, 'F', '1', '0', 'sgj:note:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('2015', '回收站', '2000', '5', 'recycle', 'business/recycle/index', '', '', 1, 0, 'C', '0', '0', 'sgj:recycle:list', 'time', 'admin', sysdate(), '', null, '已删条目和笔记的恢复与彻底删除');
-- ============================================================
-- 拾光记业务数据字典（sgj_*）
-- 说明：核心枚举（条目类型/状态）value 使用稳定 code，
--       开放值域（分类/题材/地区/语言等）value 使用中文标签以兼容存量自由文本。
-- ============================================================

-- 字典类型
insert into sys_dict_type values(30, '条目类型',       'sgj_item_type',     '0', 'admin', sysdate(), '', null, '影视/书籍/地点条目类型');
insert into sys_dict_type values(31, '影视状态',       'sgj_movie_status',  '0', 'admin', sysdate(), '', null, '影视条目状态（想看/看过）');
insert into sys_dict_type values(32, '阅读状态',       'sgj_book_status',   '0', 'admin', sysdate(), '', null, '书籍条目状态（想读/已读）');
insert into sys_dict_type values(33, '旅行状态',       'sgj_place_status',  '0', 'admin', sysdate(), '', null, '地点条目状态（想去/去过）');
insert into sys_dict_type values(34, '地点分类',       'sgj_place_category','0', 'admin', sysdate(), '', null, '地点分类（自然/人文/美食等）');
insert into sys_dict_type values(35, '最佳季节',       'sgj_best_season',   '0', 'admin', sysdate(), '', null, '地点最佳游览季节');
insert into sys_dict_type values(36, '影视题材',       'sgj_movie_genre',   '0', 'admin', sysdate(), '', null, '电影/电视剧类型题材');
insert into sys_dict_type values(37, '书籍分类',       'sgj_book_genre',    '0', 'admin', sysdate(), '', null, '书籍分类');
insert into sys_dict_type values(38, '影视地区',       'sgj_region',        '0', 'admin', sysdate(), '', null, '影视出品地区');
insert into sys_dict_type values(39, '影视语言',       'sgj_language',      '0', 'admin', sysdate(), '', null, '影视语言');

-- 字典数据
-- 条目类型
insert into sys_dict_data values(100, 1, '电影',   'MOVIE', 'sgj_item_type',      '', 'primary', 'Y', '0', 'admin', sysdate(), '', null, '电影');
insert into sys_dict_data values(101, 2, '电视剧', 'TV',    'sgj_item_type',      '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '电视剧');
insert into sys_dict_data values(102, 3, '书籍',   'BOOK',  'sgj_item_type',      '', 'success', 'N', '0', 'admin', sysdate(), '', null, '书籍');
insert into sys_dict_data values(103, 4, '地点',   'PLACE', 'sgj_item_type',      '', 'info',    'N', '0', 'admin', sysdate(), '', null, '地点');
-- 影视状态
insert into sys_dict_data values(110, 1, '想看', 'WANT', 'sgj_movie_status', '', 'info',    'Y', '0', 'admin', sysdate(), '', null, '想看');
insert into sys_dict_data values(111, 2, '看过', 'DONE', 'sgj_movie_status', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '看过');
-- 阅读状态
insert into sys_dict_data values(120, 1, '想读', 'WANT', 'sgj_book_status', '', 'info',    'Y', '0', 'admin', sysdate(), '', null, '想读');
insert into sys_dict_data values(121, 2, '已读', 'DONE', 'sgj_book_status', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '已读');
-- 旅行状态
insert into sys_dict_data values(130, 1, '想去', 'WANT', 'sgj_place_status', '', 'info',    'Y', '0', 'admin', sysdate(), '', null, '想去');
insert into sys_dict_data values(131, 2, '去过', 'DONE', 'sgj_place_status', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '去过');
-- 地点分类（开放值域，value 用中文兼容存量）
insert into sys_dict_data values(140, 1, '自然',     '自然',     'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '自然风光');
insert into sys_dict_data values(141, 2, '人文',     '人文',     'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '人文历史');
insert into sys_dict_data values(142, 3, '美食',     '美食',     'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '美食探店');
insert into sys_dict_data values(143, 4, '城市地标', '城市地标', 'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '城市地标');
insert into sys_dict_data values(144, 5, '文化场馆', '文化场馆', 'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '博物馆/美术馆等');
insert into sys_dict_data values(145, 6, '其他',     '其他',     'sgj_place_category', '', '', 'N', '0', 'admin', sysdate(), '', null, '其他');
-- 最佳季节
insert into sys_dict_data values(150, 1, '春', '春', 'sgj_best_season', '', '', 'N', '0', 'admin', sysdate(), '', null, '春季');
insert into sys_dict_data values(151, 2, '夏', '夏', 'sgj_best_season', '', '', 'N', '0', 'admin', sysdate(), '', null, '夏季');
insert into sys_dict_data values(152, 3, '秋', '秋', 'sgj_best_season', '', '', 'N', '0', 'admin', sysdate(), '', null, '秋季');
insert into sys_dict_data values(153, 4, '冬', '冬', 'sgj_best_season', '', '', 'N', '0', 'admin', sysdate(), '', null, '冬季');
insert into sys_dict_data values(154, 5, '四季皆宜', '四季皆宜', 'sgj_best_season', '', '', 'N', '0', 'admin', sysdate(), '', null, '四季皆宜');
-- 影视题材（开放值域）
insert into sys_dict_data values(160, 1, '动作', '动作', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '动作');
insert into sys_dict_data values(161, 2, '剧情', '剧情', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '剧情');
insert into sys_dict_data values(162, 3, '喜剧', '喜剧', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '喜剧');
insert into sys_dict_data values(163, 4, '爱情', '爱情', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '爱情');
insert into sys_dict_data values(164, 5, '科幻', '科幻', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '科幻');
insert into sys_dict_data values(165, 6, '悬疑', '悬疑', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '悬疑');
insert into sys_dict_data values(166, 7, '犯罪', '犯罪', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '犯罪');
insert into sys_dict_data values(167, 8, '奇幻', '奇幻', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '奇幻');
insert into sys_dict_data values(168, 9, '动画', '动画', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '动画');
insert into sys_dict_data values(169, 10, '纪录片', '纪录片', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '纪录片');
insert into sys_dict_data values(170, 11, '战争', '战争', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '战争');
insert into sys_dict_data values(171, 12, '历史', '历史', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '历史');
insert into sys_dict_data values(172, 13, '恐怖', '恐怖', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '恐怖');
insert into sys_dict_data values(173, 14, '其他', '其他', 'sgj_movie_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '其他');
-- 书籍分类（开放值域）
insert into sys_dict_data values(180, 1, '小说', '小说', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '小说');
insert into sys_dict_data values(181, 2, '文学', '文学', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '文学');
insert into sys_dict_data values(182, 3, '历史', '历史', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '历史');
insert into sys_dict_data values(183, 4, '传记', '传记', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '传记');
insert into sys_dict_data values(184, 5, '科学技术', '科学技术', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '科学/技术');
insert into sys_dict_data values(185, 6, '经济管理', '经济管理', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '经济/管理');
insert into sys_dict_data values(186, 7, '艺术', '艺术', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '艺术');
insert into sys_dict_data values(187, 8, '教育', '教育', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '教育');
insert into sys_dict_data values(188, 9, '生活', '生活', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '生活');
insert into sys_dict_data values(189, 10, '其他', '其他', 'sgj_book_genre', '', '', 'N', '0', 'admin', sysdate(), '', null, '其他');
-- 影视地区（开放值域）
insert into sys_dict_data values(200, 1, '中国大陆', '中国大陆', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '内地');
insert into sys_dict_data values(201, 2, '港台', '港台', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '香港/台湾');
insert into sys_dict_data values(202, 3, '欧美', '欧美', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '欧美');
insert into sys_dict_data values(203, 4, '日韩', '日韩', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '日韩');
insert into sys_dict_data values(204, 5, '东南亚', '东南亚', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '东南亚');
insert into sys_dict_data values(205, 6, '其他', '其他', 'sgj_region', '', '', 'N', '0', 'admin', sysdate(), '', null, '其他');
-- 影视语言（开放值域）
insert into sys_dict_data values(210, 1, '国语', '国语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '中文普通话');
insert into sys_dict_data values(211, 2, '粤语', '粤语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '粤语');
insert into sys_dict_data values(212, 3, '英语', '英语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '英语');
insert into sys_dict_data values(213, 4, '日语', '日语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '日语');
insert into sys_dict_data values(214, 5, '韩语', '韩语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '韩语');
insert into sys_dict_data values(215, 6, '法语', '法语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '法语');
insert into sys_dict_data values(216, 7, '德语', '德语', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '德语');
insert into sys_dict_data values(217, 8, '其他', '其他', 'sgj_language', '', '', 'N', '0', 'admin', sysdate(), '', null, '其他');
