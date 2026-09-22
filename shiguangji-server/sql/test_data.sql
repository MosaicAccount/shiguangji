-- ----------------------------
-- 拾光记测试数据（单文件，多维度覆盖）
--
-- 前置：按顺序执行 init_system.sql、quartz.sql、init_business.sql。
--       不需要 update/ 下的增量脚本（sgj_tag / sgj_item_photo / sgj_note_draft /
--       全文索引都已在 init_business.sql 里）。
--       例外：库是 2026-09-22 之前建的，得先跑 update/20260922_rating_precision.sql
--       （rating 列还是 decimal(2,1) 时，本脚本的满分样例 10.0 会撞 ERROR 1264）。
-- 幂等：可重复执行。只清理本脚本号段（id 9001-9499）与两个已删除的旧脚本遗留号段
--       （旧 test_data.sql 的 9001+、旧 test_data_bulk.sql 的 10001-10200 /
--        10501-10550 / 10601-10651），不碰手工创建的数据。
-- 非破坏性：不 drop / truncate 任何表。
--
-- 覆盖维度：
--    1. 四类条目 × 两状态（WANT / DONE）× 双用户（admin / shiguangji）
--    2. 近 6 个自然月，每月四类都有数据（仪表盘 selectItemMonthTrend 逐月逐类型非零）
--    3. 回收站软删除：四类条目各 1 条 + 笔记 1 条（del_flag='2'）
--    4. 四张扩展表字段全量填充；地点含国外坐标与南半球负经纬度
--    5. 标签：5 个模块，含停用（status='1'）与逻辑删除（del_flag='2'）；
--       并埋「剧情 / 剧情片」姊妹标签验证 FIND_IN_SET 精确匹配（搜「剧情」不得命中「剧情片」）
--    6. 笔记：关联条目 / 独立、公开 / 私密、回收站、Markdown（front-matter、H1、待办、超长正文），
--       以及「检索词只出现在正文」「只出现单字」两个全文检索样例
--    7. 条目照片：多图 + sort_order 顺序（只给已去过的地方，其余条目无照片）
--    8. 草稿箱：空白草稿（note_id=0）与编辑态草稿（note_id=已有笔记）。只写 shiguangji 用户，
--       避免覆盖 admin 的真实草稿；重复执行会清掉 shiguangji 名下的草稿再重建
--    9. 封面 / 照片三种形态：空串、/profile 站内相对路径、https 外链
--       （对应前端 photoUrl() 的「补前缀」与「外链原样」两个分支）
--   10. 边界值：评分满分 10.0 与下界 0.0、无标签、超长标题、短评含全角引号与 emoji、
--       连载中剧集（end_year 为空）、书籍缺页数与出版日期、照片文件不存在时的占位回退
--
-- 时间口径：create_time = date_sub(now(), interval N month)，N 取 5..0，每月 6-8 条。
--   这样 6 个自然月各自都有数据，且 N=5 恰好落在 selectItemMonthTrend 的
--   `create_time >= date_sub(curdate(), interval 5 month)` 边界上（含端点）。
--   同一个月内的多条共用同一时间戳：没有消费方按天聚合（趋势图按月、最近列表按月与 id），
--   所以不值得为「同月不同日」付出跨月边界的风险。
--   想看（WANT）无开始 / 完成日期，看过（DONE）的开始 = 完成 = 创建日期。
-- ----------------------------

set names utf8mb4;

-- ----------------------------
-- 0、清理本脚本与两个已删除旧脚本的号段（笔记先删：独立笔记不随主表级联）
-- ----------------------------
delete from sgj_note       where note_id  between 9001 and 9499;
delete from sgj_note       where note_id  between 10501 and 10550;  -- 旧 test_data_bulk.sql
delete from sgj_item       where item_id  between 9001 and 9499;    -- 级联清扩展表与照片
delete from sgj_item       where item_id  between 10001 and 10200;  -- 旧 test_data_bulk.sql
delete from sgj_tag        where tag_id   between 9001 and 9499;
delete from sgj_tag        where tag_id   between 10601 and 10651;  -- 旧 test_data_bulk.sql
delete from sgj_note_draft where create_by = 'shiguangji';

-- 月份桶：@m5 最早、@m0 本月
set @m5 := date_sub(now(), interval 5 month);
set @m4 := date_sub(now(), interval 4 month);
set @m3 := date_sub(now(), interval 3 month);
set @m2 := date_sub(now(), interval 2 month);
set @m1 := date_sub(now(), interval 1 month);
set @m0 := now();

-- ----------------------------
-- 1、条目主表（44 条，每月 6-8 条）
--    电影 9001-9011 / 电视剧 9012-9022 / 书籍 9023-9033 / 地点 9034-9044
--    每类 1-6 号铺满 @m5-@m0，7-11 号补第二用户、回收站与边界样例
--    每类的月份分布（保证每月每类非零）：
--      1-6 号 = @m5 @m4 @m3 @m2 @m1 @m0     7-11 号 = 见行尾月份
-- ----------------------------
insert into sgj_item (item_id, item_type, title, status, rating, `comment`, tags, cover_url, start_date, finish_date, del_flag, create_by, create_time, update_by, update_time, remark) values
-- 电影
(9001, 'MOVIE', '肖申克的救赎', 'DONE', 9.7, '希望是件好东西，也许是世上最好的东西。', '剧情,经典', '', date(@m5), date(@m5), '0', 'admin', @m5, 'admin', @m5, null),
(9002, 'MOVIE', '沙丘2', 'WANT', null, null, '科幻', '', null, null, '0', 'admin', @m4, 'admin', @m4, null),
(9003, 'MOVIE', '星际穿越', 'DONE', 9.6, '爱可以穿越时间与维度。', '科幻,太空', 'https://picsum.photos/seed/sgj-9003/480/720', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, '封面用外链，验证 photoUrl() 的外链原样分支'),
(9004, 'MOVIE', '奥本海默', 'WANT', null, '等一个安静的周末。', '传记,剧情', '', null, null, '0', 'admin', @m2, 'admin', @m2, null),
(9005, 'MOVIE', '千与千寻', 'DONE', 9.4, '不要吃太胖哦，会被杀掉的。', '动画,奇幻', '', date(@m1), date(@m1), '0', 'admin', @m1, 'admin', @m1, null),
(9006, 'MOVIE', '蜘蛛侠：纵横宇宙', 'WANT', null, null, '动画,科幻', '', null, null, '0', 'admin', @m0, 'admin', @m0, null),
(9007, 'MOVIE', '让子弹飞', 'DONE', 9.0, '站着，把钱挣了。', '喜剧,剧情片', '', date(@m5), date(@m5), '0', 'admin', @m5, 'admin', @m5, '和 9001 一起验证 FIND_IN_SET：搜「剧情」不得命中本条'),
(9008, 'MOVIE', '上海堡垒', 'DONE', 2.9, '看完了，但仿佛什么都没看。', '科幻', '', date(@m3), date(@m3), '2', 'admin', @m3, 'admin', now(), '回收站样例'),
(9009, 'MOVIE', '流浪地球2', 'DONE', 8.3, '人类的勇气可以跨越所有历史。', '科幻', '', date(@m1), date(@m1), '0', 'shiguangji', @m1, 'shiguangji', @m1, null),
(9010, 'MOVIE', '布达佩斯大饭店', 'WANT', null, null, '剧情,喜剧', '', null, null, '0', 'shiguangji', @m0, 'shiguangji', @m0, null),
(9011, 'MOVIE', '标题很长的电影：用来验证列表页标题截断与详情页换行的高度自适应该怎么写才不塌', 'DONE', 10.0, '他说："这才是电影。" 🎬 满分。', '剧情', '', date(@m4), date(@m4), '0', 'admin', @m4, 'admin', @m4, '边界样例：满分 10.0（rating 是 decimal(3,1)，业务上限 10）、超长标题、短评含全角引号与 emoji、无 IMDb / 豆瓣编号'),
-- 电视剧
(9012, 'TV', '琅琊榜', 'DONE', 9.4, '麒麟才子，得之可得天下。', '古装,权谋', '', date(@m5), date(@m5), '0', 'admin', @m5, 'admin', @m5, null),
(9013, 'TV', '老友记', 'WANT', null, null, '喜剧,经典', '', null, null, '0', 'admin', @m4, 'admin', @m4, null),
(9014, 'TV', '漫长的季节', 'DONE', 9.4, '往前看，别回头。', '悬疑', '', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, null),
(9015, 'TV', '权力的游戏', 'WANT', null, '凛冬将至，只是结局差了口气。', '奇幻,史诗', '', null, null, '0', 'admin', @m2, 'admin', @m2, null),
(9016, 'TV', '狂飙', 'DONE', 8.5, '风浪越大鱼越贵。', '悬疑,犯罪', '', date(@m1), date(@m1), '0', 'admin', @m1, 'admin', @m1, null),
(9017, 'TV', '三体', 'WANT', null, null, '科幻', '', null, null, '0', 'admin', @m0, 'admin', @m0, null),
(9018, 'TV', '我的天才女友', 'DONE', 9.3, '友谊里也有刀锋。', '剧情', '', date(@m4), date(@m4), '0', 'admin', @m4, 'admin', @m4, '语言取字典的「其他」值，覆盖非主流语言'),
(9019, 'TV', '东八区的先生们', 'DONE', 2.1, '看完了，并且有点后悔。', '剧情', '', date(@m2), date(@m2), '2', 'admin', @m2, 'admin', now(), '回收站样例'),
(9020, 'TV', '请回答1988', 'DONE', 9.7, '双门洞的冬天，谁不想回去。', '剧情,治愈', '', date(@m0), date(@m0), '0', 'shiguangji', @m0, 'shiguangji', @m0, null),
(9021, 'TV', '黑镜', 'WANT', null, null, '科幻', '', null, null, '0', 'shiguangji', @m5, 'shiguangji', @m5, null),
(9022, 'TV', '名侦探柯南', 'DONE', 9.4, '连载到现在的老朋友。', '动画', '', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, '边界样例：连载中，end_year 为空、集数很大'),
-- 书籍
(9023, 'BOOK', '三体', 'DONE', 9.8, '给岁月以文明，而不是给文明以岁月。', '科幻,小说', '/profile/testdata/book-9023.jpg', date(@m5), date(@m5), '0', 'admin', @m5, 'admin', @m5, '封面用站内相对路径，验证 photoUrl() 的补前缀分支（文件不存在，前端回退占位）'),
(9024, 'BOOK', '红楼梦', 'WANT', null, null, '古典,小说', '', null, null, '0', 'admin', @m4, 'admin', @m4, null),
(9025, 'BOOK', '活着', 'DONE', 9.4, '人是为活着本身而活着的。', '文学', '', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, null),
(9026, 'BOOK', '人类简史', 'WANT', null, '讲故事的能力塑造了人类。', '历史,科普', '', null, null, '0', 'admin', @m2, 'admin', @m2, null),
(9027, 'BOOK', '深度工作', 'DONE', 8.0, '注意力是稀缺资源。', '效率', '', date(@m1), date(@m1), '0', 'admin', @m1, 'admin', @m1, null),
(9028, 'BOOK', '失明症漫记', 'WANT', null, null, '文学,小说', '', null, null, '0', 'admin', @m0, 'admin', @m0, null),
(9029, 'BOOK', '枪炮、病菌与钢铁', 'DONE', 9.0, '地理决定论的一次完整论证。', '历史,科普', '', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, null),
(9030, 'BOOK', '小王子', 'DONE', 9.1, '重要的东西用眼睛是看不见的。', '文学,经典', '', date(@m1), date(@m1), '2', 'admin', @m1, 'admin', now(), '回收站样例'),
(9031, 'BOOK', '百年孤独', 'DONE', 9.3, '家族的第一代人被捆在树上。', '文学,小说', '', date(@m5), date(@m5), '0', 'shiguangji', @m5, 'shiguangji', @m5, null),
(9032, 'BOOK', '置身事内', 'WANT', null, null, '经济管理', '', null, null, '0', 'shiguangji', @m4, 'shiguangji', @m4, null),
(9033, 'BOOK', '瓦尔登湖', 'DONE', 8.4, '一个人越能放下，就越富有。', '', '', date(@m2), date(@m2), '0', 'admin', @m2, 'admin', @m2, '边界样例：tags 为空串（无标签条目），扩展表 pages / publish_date / isbn 缺失'),
-- 地点
(9034, 'PLACE', '故宫博物院', 'DONE', 9.6, '红墙黄瓦，一眼六百年。', '人文,古建筑', '', date(@m5), date(@m5), '0', 'admin', @m5, 'admin', @m5, null),
(9035, 'PLACE', '张家界国家森林公园', 'WANT', null, '想去徒步，攒假期中。', '自然,徒步', '', null, null, '0', 'admin', @m4, 'admin', @m4, null),
(9036, 'PLACE', '九寨沟', 'DONE', 9.5, '九寨归来不看水。', '自然,湖泊', '', date(@m3), date(@m3), '0', 'admin', @m3, 'admin', @m3, null),
(9037, 'PLACE', '洪崖洞', 'WANT', null, null, '美食,夜景', '', null, null, '0', 'admin', @m2, 'admin', @m2, null),
(9038, 'PLACE', '西湖', 'DONE', 9.0, '欲把西湖比西子，淡妆浓抹总相宜。', '风景,徒步', '', date(@m1), date(@m1), '0', 'admin', @m1, 'admin', @m1, null),
(9039, 'PLACE', '秦始皇兵马俑博物馆', 'WANT', null, null, '人文,历史', '', null, null, '0', 'admin', @m0, 'admin', @m0, null),
(9040, 'PLACE', '上海迪士尼度假区', 'DONE', 8.6, '一天走了两万步。', '亲子,乐园', '', date(@m2), date(@m2), '0', 'admin', @m2, 'admin', @m2, null),
(9041, 'PLACE', '茶卡盐湖', 'DONE', 0.0, '人比盐多。', '自然', '', date(@m0), date(@m0), '2', 'admin', @m0, 'admin', now(), '回收站样例 + 评分取下界 0.0'),
(9042, 'PLACE', '布达拉宫', 'DONE', 9.5, '从山脚抬头的那一刻就值了。', '人文,历史', '', date(@m4), date(@m4), '0', 'shiguangji', @m4, 'shiguangji', @m4, null),
(9043, 'PLACE', '富士山', 'WANT', null, null, '自然,风景', '', null, null, '0', 'shiguangji', @m3, 'shiguangji', @m3, '国外地点，验证国家/州字段与跨国坐标'),
(9044, 'PLACE', '悉尼歌剧院', 'DONE', 9.1, '坐在台阶上看了一下午海。', '人文,城市地标', '', date(@m0), date(@m0), '0', 'admin', @m0, 'admin', @m0, '边界样例：南半球负纬度、国外地点');

-- ----------------------------
-- 2、电影扩展表（11 条）
-- ----------------------------
insert into sgj_item_movie (item_id, director, actors, genre, region, `language`, release_year, duration_minutes, imdb_id, douban_id) values
(9001, '弗兰克·德拉邦特',   '蒂姆·罗宾斯,摩根·弗里曼,鲍勃·冈顿',       '剧情', '欧美',     '英语', 1994, 142, 'tt0111161',  '1292052'),
(9002, '丹尼斯·维伦纽瓦',   '提莫西·查拉梅,赞达亚,丽贝卡·弗格森',       '科幻', '欧美',     '英语', 2024, 166, 'tt15239678', '35297576'),
(9003, '克里斯托弗·诺兰',   '马修·麦康纳,安妮·海瑟薇,杰西卡·查斯坦',   '科幻', '欧美',     '英语', 2014, 169, 'tt0816692',  '1889243'),
(9004, '克里斯托弗·诺兰',   '基里安·墨菲,艾米莉·布朗特,小罗伯特·唐尼', '传记', '欧美',     '英语', 2023, 180, 'tt15398776', '35593344'),
(9005, '宫崎骏',            '柊瑠美,入野自由,夏木真理',                 '动画', '日韩',     '日语', 2001, 125, 'tt0245429',  '1291561'),
(9006, '华金·多斯·桑托斯',  '沙梅克·摩尔,海莉·斯坦菲尔德',             '动画', '欧美',     '英语', 2023, 140, 'tt9362722',  '35801369'),
(9007, '姜文',              '姜文,葛优,周润发',                         '喜剧', '中国大陆', '国语', 2010, 132, 'tt1533117',  '3742360'),
(9008, '滕华涛',            '鹿晗,舒淇,石凉',                           '科幻', '中国大陆', '国语', 2019, 107, 'tt9812676',  '4828400'),
(9009, '郭帆',              '吴京,刘德华,李雪健',                       '科幻', '中国大陆', '国语', 2023, 173, 'tt13539170', '34841067'),
(9010, '韦斯·安德森',       '拉尔夫·费因斯,F·默里·亚伯拉罕',           '剧情', '欧美',     '英语', 2014, 99,  'tt2278388',  '11516473'),
(9011, '测试导演',          '甲,乙,丙',                                 '剧情', '中国大陆', '国语', 2026, 999, '',           '');

-- ----------------------------
-- 3、电视剧扩展表（11 条）
-- ----------------------------
insert into sgj_item_tv (item_id, director, actors, genre, region, `language`, start_year, end_year, season_count, episode_count, imdb_id, douban_id) values
(9012, '孔笙,李雪',              '胡歌,刘涛,王凯',                      '古装', '中国大陆', '国语',   2015, 2015, 1,  54,   '',          '25754848'),
(9013, '凯文·布赖特',            '詹妮弗·安妮斯顿,柯特妮·考克斯',      '喜剧', '欧美',     '英语',   1994, 2004, 10, 236,  'tt0108778', '9410640'),
(9014, '辛爽',                   '范伟,秦昊,陈明昊',                    '悬疑', '中国大陆', '国语',   2023, 2023, 1,  12,   '',          '35819500'),
(9015, '大卫·贝尼奥夫,D·B·威斯', '彼得·丁拉基,艾米莉亚·克拉克',        '奇幻', '欧美',     '英语',   2011, 2019, 8,  73,   'tt0944947', '3016187'),
(9016, '徐纪周',                 '张译,张颂文,李一桐',                  '悬疑', '中国大陆', '国语',   2023, 2023, 1,  39,   '',          '35465232'),
(9017, '杨磊',                   '张鲁一,于和伟,陈瑾',                  '科幻', '中国大陆', '国语',   2023, 2023, 1,  30,   '',          '36113599'),
(9018, '萨维里奥·科斯坦佐',      '伊莱莎·德尔·杰尼奥,卢多维卡·纳斯',  '剧情', '欧美',     '其他',   2018, 2024, 4,  34,   'tt7278862', '27137089'),
(9019, '李骏',                   '张翰,王晓晨,杜淳',                    '剧情', '中国大陆', '国语',   2022, 2022, 1,  40,   '',          '35594115'),
(9020, '申源浩',                 '李惠利,朴宝剑,柳俊烈',                '剧情', '日韩',     '韩语',   2015, 2016, 1,  20,   'tt5182866', '26302614'),
(9021, '查理·布鲁克',            '安娜贝尔·琼斯,布莱丝·达拉斯·霍华德', '科幻', '欧美',     '英语',   2011, 2023, 6,  27,   'tt2085059', '10442505'),
(9022, '青山刚昌',               '高山南,山崎和佳奈',                   '动画', '日韩',     '日语',   1996, null, 1,  1170, 'tt0131149', '1465999');

-- ----------------------------
-- 4、书籍扩展表（11 条）
-- ----------------------------
insert into sgj_item_book (item_id, author, publisher, publish_date, isbn, pages, genre) values
(9023, '刘慈欣',                 '重庆出版社',     '2008-01-01', '9787536692930', 302,  '小说'),
(9024, '曹雪芹',                 '人民文学出版社', '1996-12-01', '9787020002207', 1606, '小说'),
(9025, '余华',                   '作家出版社',     '2012-08-01', '9787506365437', 191,  '文学'),
(9026, '尤瓦尔·赫拉利',          '中信出版社',     '2014-11-01', '9787508647357', 440,  '历史'),
(9027, '卡尔·纽波特',            '江西人民出版社', '2017-07-01', '9787210096171', 304,  '科学技术'),
(9028, '若泽·萨拉马戈',          '新星出版社',     '2018-07-01', '9787513329544', 320,  '小说'),
(9029, '贾雷德·戴蒙德',          '中信出版社',     '2022-03-01', '9787521738642', 592,  '历史'),
(9030, '安托万·德·圣埃克苏佩里', '人民文学出版社', '2018-04-01', '9787020138326', 112,  '文学'),
(9031, '加西亚·马尔克斯',        '南海出版公司',   '2017-08-01', '9787544291170', 360,  '小说'),
(9032, '兰小欢',                 '上海人民出版社', '2021-08-01', '9787208171336', 336,  '经济管理'),
(9033, '亨利·戴维·梭罗',         '译林出版社',     null,         '',              null, '');

-- ----------------------------
-- 5、地点扩展表（11 条，经纬度为真实坐标，可直接用于地图展示与选点回归）
-- ----------------------------
insert into sgj_item_place (item_id, address, city, province, country, latitude, longitude, best_season, place_category) values
(9034, '北京市东城区景山前街4号',                  '北京市',   '北京市',       '中国',       39.9163000,  116.3972000, '四季皆宜', '人文'),
(9035, '湖南省张家界市武陵源区军地坪',              '张家界市', '湖南省',       '中国',       29.3152000,  110.4362000, '秋',       '自然'),
(9036, '四川省阿坝藏族羌族自治州九寨沟县漳扎镇',    '阿坝州',   '四川省',       '中国',       33.2607000,  103.9180000, '秋',       '自然'),
(9037, '重庆市渝中区嘉陵江滨江路88号',              '重庆市',   '重庆市',       '中国',       29.5628000,  106.5780000, '四季皆宜', '美食'),
(9038, '浙江省杭州市西湖区龙井路1号',               '杭州市',   '浙江省',       '中国',       30.2430000,  120.1536000, '春',       '自然'),
(9039, '陕西省西安市临潼区秦陵镇',                  '西安市',   '陕西省',       '中国',       34.3841000,  109.2785000, '四季皆宜', '人文'),
(9040, '上海市浦东新区川沙新镇申迪北路753号',       '上海市',   '上海市',       '中国',       31.1432000,  121.6572000, '四季皆宜', '城市地标'),
(9041, '青海省海西蒙古族藏族自治州乌兰县茶卡镇',    '海西州',   '青海省',       '中国',       36.7017000,  99.0864000,  '夏',       '自然'),
(9042, '西藏自治区拉萨市城关区北京中路35号',        '拉萨市',   '西藏自治区',   '中国',       29.6558000,  91.1170000,  '夏',       '人文'),
(9043, '日本静冈县与山梨县交界',                    '静冈县',   '静冈县',       '日本',       35.3606000,  138.7274000, '夏',       '自然'),
(9044, '澳大利亚新南威尔士州悉尼贝内朗角',          '悉尼',     '新南威尔士州', '澳大利亚',  -33.8568000,  151.2153000, '春',       '人文');

-- ----------------------------
-- 6、条目照片（6 条：多图 + 顺序 + 站内相对路径与外链各一）
--    只给「去过」的地点挂照片，其余条目保持无照片（验证相册入口的隐藏与空态）
--    文件本身不存在，前端 el-image 会走 error 槽回退成类型占位字
-- ----------------------------
insert into sgj_item_photo (item_id, url, sort_order, create_by, create_time) values
(9034, '/profile/testdata/place-9034-1.jpg',          0, 'admin',      @m5),
(9034, '/profile/testdata/place-9034-2.jpg',          1, 'admin',      @m5),
(9034, 'https://picsum.photos/seed/sgj-9034/800/600', 2, 'admin',      @m5),
(9036, '/profile/testdata/place-9036-1.jpg',          0, 'admin',      @m3),
(9036, '/profile/testdata/place-9036-2.jpg',          1, 'admin',      @m3),
(9042, '/profile/testdata/place-9042-1.jpg',          0, 'shiguangji', @m4);

-- ----------------------------
-- 7、笔记（13 条：关联 / 独立、公开 / 私密、回收站、front-matter、超长正文、全文检索样例）
--    每条的 create_time 不早于所关联条目的完成时间
-- ----------------------------
insert into sgj_note (note_id, item_id, title, content, tags, is_public, del_flag, create_by, create_time, update_by, update_time, remark) values
(9001, 9023, '三体 读后笔记',
  '# 三体 读后笔记\n\n## 印象最深的设定\n- 智子锁死基础物理\n- 面壁计划与猜疑链\n- 黑暗森林法则\n\n## 摘抄\n> 给岁月以文明，而不是给文明以岁月。\n\n## 待读\n- [ ] 《三体2：黑暗森林》\n- [ ] 《三体3：死神永生》',
  '读书笔记,科幻', '1', '0', 'admin', @m2, 'admin', @m2, '标题与正文都含「三体」，全文检索任意命中都应有结果'),
(9002, 9034, '故宫一日游路线',
  '# 故宫一日游路线\n\n## 上午\n午门 → 太和殿 → 中和殿 → 保和殿\n\n## 下午\n乾清宫 → 御花园 → 神武门\n\n## 提示\n- 珍宝馆、钟表馆需另购票\n- 周一闭馆（法定节假日除外）\n\n## 待办\n- [ ] 下次带广角镜头拍角楼',
  '旅行,攻略', '1', '0', 'admin', @m3, 'admin', @m3, null),
(9003, 9003, '星际穿越 物理设定笔记',
  '# 星际穿越 物理设定笔记\n\n- 米勒星球靠近黑洞，引力时间膨胀，1 小时约等于地球 7 年\n- 库珀空间站靠旋转产生模拟重力\n- 五维空间里引力可以跨维度传递信息\n\n**仅个人笔记，未公开。**',
  '观影笔记', '0', '0', 'admin', @m3, 'admin', @m3, null),
(9004, null, '国庆旅行计划',
  '# 国庆旅行计划\n\n1. 重庆：洪崖洞夜景、火锅\n2. 张家界：森林公园两日徒步\n3. 预算：人均 4000 元\n\n- [ ] 订机票\n- [ ] 订酒店\n- [ ] 查天气',
  '计划', '0', '0', 'admin', @m0, 'admin', @m0, '独立笔记（item_id 为空）'),
(9005, null, '拾光记本地部署手记',
  '---\ntitle: 拾光记本地部署手记\ntags: 运维\ndate: 2026-08-01\n---\n\n# 拾光记本地部署手记\n\n## 环境\n\n- MySQL：local-mysql 容器，13306\n- Redis：local-redis 容器，16379\n- MinIO：local-minio 容器，29000\n\n## 启动\n\n1. 建库：按顺序执行 `init_system.sql`、`quartz.sql`、`init_business.sql`\n2. 灌数据：执行 `test_data.sql`\n3. 起后端：`java -jar shiguangji-admin/target/shiguangji-admin.jar`\n4. 起前端：`npm run dev`\n\n## 注意\n\n- 环境变量必须在 shell 里注入，Spring Boot 不读 `.env`\n- 上传目录由配置决定，删库不会删文件',
  '运维', '1', '0', 'admin', @m1, 'admin', @m1, 'front-matter 样例：摘要片段应跳过 front-matter 取正文'),
(9006, null, '读书笔记长文整理',
  '# 读书笔记长文整理\n\n## 一、为什么要重排一遍\n\n读过的东西如果只留在脑子里，过一阵就只剩一个模糊的印象。把半年来读过的书按主题重排一遍，能看出自己反复被什么问题吸引，也能看出哪些书其实只翻了几页就被放下了。这个过程本身比结论有用。\n\n## 二、按主题分组\n\n1. 科幻与未来：《三体》三部曲、《流浪地球》短篇集。共同的主题是文明在极端压力下的选择。\n2. 历史与地理：《人类简史》《枪炮、病菌与钢铁》。前者讲叙事如何塑造共同体，后者讲地理条件如何约束路径依赖。两本放一起读，比单读一本有意思得多。\n3. 工作方法：《深度工作》《刻意练习》。都在说同一件事：注意力是有限资源，怎么分配比怎么努力重要。\n4. 文学：《活着》《百年孤独》《失明症漫记》。三本都在写苦难，但写法的差别很大：《活着》用克制的白描，《百年孤独》用魔幻的循环，《失明症漫记》用寓言式的极端设定。\n\n## 三、下一步\n\n- [ ] 给每本书补一段两百字的短评\n- [ ] 把重复出现的主题词整理成标签\n- [ ] 挑一本重读，验证「读第二遍是否还有新东西」\n\n## 四、随手记\n\n长文还有一个作用：把「读完了」和「读懂了」区分开。写得出来才算读懂了一半，写得清楚才算读懂了大半。剩下的一半，大概只能靠时间和实践慢慢补。\n\n## 五、半年书单（按读完时间）\n\n- 上半年：《活着》《人类简史》《深度工作》\n- 下半年：《三体》《百年孤独》《失明症漫记》《枪炮、病菌与钢铁》\n- 没读完：《红楼梦》，卡在第五十回，今年继续\n\n## 六、最后\n\n这份整理本身也是给未来的自己留的一封信：如果哪天忘了当初为什么读这些书，翻回来看看这一页就够了。',
  '读书笔记', '1', '0', 'admin', @m0, 'admin', @m0, '超长正文（> 600 字）：验证摘要片段截断与省略号'),
(9007, 9025, '活着 读书摘抄',
  '# 活着 读书摘抄\n\n> 少年去游荡，中年想掘藏，老年做和尚。\n\n（还没整理完就删掉了，在回收站里）',
  '读书笔记', '0', '2', 'admin', @m1, 'admin', now(), '回收站样例：软删除的笔记'),
(9008, 9042, '布达拉宫游记',
  '# 布达拉宫游记\n\n## 路线\n从广场东侧排队进，沿白宫、红宫顺序往上走，全程约两小时。\n\n## 提示\n- 海拔三千七，上去慢一点，别急着拍照\n- 殿内不允许拍照，包要寄存\n- 下午的光最好，从药王山看过去是经典机位\n\n## 一句话\n- 值得为它专门飞一趟。',
  '旅行', '1', '0', 'shiguangji', @m4, 'shiguangji', @m4, null),
(9009, null, '本周待办',
  '# 本周待办\n\n- [ ] 把 9001-9010 号测试条目标签补齐\n- [ ] 给地点补照片\n- [ ] 跑一遍接口冒烟测试',
  '计划', '0', '0', 'shiguangji', @m1, 'shiguangji', @m1, '有一条编辑态草稿指向它（sgj_note_draft.note_id = 9009）'),
(9010, null, '年度回顾与整理',
  '# 年度回顾与整理\n\n今年把「深空」相关的纪录片、播客、专栏都归到一个主题里看了下来。\n\n## 深空 相关的记录\n\n- 纪录片：深空探测的几次关键窗口期\n- 播客：聊深空预算与工程取舍的那一期\n- 专栏：深空探测器通信延迟对工程决策的影响\n\n结论：深空探索的难点往往不在推进器，而在通信、供电与决策延迟。',
  '工具', '1', '0', 'shiguangji', @m0, 'shiguangji', @m0, '检索词「深空」只在正文、不在标题：验证 MATCH(title, content) 覆盖正文'),
(9011, 9044, '悉尼歌剧院参观笔记',
  '# 悉尼歌剧院参观笔记\n\n- 外观比照片更白，正午反光很强，拍照建议早晚\n- 内部导览约一小时，讲贝壳结构的建造过程\n- 从环形码头步行过去只要十分钟\n\n> 关联条目是国外地点，验证笔记与地点的关联展示。',
  '旅行,攻略', '1', '0', 'admin', @m0, 'admin', @m0, null),
(9012, null, '热力学与信息熵笔记',
  '# 热力学与信息熵笔记\n\n- 熵是系统无序度的度量\n- 熵增是不可逆过程的标志\n- 麦克斯韦妖与信息熵的关系，本质是「删除信息也要付出代价」\n\n## 一个坑\n\n搜「熵」搜不到，搜「熵增」才行。',
  '工具', '1', '0', 'admin', @m2, 'admin', @m2, 'ngram 按 2 字切词：搜单字「熵」无结果、搜「熵增」命中，用于验证搜索框的最小长度提示'),
(9013, 9001, '肖申克的救赎 观后感',
  '# 肖申克的救赎 观后感\n\n## 三处细节\n1. 屋顶上那场啤酒戏，是把「人的尊严」还给他\n2. 歌剧唱片在广播里响起时，所有人停下手里的活\n3. 越狱当晚的雷雨，是整部片子唯一一次情绪外露\n\n## 一句话\n- 希望是好东西，而且不需要理由。',
  '观影笔记', '1', '0', 'admin', @m5, 'admin', @m5, null);

-- ----------------------------
-- 8、笔记草稿（3 条，只写 shiguangji，避免覆盖 admin 的真实草稿）
--    身份是 (create_by, note_id)：note_id = 0 即空白草稿（每人一份），不为 0 即某篇笔记的编辑态
-- ----------------------------
insert into sgj_note_draft (note_id, item_id, title, content, tags, is_public, create_by, create_time, update_time) values
(0,    9043, '', '（空白草稿：还没有内容，但已经选好了要挂的条目）', '', '0', 'shiguangji', @m1, @m0),
(9009, 0,    '本周待办（未保存改动）', '# 本周待办\n\n- [ ] 把测试条目标签补齐\n- [ ] 给地点补照片\n- [x] 跑一遍接口冒烟测试\n- [ ] 顺手把这行加到草稿里试试', '计划', '0', 'shiguangji', @m1, @m0),
(9008, 9042, '布达拉宫游记（草稿）', '# 布达拉宫游记（草稿）\n\n还在写：\n\n- 从药王山看过去的机位\n- 殿内不能拍照，只能记', '旅行', '1', 'shiguangji', @m4, @m2);

-- ----------------------------
-- 9、标签表（50 条：5 个模块）
--    tag_name 与条目 / 笔记 `tags` 字段里的 token 一一对应（逗号分隔、无空格），
--    这样标签管理与「按标签精确筛选」两侧都能命中
-- ----------------------------
insert into sgj_tag (tag_id, module, tag_name, sort, status, del_flag, create_by, create_time, update_by, update_time) values
-- 电影
(9001, 'MOVIE', '剧情',     1, '0', '0', 'admin', @m5, 'admin', @m5),
(9002, 'MOVIE', '经典',     2, '0', '0', 'admin', @m5, 'admin', @m5),
(9003, 'MOVIE', '科幻',     3, '0', '0', 'admin', @m5, 'admin', @m5),
(9004, 'MOVIE', '太空',     4, '0', '0', 'admin', @m5, 'admin', @m5),
(9005, 'MOVIE', '传记',     5, '0', '0', 'admin', @m5, 'admin', @m5),
(9006, 'MOVIE', '动画',     6, '0', '0', 'admin', @m5, 'admin', @m5),
(9007, 'MOVIE', '奇幻',     7, '0', '0', 'admin', @m5, 'admin', @m5),
(9008, 'MOVIE', '喜剧',     8, '0', '0', 'admin', @m5, 'admin', @m5),
(9009, 'MOVIE', '剧情片',   9, '0', '0', 'admin', @m5, 'admin', @m5),  -- 与 9001「剧情」构成姊妹标签，验证 FIND_IN_SET 精确匹配
-- 电视剧
(9010, 'TV', '古装',   1,  '0', '0', 'admin', @m5, 'admin', @m5),
(9011, 'TV', '权谋',   2,  '0', '0', 'admin', @m5, 'admin', @m5),
(9012, 'TV', '喜剧',   3,  '0', '0', 'admin', @m5, 'admin', @m5),
(9013, 'TV', '经典',   4,  '0', '0', 'admin', @m5, 'admin', @m5),
(9014, 'TV', '悬疑',   5,  '0', '0', 'admin', @m5, 'admin', @m5),
(9015, 'TV', '奇幻',   6,  '0', '0', 'admin', @m5, 'admin', @m5),
(9016, 'TV', '史诗',   7,  '0', '0', 'admin', @m5, 'admin', @m5),
(9017, 'TV', '犯罪',   8,  '1', '0', 'admin', @m5, 'admin', @m5),      -- 停用：前台 /app/tag/list 不应返回
(9018, 'TV', '科幻',   9,  '0', '0', 'admin', @m5, 'admin', @m5),
(9019, 'TV', '剧情',   10, '0', '0', 'admin', @m5, 'admin', @m5),
(9020, 'TV', '治愈',   11, '0', '0', 'admin', @m5, 'admin', @m5),
(9021, 'TV', '动画',   12, '0', '0', 'admin', @m5, 'admin', @m5),
-- 书籍
(9022, 'BOOK', '科幻',     1, '0', '0', 'admin', @m5, 'admin', @m5),
(9023, 'BOOK', '小说',     2, '0', '0', 'admin', @m5, 'admin', @m5),
(9024, 'BOOK', '古典',     3, '0', '2', 'admin', @m5, 'admin', @m5),   -- 逻辑删除：标签管理不显示，但条目 9024 的 tags 仍挂着它
(9025, 'BOOK', '文学',     4, '0', '0', 'admin', @m5, 'admin', @m5),
(9026, 'BOOK', '历史',     5, '0', '0', 'admin', @m5, 'admin', @m5),
(9027, 'BOOK', '科普',     6, '0', '0', 'admin', @m5, 'admin', @m5),
(9028, 'BOOK', '效率',     7, '0', '0', 'admin', @m5, 'admin', @m5),
(9029, 'BOOK', '经典',     8, '0', '0', 'admin', @m5, 'admin', @m5),
(9030, 'BOOK', '经济管理', 9, '0', '0', 'admin', @m5, 'admin', @m5),
-- 地点
(9031, 'PLACE', '人文',     1,  '0', '0', 'admin', @m5, 'admin', @m5),
(9032, 'PLACE', '古建筑',   2,  '0', '0', 'admin', @m5, 'admin', @m5),
(9033, 'PLACE', '自然',     3,  '0', '0', 'admin', @m5, 'admin', @m5),
(9034, 'PLACE', '徒步',     4,  '0', '0', 'admin', @m5, 'admin', @m5),
(9035, 'PLACE', '湖泊',     5,  '0', '0', 'admin', @m5, 'admin', @m5),
(9036, 'PLACE', '美食',     6,  '0', '0', 'admin', @m5, 'admin', @m5),
(9037, 'PLACE', '夜景',     7,  '0', '0', 'admin', @m5, 'admin', @m5),
(9038, 'PLACE', '风景',     8,  '0', '0', 'admin', @m5, 'admin', @m5),
(9039, 'PLACE', '历史',     9,  '0', '0', 'admin', @m5, 'admin', @m5),
(9040, 'PLACE', '亲子',     10, '0', '0', 'admin', @m5, 'admin', @m5),
(9041, 'PLACE', '乐园',     11, '0', '0', 'admin', @m5, 'admin', @m5),
(9042, 'PLACE', '城市地标', 12, '0', '0', 'admin', @m5, 'admin', @m5),
-- 笔记
(9043, 'NOTE', '读书笔记', 1, '0', '0', 'admin', @m5, 'admin', @m5),
(9044, 'NOTE', '科幻',     2, '0', '0', 'admin', @m5, 'admin', @m5),
(9045, 'NOTE', '旅行',     3, '0', '0', 'admin', @m5, 'admin', @m5),
(9046, 'NOTE', '攻略',     4, '0', '0', 'admin', @m5, 'admin', @m5),
(9047, 'NOTE', '观影笔记', 5, '0', '0', 'admin', @m5, 'admin', @m5),
(9048, 'NOTE', '计划',     6, '0', '0', 'admin', @m5, 'admin', @m5),
(9049, 'NOTE', '工具',     7, '0', '0', 'admin', @m5, 'admin', @m5),
(9050, 'NOTE', '运维',     8, '1', '0', 'admin', @m5, 'admin', @m5);  -- 停用

-- ----------------------------
-- 10、自检：导入完顺手核对不变量
--     四个「异常数」应为 0；月份矩阵每个格子应 ≥ 1（仪表盘按月趋势要求）
--     非 0 就说明这份数据被手工改过，或库里本来就有同号段的脏数据
-- ----------------------------
select '缺扩展行的条目' as 检查项,
       (select count(*) from sgj_item i
         where i.item_id between 9001 and 9044
           and ((i.item_type = 'MOVIE' and not exists (select 1 from sgj_item_movie x where x.item_id = i.item_id))
             or (i.item_type = 'TV'    and not exists (select 1 from sgj_item_tv    x where x.item_id = i.item_id))
             or (i.item_type = 'BOOK'  and not exists (select 1 from sgj_item_book  x where x.item_id = i.item_id))
             or (i.item_type = 'PLACE' and not exists (select 1 from sgj_item_place x where x.item_id = i.item_id)))) as 异常数
union all
select 'tags 含空格',
       (select count(*) from sgj_item where item_id between 9001 and 9044 and tags <> replace(tags, ' ', ''))
union all
select '条目标签未登记到 sgj_tag',
       (select count(*) from (
          select i.item_type as module, jt.t as tag
          from sgj_item i,
               json_table(concat('["', replace(i.tags, ',', '","'), '"]'),
                          '$[*]' columns (t varchar(50) path '$')) jt
          where i.item_id between 9001 and 9044 and i.tags <> ''
        ) x
        where not exists (select 1 from sgj_tag g where g.module = x.module and g.tag_name = x.tag))
union all
select '笔记标签未登记到 sgj_tag',
       (select count(*) from (
          select jt.t as tag
          from sgj_note n,
               json_table(concat('["', replace(n.tags, ',', '","'), '"]'),
                          '$[*]' columns (t varchar(50) path '$')) jt
          where n.note_id between 9001 and 9499 and n.tags <> ''
        ) x
        where not exists (select 1 from sgj_tag g where g.module = 'NOTE' and g.tag_name = x.tag));

select date_format(create_time, '%Y-%m') as 月份,
       sum(item_type = 'MOVIE') as 电影,
       sum(item_type = 'TV')    as 电视剧,
       sum(item_type = 'BOOK')  as 书籍,
       sum(item_type = 'PLACE') as 地点,
       sum(status = 'WANT')     as 想看,
       sum(status = 'DONE')     as 看过,
       sum(del_flag = '2')      as 回收站,
       count(*)                 as 合计
from sgj_item
where item_id between 9001 and 9044
group by date_format(create_time, '%Y-%m')
order by 月份;
