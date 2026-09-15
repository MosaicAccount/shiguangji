-- ----------------------------
-- 拾光记业务测试数据
-- 前置：按顺序执行 init_system.sql、init_business.sql、update/20260906_tag_management.sql
-- 幂等：可重复执行；只清理本脚本写入的 9001+ 号数据，不影响手工创建的记录
-- 覆盖场景：
--   1. 四类条目（MOVIE/TV/BOOK/PLACE）× 想看/看过 两种状态
--   2. 双用户（admin / shiguangji）数据隔离
--   3. 标签精确筛选（sgj_item.tags 与 sgj_tag 保持一致，逗号分隔无空格）
--   4. 地点含真实经纬度（配合地图选点功能）
--   5. 笔记：关联条目/独立笔记、公开/私密
--   6. 回收站：含软删除的条目与笔记（del_flag='2'）
--   7. 仪表盘：创建时间分布在近 5 个月，月度趋势/最近条目/待办/时间线均有数据
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 0、清理本脚本写入过的旧测试数据（笔记先行，独立笔记不随主表级联删除）
-- ----------------------------
delete from sgj_note where note_id between 9001 and 9999;
delete from sgj_item where item_id between 9001 and 9999;
delete from sgj_tag where tag_id  between 9001 and 9999;

-- ----------------------------
-- 1、条目主表（create_time 依次前移，保证月度趋势近 5 个月每月都有数据）
-- ----------------------------
insert into sgj_item (item_id, item_type, title, status, rating, `comment`, tags, cover_url, start_date, finish_date, del_flag, create_by, create_time, update_by, update_time) values
-- admin 的电影
(9001, 'MOVIE', '肖申克的救赎',   'DONE', 9.7, '希望是件好东西，也许是世上最好的东西。',                 '剧情,经典',  '', date_sub(curdate(), interval 140 day), date_sub(curdate(), interval 115 day), '0', 'admin', date_sub(now(), interval 140 day), 'admin', date_sub(now(), interval 140 day)),
(9002, 'MOVIE', '星际穿越',       'DONE', 9.6, '爱可以穿越时间与维度。',                                 '科幻,太空',  '', date_sub(curdate(), interval 80 day),  date_sub(curdate(), interval 62 day),  '0', 'admin', date_sub(now(), interval 80 day),  'admin', date_sub(now(), interval 80 day)),
(9003, 'MOVIE', '千与千寻',       'DONE', 9.4, '不要吃太胖哦，会被杀掉的。',                             '动画,奇幻',  '', date_sub(curdate(), interval 50 day),  date_sub(curdate(), interval 44 day),  '0', 'admin', date_sub(now(), interval 50 day),  'admin', date_sub(now(), interval 50 day)),
(9004, 'MOVIE', '让子弹飞',       'DONE', 9.0, '站着，把钱挣了。',                                       '喜剧,经典',  '', date_sub(curdate(), interval 30 day),  date_sub(curdate(), interval 24 day),  '0', 'admin', date_sub(now(), interval 30 day),  'admin', date_sub(now(), interval 30 day)),
(9005, 'MOVIE', '沙丘2',          'WANT', null, null,                                                    '科幻',       '', null,                                  null,                                  '0', 'admin', date_sub(now(), interval 6 day),   'admin', date_sub(now(), interval 6 day)),
(9006, 'MOVIE', '奥本海默',       'WANT', null, null,                                                    '传记,剧情',  '', null,                                  null,                                  '0', 'admin', date_sub(now(), interval 3 day),   'admin', date_sub(now(), interval 3 day)),
-- admin 的电视剧
(9007, 'TV',    '琅琊榜',         'DONE', 9.4, '麒麟才子，得之可得天下。',                               '古装,权谋',  '', date_sub(curdate(), interval 130 day), date_sub(curdate(), interval 108 day), '0', 'admin', date_sub(now(), interval 130 day), 'admin', date_sub(now(), interval 130 day)),
(9008, 'TV',    '漫长的季节',     'DONE', 9.4, '往前看，别回头。',                                       '悬疑',       '', date_sub(curdate(), interval 40 day),  date_sub(curdate(), interval 33 day),  '0', 'admin', date_sub(now(), interval 40 day),  'admin', date_sub(now(), interval 40 day)),
(9009, 'TV',    '权力的游戏',     'DONE', 9.3, '凛冬将至，只是结局差了口气。',                           '奇幻,史诗',  '', date_sub(curdate(), interval 120 day), date_sub(curdate(), interval 98 day),  '0', 'admin', date_sub(now(), interval 120 day), 'admin', date_sub(now(), interval 120 day)),
(9010, 'TV',    '老友记',         'WANT', null, null,                                                    '喜剧,经典',  '', null,                                  null,                                  '0', 'admin', date_sub(now(), interval 90 day),  'admin', date_sub(now(), interval 90 day)),
-- admin 的书籍
(9011, 'BOOK',  '三体',           'DONE', 9.8, '给岁月以文明，而不是给文明以岁月。',                     '科幻,小说',  '', date_sub(curdate(), interval 100 day), date_sub(curdate(), interval 85 day),  '0', 'admin', date_sub(now(), interval 100 day), 'admin', date_sub(now(), interval 100 day)),
(9012, 'BOOK',  '活着',           'DONE', 9.4, '人是为活着本身而活着的。',                               '文学',       '', date_sub(curdate(), interval 60 day),  date_sub(curdate(), interval 52 day),  '0', 'admin', date_sub(now(), interval 60 day),  'admin', date_sub(now(), interval 60 day)),
(9013, 'BOOK',  '人类简史',       'DONE', 9.1, '讲故事的能力塑造了人类。',                               '历史,科普',  '', date_sub(curdate(), interval 35 day),  date_sub(curdate(), interval 27 day),  '0', 'admin', date_sub(now(), interval 35 day),  'admin', date_sub(now(), interval 35 day)),
(9014, 'BOOK',  '红楼梦',         'WANT', null, null,                                                    '古典,小说',  '', null,                                  null,                                  '0', 'admin', date_sub(now(), interval 15 day),  'admin', date_sub(now(), interval 15 day)),
-- admin 的地点
(9015, 'PLACE', '故宫博物院',           'DONE', 9.6, '红墙黄瓦，一眼六百年。',                           '人文,古建筑', '', date_sub(curdate(), interval 70 day), date_sub(curdate(), interval 64 day),  '0', 'admin', date_sub(now(), interval 70 day),  'admin', date_sub(now(), interval 70 day)),
(9016, 'PLACE', '九寨沟',               'DONE', 9.5, '九寨归来不看水。',                                 '自然,湖泊',   '', date_sub(curdate(), interval 45 day), date_sub(curdate(), interval 40 day),  '0', 'admin', date_sub(now(), interval 45 day),  'admin', date_sub(now(), interval 45 day)),
(9017, 'PLACE', '西湖',                 'DONE', 9.0, '欲把西湖比西子，淡妆浓抹总相宜。',                 '风景,徒步',   '', date_sub(curdate(), interval 25 day), date_sub(curdate(), interval 19 day),  '0', 'admin', date_sub(now(), interval 25 day),  'admin', date_sub(now(), interval 25 day)),
(9018, 'PLACE', '洪崖洞',               'WANT', null, null,                                              '美食,夜景',   '', null,                                 null,                                  '0', 'admin', date_sub(now(), interval 10 day),  'admin', date_sub(now(), interval 10 day)),
(9019, 'PLACE', '张家界国家森林公园',   'WANT', null, null,                                              '自然,徒步',   '', null,                                 null,                                  '0', 'admin', date_sub(now(), interval 9 day),   'admin', date_sub(now(), interval 9 day)),
-- admin 回收站（软删除）
(9020, 'MOVIE', '上海堡垒',       'DONE', 2.9, '看完了，但仿佛什么都没看。',                             '科幻',       '', date_sub(curdate(), interval 75 day),  date_sub(curdate(), interval 72 day),  '2', 'admin', date_sub(now(), interval 75 day),  'admin', now()),
-- shiguangji 用户（验证数据隔离）
(9021, 'MOVIE', '流浪地球2',         'DONE', 8.3, '人类的勇气可以跨越所有历史。',                        '科幻',        '', date_sub(curdate(), interval 55 day), date_sub(curdate(), interval 47 day), '0', 'shiguangji', date_sub(now(), interval 55 day), 'shiguangji', date_sub(now(), interval 55 day)),
(9022, 'MOVIE', '布达佩斯大饭店',    'DONE', 8.9, '对称构图强迫症福音。',                                '剧情,喜剧',   '', date_sub(curdate(), interval 20 day), date_sub(curdate(), interval 14 day), '0', 'shiguangji', date_sub(now(), interval 20 day), 'shiguangji', date_sub(now(), interval 20 day)),
(9023, 'TV',    '狂飙',              'DONE', 8.5, '风浪越大鱼越贵。',                                    '悬疑,犯罪',   '', date_sub(curdate(), interval 18 day), date_sub(curdate(), interval 11 day), '0', 'shiguangji', date_sub(now(), interval 18 day), 'shiguangji', date_sub(now(), interval 18 day)),
(9024, 'BOOK',  '失明症漫记',        'DONE', 9.1, '能看见却不想看，才是真正的失明。',                    '文学,小说',   '', date_sub(curdate(), interval 12 day), date_sub(curdate(), interval 7 day),  '0', 'shiguangji', date_sub(now(), interval 12 day), 'shiguangji', date_sub(now(), interval 12 day)),
(9025, 'BOOK',  '深度工作',          'WANT', null, null,                                                 '效率',        '', null,                                 null,                                 '0', 'shiguangji', date_sub(now(), interval 4 day),  'shiguangji', date_sub(now(), interval 4 day)),
(9026, 'PLACE', '秦始皇兵马俑博物馆', 'DONE', 9.2, '世界第八大奇迹，值得请讲解。',                       '人文,历史',   '', date_sub(curdate(), interval 33 day), date_sub(curdate(), interval 29 day), '0', 'shiguangji', date_sub(now(), interval 33 day), 'shiguangji', date_sub(now(), interval 33 day)),
(9027, 'PLACE', '上海迪士尼度假区',  'WANT', null, null,                                                 '亲子,乐园',   '', null,                                 null,                                 '0', 'shiguangji', date_sub(now(), interval 2 day),  'shiguangji', date_sub(now(), interval 2 day));

-- ----------------------------
-- 2、电影扩展表
-- ----------------------------
insert into sgj_item_movie (item_id, director, actors, genre, region, `language`, release_year, duration_minutes, imdb_id, douban_id) values
(9001, '弗兰克·德拉邦特', '蒂姆·罗宾斯,摩根·弗里曼,鲍勃·冈顿',       '剧情', '欧美',     '英语', 1994, 142, 'tt0111161',  '1292052'),
(9002, '克里斯托弗·诺兰', '马修·麦康纳,安妮·海瑟薇,杰西卡·查斯坦',   '科幻', '欧美',     '英语', 2014, 169, 'tt0816692',  '1889243'),
(9003, '宫崎骏',          '柊瑠美,入野自由,夏木真理',                 '动画', '日韩',     '日语', 2001, 125, 'tt0245429',  '1291561'),
(9004, '姜文',            '姜文,葛优,周润发',                         '喜剧', '中国大陆', '国语', 2010, 132, 'tt1533117',  '3742360'),
(9005, '丹尼斯·维伦纽瓦', '提莫西·查拉梅,赞达亚,丽贝卡·弗格森',       '科幻', '欧美',     '英语', 2024, 166, 'tt15239678', '35297576'),
(9006, '克里斯托弗·诺兰', '基里安·墨菲,艾米莉·布朗特,小罗伯特·唐尼', '传记', '欧美',     '英语', 2023, 180, 'tt15398776', '35593344'),
(9020, '滕华涛',          '鹿晗,舒淇,石凉',                           '科幻', '中国大陆', '国语', 2019, 107, 'tt9812676',  '4828400'),
(9021, '郭帆',            '吴京,刘德华,李雪健',                       '科幻', '中国大陆', '国语', 2023, 173, 'tt13539170', '34841067'),
(9022, '韦斯·安德森',     '拉尔夫·费因斯,F·默里·亚伯拉罕',           '剧情', '欧美',     '英语', 2014, 99,  'tt2278388',  '11516473');

-- ----------------------------
-- 3、电视剧扩展表
-- ----------------------------
insert into sgj_item_tv (item_id, director, actors, genre, region, `language`, start_year, end_year, season_count, episode_count, imdb_id, douban_id) values
(9007, '孔笙,李雪',           '胡歌,刘涛,王凯',               '古装', '中国大陆', '国语', 2015, 2015, 1,  54,  '',        '25754848'),
(9008, '辛爽',                '范伟,秦昊,陈明昊',             '悬疑', '中国大陆', '国语', 2023, 2023, 1,  12,  '',        '35819500'),
(9009, '大卫·贝尼奥夫,D·B·威斯', '彼得·丁拉基,艾米莉亚·克拉克', '奇幻', '欧美',     '英语', 2011, 2019, 8,  73,  'tt0944947', '3016187'),
(9010, '凯文·布赖特',         '詹妮弗·安妮斯顿,柯特妮·考克斯', '喜剧', '欧美',     '英语', 1994, 2004, 10, 236, 'tt0108778', '9410640'),
(9023, '徐纪周',              '张译,张颂文,李一桐',           '悬疑', '中国大陆', '国语', 2023, 2023, 1,  39,  '',        '35465232');

-- ----------------------------
-- 4、书籍扩展表
-- ----------------------------
insert into sgj_item_book (item_id, author, publisher, publish_date, isbn, pages, genre) values
(9011, '刘慈欣',                '重庆出版社',   '2008-01-01', '9787536692930', 302,  '小说'),
(9012, '余华',                  '作家出版社',   '2012-08-01', '9787506365437', 191,  '文学'),
(9013, '尤瓦尔·赫拉利',         '中信出版社',   '2014-11-01', '9787508647357', 440,  '历史'),
(9014, '曹雪芹',                '人民文学出版社', '1996-12-01', '9787020002207', 1606, '小说'),
(9024, '若泽·萨拉马戈',         '新星出版社',   '2018-07-01', '9787513329544', 320,  '小说'),
(9025, '卡尔·纽波特',           '江西人民出版社', '2017-07-01', '9787210096171', 304, '科学技术');

-- ----------------------------
-- 5、地点扩展表（经纬度为真实坐标，可直接用于地图展示/选点回归）
-- ----------------------------
insert into sgj_item_place (item_id, address, city, province, country, latitude, longitude, best_season, place_category) values
(9015, '北京市东城区景山前街4号',                     '北京市', '北京市', '中国', 39.9163000,  116.3972000, '四季皆宜', '人文'),
(9016, '四川省阿坝藏族羌族自治州九寨沟县漳扎镇',       '阿坝州', '四川省', '中国', 33.2607000,  103.9180000, '秋',       '自然'),
(9017, '浙江省杭州市西湖区龙井路1号西湖风景名胜区',   '杭州市', '浙江省', '中国', 30.2430000,  120.1536000, '春',       '自然'),
(9018, '重庆市渝中区嘉陵江滨江路88号',                 '重庆市', '重庆市', '中国', 29.5628000,  106.5780000, '四季皆宜', '美食'),
(9019, '湖南省张家界市武陵源区军地坪',                 '张家界市', '湖南省', '中国', 29.3152000, 110.4362000, '秋',       '自然'),
(9026, '陕西省西安市临潼区秦陵镇',                     '西安市', '陕西省', '中国', 34.3841000,  109.2785000, '四季皆宜', '人文'),
(9027, '上海市浦东新区川沙新镇申迪北路753号',          '上海市', '上海市', '中国', 31.1432000,  121.6572000, '四季皆宜', '城市地标');

-- ----------------------------
-- 6、学习笔记（关联条目 + 独立笔记；公开 + 私密；含回收站样例）
-- ----------------------------
insert into sgj_note (note_id, item_id, title, content, tags, is_public, del_flag, create_by, create_time, update_by, update_time) values
(9001, 9011, '三体 读后笔记',
  '# 三体 读后笔记\n\n## 印象最深的设定\n- 智子锁死基础物理\n- 面壁计划与猜疑链\n\n## 摘抄\n> 给岁月以文明，而不是给文明以岁月。\n\n## 待读\n- 《三体2：黑暗森林》《三体3：死神永生》',
  '读书笔记,科幻', '1', '0', 'admin', date_sub(now(), interval 84 day), 'admin', date_sub(now(), interval 84 day)),
(9002, 9015, '故宫一日游路线',
  '# 故宫一日游路线\n\n- 上午：午门 → 太和殿 → 中和殿 → 保和殿\n- 下午：乾清宫 → 御花园 → 神武门\n- 提示：珍宝馆、钟表馆需另购票，周一闭馆\n\n## 待办\n- [ ] 下次带广角镜头拍角楼',
  '旅行,攻略', '1', '0', 'admin', date_sub(now(), interval 63 day), 'admin', date_sub(now(), interval 63 day)),
(9003, 9002, '星际穿越 物理设定笔记',
  '# 星际穿越 物理设定笔记\n\n- 米勒星球：靠近黑洞，引力时间膨胀，1小时=地球7年\n- 库珀空间站：旋转产生模拟重力\n- 五维空间：引力可以跨维度传递信息\n\n**仅个人笔记，未公开。**',
  '观影笔记,科幻', '0', '0', 'admin', date_sub(now(), interval 58 day), 'admin', date_sub(now(), interval 58 day)),
(9004, null, '2026年国庆旅行计划',
  '# 2026年国庆旅行计划\n\n1. 重庆：洪崖洞夜景、火锅\n2. 张家界：森林公园两日徒步\n3. 预算：人均 4000 元\n\n- [ ] 订机票\n- [ ] 订酒店\n- [ ] 查天气',
  '旅行,计划', '0', '0', 'admin', date_sub(now(), interval 9 day), 'admin', date_sub(now(), interval 9 day)),
(9005, null, 'Markdown 语法备忘',
  '# Markdown 语法备忘\n\n- 标题：`#` ~ `######`\n- 列表：`-` / `1.`\n- 代码：`` `code` `` 或三反引号代码块\n- 引用：`>`\n- 待办：`- [ ]`',
  '工具', '1', '0', 'shiguangji', date_sub(now(), interval 7 day), 'shiguangji', date_sub(now(), interval 7 day)),
(9006, null, '拾光记本地部署记录',
  '# 拾光记本地部署记录\n\n- MySQL：local-mysql 容器，端口 13307\n- Redis：sgj-dev-redis 容器，端口 16380\n- 启动：`java -jar shiguangji-admin/target/shiguangji-admin.jar`\n- 注意：Spring Boot 不会自动读 .env，需 shell 注入',
  '运维', '0', '0', 'admin', date_sub(now(), interval 1 day), 'admin', date_sub(now(), interval 1 day)),
(9007, 9012, '活着 读书摘抄（草稿）',
  '# 活着 读书摘抄（草稿）\n\n> 少年去游荡，中年想掘藏，老年做和尚。\n\n（还没整理完，先删进回收站）',
  '读书笔记', '0', '2', 'admin', date_sub(now(), interval 20 day), 'admin', now());

-- ----------------------------
-- 7、标签表（与条目/笔记的 tags 字段一一对应，供标签管理与精确筛选）
-- ----------------------------
insert into sgj_tag (tag_id, module, tag_name, sort, create_by, create_time) values
(9001, 'MOVIE', '剧情',   1, 'admin', now()),
(9002, 'MOVIE', '经典',   2, 'admin', now()),
(9003, 'MOVIE', '科幻',   3, 'admin', now()),
(9004, 'MOVIE', '太空',   4, 'admin', now()),
(9005, 'MOVIE', '动画',   5, 'admin', now()),
(9006, 'MOVIE', '奇幻',   6, 'admin', now()),
(9007, 'MOVIE', '喜剧',   7, 'admin', now()),
(9008, 'MOVIE', '传记',   8, 'admin', now()),
(9009, 'TV',    '古装',   1, 'admin', now()),
(9010, 'TV',    '权谋',   2, 'admin', now()),
(9011, 'TV',    '悬疑',   3, 'admin', now()),
(9012, 'TV',    '奇幻',   4, 'admin', now()),
(9013, 'TV',    '史诗',   5, 'admin', now()),
(9014, 'TV',    '喜剧',   6, 'admin', now()),
(9015, 'TV',    '经典',   7, 'admin', now()),
(9016, 'TV',    '犯罪',   8, 'admin', now()),
(9017, 'BOOK',  '科幻',   1, 'admin', now()),
(9018, 'BOOK',  '小说',   2, 'admin', now()),
(9019, 'BOOK',  '文学',   3, 'admin', now()),
(9020, 'BOOK',  '历史',   4, 'admin', now()),
(9021, 'BOOK',  '科普',   5, 'admin', now()),
(9022, 'BOOK',  '古典',   6, 'admin', now()),
(9023, 'BOOK',  '效率',   7, 'admin', now()),
(9024, 'PLACE', '人文',   1, 'admin', now()),
(9025, 'PLACE', '古建筑', 2, 'admin', now()),
(9026, 'PLACE', '自然',   3, 'admin', now()),
(9027, 'PLACE', '湖泊',   4, 'admin', now()),
(9028, 'PLACE', '风景',   5, 'admin', now()),
(9029, 'PLACE', '徒步',   6, 'admin', now()),
(9030, 'PLACE', '美食',   7, 'admin', now()),
(9031, 'PLACE', '夜景',   8, 'admin', now()),
(9032, 'PLACE', '历史',   9, 'admin', now()),
(9033, 'PLACE', '亲子',   10, 'admin', now()),
(9034, 'PLACE', '乐园',   11, 'admin', now()),
(9035, 'NOTE',  '读书笔记', 1, 'admin', now()),
(9036, 'NOTE',  '科幻',     2, 'admin', now()),
(9037, 'NOTE',  '旅行',     3, 'admin', now()),
(9038, 'NOTE',  '攻略',     4, 'admin', now()),
(9039, 'NOTE',  '观影笔记', 5, 'admin', now()),
(9040, 'NOTE',  '计划',     6, 'admin', now()),
(9041, 'NOTE',  '工具',     7, 'admin', now()),
(9042, 'NOTE',  '运维',     8, 'admin', now());
