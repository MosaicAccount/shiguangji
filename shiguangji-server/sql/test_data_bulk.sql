-- ----------------------------
-- 拾光记批量测试数据（每类条目 50 条，共 200 条 + 笔记 50 条 + 标签 51 条）
-- 前置：按顺序执行 init_system.sql、init_business.sql；与 test_data.sql（9001+ 号段）互不影响
-- 幂等：可重复执行；只清理本脚本写入的 10001-10200 / 10501-10550 / 10601-10651 号数据
-- 覆盖场景：四类条目 × 想看/看过、双用户隔离、软删除、地点真实经纬度（地图选点）、
--            笔记关联/独立 × 公开/私密、创建时间分布近 5 个月（仪表盘趋势）
-- 注意：条目标题/经纬度为真实数据；演职员、ISBN、IMDb/豆瓣编号为测试用近似数据
-- ----------------------------

set names utf8mb4;

-- 0、清理本脚本写入过的旧数据（笔记先行，主表级联删扩展表）
delete from sgj_note where note_id between 10501 and 10550;
delete from sgj_item where item_id between 10001 and 10200;
delete from sgj_tag  where tag_id  between 10601 and 10651;

-- 1、条目主表（200 条：MOVIE/TV/BOOK/PLACE 各 50）
insert into sgj_item (item_id, item_type, title, status, rating, `comment`, tags, cover_url, start_date, finish_date, del_flag, create_by, create_time, update_by, update_time) values
(10001, 'MOVIE', '阿凡达', 'DONE', 7.0, '值回票价，二刷预定。', '科幻,太空', '', date_sub(curdate(), interval 149 day), date_sub(curdate(), interval 149 day), '0', 'admin', date_sub(now(), interval 149 day), 'admin', date_sub(now(), interval 149 day)),
(10002, 'MOVIE', '泰坦尼克号', 'DONE', 8.1, '节奏稍慢，但结局值得。', '爱情,经典', '', date_sub(curdate(), interval 139 day), date_sub(curdate(), interval 139 day), '0', 'admin', date_sub(now(), interval 139 day), 'admin', date_sub(now(), interval 139 day)),
(10003, 'MOVIE', '霸王别姬', 'DONE', 8.8, '氛围感拉满，配乐加分。', '剧情,经典', '', date_sub(curdate(), interval 140 day), date_sub(curdate(), interval 138 day), '0', 'admin', date_sub(now(), interval 140 day), 'admin', date_sub(now(), interval 138 day)),
(10004, 'MOVIE', '阿甘正传', 'WANT', null, '看预告片种草了。', '剧情', '', null, null, '0', 'shiguangji', date_sub(now(), interval 141 day), 'shiguangji', date_sub(now(), interval 141 day)),
(10005, 'MOVIE', '这个杀手不太冷', 'DONE', 9.3, '看完久久不能平静。', '剧情,哲思', '', date_sub(curdate(), interval 135 day), date_sub(curdate(), interval 133 day), '0', 'admin', date_sub(now(), interval 135 day), 'admin', date_sub(now(), interval 133 day)),
(10006, 'MOVIE', '美丽人生', 'WANT', null, '朋友强烈推荐，先 mark。', '剧情,治愈', '', null, null, '2', 'admin', date_sub(now(), interval 135 day), 'admin', now()),
(10007, 'MOVIE', '寄生虫', 'DONE', 9.0, '台词写得真好。', '剧情,黑色幽默', '', date_sub(curdate(), interval 123 day), date_sub(curdate(), interval 122 day), '0', 'admin', date_sub(now(), interval 123 day), 'admin', date_sub(now(), interval 122 day)),
(10008, 'MOVIE', '疯狂原始人', 'DONE', 7.0, '适合一个人深夜看。', '动画,治愈', '', date_sub(curdate(), interval 122 day), date_sub(curdate(), interval 122 day), '0', 'shiguangji', date_sub(now(), interval 122 day), 'shiguangji', date_sub(now(), interval 122 day)),
(10009, 'MOVIE', '寻梦环游记', 'DONE', 9.1, '画面每一帧都是壁纸。', '动画,亲情', '', date_sub(curdate(), interval 121 day), date_sub(curdate(), interval 119 day), '0', 'admin', date_sub(now(), interval 121 day), 'admin', date_sub(now(), interval 119 day)),
(10010, 'MOVIE', '龙猫', 'DONE', 8.1, '被预告片骗了，成片更好。', '动画,治愈', '', date_sub(curdate(), interval 120 day), date_sub(curdate(), interval 120 day), '0', 'admin', date_sub(now(), interval 120 day), 'admin', date_sub(now(), interval 120 day)),
(10011, 'MOVIE', '天空之城', 'DONE', 7.3, '值回票价，二刷预定。', '动画,奇幻', '', date_sub(curdate(), interval 113 day), date_sub(curdate(), interval 111 day), '0', 'admin', date_sub(now(), interval 113 day), 'admin', date_sub(now(), interval 111 day)),
(10012, 'MOVIE', '哈尔的移动城堡', 'WANT', null, '豆瓣高分，列入计划。', '动画,奇幻', '', null, null, '0', 'admin', date_sub(now(), interval 117 day), 'admin', date_sub(now(), interval 117 day)),
(10013, 'MOVIE', '菊次郎的夏天', 'DONE', 8.1, '氛围感拉满，配乐加分。', '剧情,治愈', '', date_sub(curdate(), interval 106 day), date_sub(curdate(), interval 106 day), '0', 'admin', date_sub(now(), interval 106 day), 'admin', date_sub(now(), interval 106 day)),
(10014, 'MOVIE', '触不可及', 'DONE', 7.2, '剧情有硬伤，视听补齐了。', '剧情,哲思', '', date_sub(curdate(), interval 103 day), date_sub(curdate(), interval 102 day), '0', 'shiguangji', date_sub(now(), interval 103 day), 'shiguangji', date_sub(now(), interval 102 day)),
(10015, 'MOVIE', '三傻大闹宝莱坞', 'DONE', 6.7, '看完久久不能平静。', '喜剧,经典', '', date_sub(curdate(), interval 98 day), date_sub(curdate(), interval 98 day), '0', 'admin', date_sub(now(), interval 98 day), 'admin', date_sub(now(), interval 98 day)),
(10016, 'MOVIE', '摔跤吧！爸爸', 'DONE', 7.5, '中规中矩，爆米花合格线。', '剧情', '', date_sub(curdate(), interval 105 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 105 day), 'admin', date_sub(now(), interval 104 day)),
(10017, 'MOVIE', '小偷家族', 'DONE', 8.1, '台词写得真好。', '剧情,治愈', '', date_sub(curdate(), interval 93 day), date_sub(curdate(), interval 91 day), '0', 'admin', date_sub(now(), interval 93 day), 'admin', date_sub(now(), interval 91 day)),
(10018, 'MOVIE', '熔炉', 'DONE', 9.1, '适合一个人深夜看。', '剧情', '', date_sub(curdate(), interval 99 day), date_sub(curdate(), interval 99 day), '0', 'shiguangji', date_sub(now(), interval 99 day), 'shiguangji', date_sub(now(), interval 99 day)),
(10019, 'MOVIE', '素媛', 'DONE', 9.1, '画面每一帧都是壁纸。', '剧情', '', date_sub(curdate(), interval 94 day), date_sub(curdate(), interval 94 day), '0', 'admin', date_sub(now(), interval 94 day), 'admin', date_sub(now(), interval 94 day)),
(10020, 'MOVIE', '辩护人', 'WANT', null, '想去了很久，攒假期中。', '剧情,哲思', '', null, null, '0', 'admin', date_sub(now(), interval 93 day), 'admin', date_sub(now(), interval 93 day)),
(10021, 'MOVIE', '恐怖直播', 'DONE', 9.4, '值回票价，二刷预定。', '悬疑,烧脑', '', date_sub(curdate(), interval 88 day), date_sub(curdate(), interval 88 day), '0', 'admin', date_sub(now(), interval 88 day), 'admin', date_sub(now(), interval 88 day)),
(10022, 'MOVIE', '沙丘', 'DONE', 7.9, '节奏稍慢，但结局值得。', '科幻', '', date_sub(curdate(), interval 80 day), date_sub(curdate(), interval 80 day), '0', 'admin', date_sub(now(), interval 80 day), 'admin', date_sub(now(), interval 80 day)),
(10023, 'MOVIE', '头号玩家', 'DONE', 8.5, '氛围感拉满，配乐加分。', '科幻,剧情', '', date_sub(curdate(), interval 76 day), date_sub(curdate(), interval 74 day), '0', 'admin', date_sub(now(), interval 76 day), 'admin', date_sub(now(), interval 74 day)),
(10024, 'MOVIE', '你的名字。', 'DONE', 6.8, '剧情有硬伤，视听补齐了。', '动画,亲情', '', date_sub(curdate(), interval 74 day), date_sub(curdate(), interval 74 day), '0', 'shiguangji', date_sub(now(), interval 74 day), 'shiguangji', date_sub(now(), interval 74 day)),
(10025, 'MOVIE', '铃芽之旅', 'DONE', 9.4, '看完久久不能平静。', '动画,奇幻', '', date_sub(curdate(), interval 73 day), date_sub(curdate(), interval 72 day), '0', 'admin', date_sub(now(), interval 73 day), 'admin', date_sub(now(), interval 72 day)),
(10026, 'MOVIE', '雄狮少年', 'DONE', 7.2, '中规中矩，爆米花合格线。', '动画,成长', '', date_sub(curdate(), interval 70 day), date_sub(curdate(), interval 69 day), '0', 'admin', date_sub(now(), interval 70 day), 'admin', date_sub(now(), interval 69 day)),
(10027, 'MOVIE', '深海', 'DONE', 9.5, '台词写得真好。', '动画,奇幻', '', date_sub(curdate(), interval 70 day), date_sub(curdate(), interval 69 day), '0', 'admin', date_sub(now(), interval 70 day), 'admin', date_sub(now(), interval 69 day)),
(10028, 'MOVIE', '哪吒之魔童降世', 'WANT', null, '等有整块时间再看。', '动画,喜剧', '', null, null, '0', 'shiguangji', date_sub(now(), interval 69 day), 'shiguangji', date_sub(now(), interval 69 day)),
(10029, 'MOVIE', '白蛇：缘起', 'DONE', 8.7, '画面每一帧都是壁纸。', '动画,奇幻', '', date_sub(curdate(), interval 58 day), date_sub(curdate(), interval 58 day), '0', 'admin', date_sub(now(), interval 58 day), 'admin', date_sub(now(), interval 58 day)),
(10030, 'MOVIE', '无间道', 'DONE', 8.1, '被预告片骗了，成片更好。', '犯罪,经典', '', date_sub(curdate(), interval 60 day), date_sub(curdate(), interval 58 day), '0', 'admin', date_sub(now(), interval 60 day), 'admin', date_sub(now(), interval 58 day)),
(10031, 'MOVIE', '花样年华', 'DONE', 8.5, '值回票价，二刷预定。', '爱情,经典', '', date_sub(curdate(), interval 56 day), date_sub(curdate(), interval 54 day), '0', 'admin', date_sub(now(), interval 56 day), 'admin', date_sub(now(), interval 54 day)),
(10032, 'MOVIE', '重庆森林', 'DONE', 9.8, '节奏稍慢，但结局值得。', '剧情,哲思', '', date_sub(curdate(), interval 52 day), date_sub(curdate(), interval 52 day), '0', 'admin', date_sub(now(), interval 52 day), 'admin', date_sub(now(), interval 52 day)),
(10033, 'MOVIE', '大话西游之大圣娶亲', 'DONE', 6.5, '氛围感拉满，配乐加分。', '喜剧,经典', '', date_sub(curdate(), interval 52 day), date_sub(curdate(), interval 50 day), '0', 'admin', date_sub(now(), interval 52 day), 'admin', date_sub(now(), interval 50 day)),
(10034, 'MOVIE', '功夫', 'DONE', 8.7, '剧情有硬伤，视听补齐了。', '喜剧,动作', '', date_sub(curdate(), interval 44 day), date_sub(curdate(), interval 44 day), '0', 'shiguangji', date_sub(now(), interval 44 day), 'shiguangji', date_sub(now(), interval 44 day)),
(10035, 'MOVIE', '少年的你', 'DONE', 7.6, '看完久久不能平静。', '剧情', '', date_sub(curdate(), interval 38 day), date_sub(curdate(), interval 36 day), '0', 'admin', date_sub(now(), interval 38 day), 'admin', date_sub(now(), interval 36 day)),
(10036, 'MOVIE', '我不是药神', 'WANT', null, '朋友强烈推荐，先 mark。', '剧情', '', null, null, '0', 'admin', date_sub(now(), interval 45 day), 'admin', date_sub(now(), interval 45 day)),
(10037, 'MOVIE', '流浪地球', 'DONE', 7.8, '台词写得真好。', '科幻', '', date_sub(curdate(), interval 42 day), date_sub(curdate(), interval 40 day), '0', 'admin', date_sub(now(), interval 42 day), 'admin', date_sub(now(), interval 40 day)),
(10038, 'MOVIE', '长安三万里', 'DONE', 9.7, '适合一个人深夜看。', '动画,经典', '', date_sub(curdate(), interval 35 day), date_sub(curdate(), interval 33 day), '0', 'shiguangji', date_sub(now(), interval 35 day), 'shiguangji', date_sub(now(), interval 33 day)),
(10039, 'MOVIE', '满江红', 'WANT', null, null, '悬疑', '', null, null, '2', 'admin', date_sub(now(), interval 36 day), 'admin', now()),
(10040, 'MOVIE', '八佰', 'DONE', 6.7, '被预告片骗了，成片更好。', '剧情', '', date_sub(curdate(), interval 25 day), date_sub(curdate(), interval 25 day), '0', 'admin', date_sub(now(), interval 25 day), 'admin', date_sub(now(), interval 25 day)),
(10041, 'MOVIE', '隐入尘烟', 'DONE', 7.4, '值回票价，二刷预定。', '喜剧', '', date_sub(curdate(), interval 23 day), date_sub(curdate(), interval 23 day), '0', 'admin', date_sub(now(), interval 23 day), 'admin', date_sub(now(), interval 23 day)),
(10042, 'MOVIE', '爱情神话', 'DONE', 7.2, '节奏稍慢，但结局值得。', '喜剧,治愈', '', date_sub(curdate(), interval 17 day), date_sub(curdate(), interval 17 day), '0', 'admin', date_sub(now(), interval 17 day), 'admin', date_sub(now(), interval 17 day)),
(10043, 'MOVIE', '热辣滚烫', 'DONE', 7.0, '氛围感拉满，配乐加分。', '剧情,喜剧', '', date_sub(curdate(), interval 16 day), date_sub(curdate(), interval 16 day), '0', 'admin', date_sub(now(), interval 16 day), 'admin', date_sub(now(), interval 16 day)),
(10044, 'MOVIE', '第二十条', 'WANT', null, '看预告片种草了。', '剧情', '', null, null, '0', 'shiguangji', date_sub(now(), interval 21 day), 'shiguangji', date_sub(now(), interval 21 day)),
(10045, 'MOVIE', '异形：夺命舰', 'DONE', 8.2, '看完久久不能平静。', '惊悚,科幻', '', date_sub(curdate(), interval 13 day), date_sub(curdate(), interval 13 day), '0', 'admin', date_sub(now(), interval 13 day), 'admin', date_sub(now(), interval 13 day)),
(10046, 'MOVIE', '奥本海默传', 'DONE', 7.0, '中规中矩，爆米花合格线。', '传记,剧情', '', date_sub(curdate(), interval 13 day), date_sub(curdate(), interval 12 day), '0', 'admin', date_sub(now(), interval 13 day), 'admin', date_sub(now(), interval 12 day)),
(10047, 'MOVIE', '芭比', 'DONE', 9.4, '台词写得真好。', '喜剧', '', date_sub(curdate(), interval 9 day), date_sub(curdate(), interval 8 day), '0', 'admin', date_sub(now(), interval 9 day), 'admin', date_sub(now(), interval 8 day)),
(10048, 'MOVIE', '蜘蛛侠：纵横宇宙', 'DONE', 7.7, '适合一个人深夜看。', '动画,动作', '', date_sub(curdate(), interval 2 day), date_sub(curdate(), interval 0 day), '0', 'shiguangji', date_sub(now(), interval 2 day), 'shiguangji', date_sub(now(), interval 0 day)),
(10049, 'MOVIE', '音乐之声', 'DONE', 8.9, '画面每一帧都是壁纸。', '音乐,经典', '', date_sub(curdate(), interval 2 day), date_sub(curdate(), interval 1 day), '0', 'admin', date_sub(now(), interval 2 day), 'admin', date_sub(now(), interval 1 day)),
(10050, 'MOVIE', '烈火战车', 'DONE', 9.1, '被预告片骗了，成片更好。', '体育,剧情', '', date_sub(curdate(), interval 1 day), date_sub(curdate(), interval 1 day), '0', 'admin', date_sub(now(), interval 1 day), 'admin', date_sub(now(), interval 1 day)),
(10051, 'TV', '权力的游戏', 'DONE', 9.6, '一口气刷完，停不下来。', '奇幻,美剧', '', date_sub(curdate(), interval 143 day), date_sub(curdate(), interval 126 day), '0', 'admin', date_sub(now(), interval 143 day), 'admin', date_sub(now(), interval 126 day)),
(10052, 'TV', '老友记', 'WANT', null, '豆瓣高分，列入计划。', '喜剧,经典', '', null, null, '0', 'admin', date_sub(now(), interval 147 day), 'admin', date_sub(now(), interval 147 day)),
(10053, 'TV', '生活大爆炸', 'DONE', 8.9, '群像戏太精彩了。', '喜剧,美剧', '', date_sub(curdate(), interval 135 day), date_sub(curdate(), interval 128 day), '0', 'admin', date_sub(now(), interval 135 day), 'admin', date_sub(now(), interval 128 day)),
(10054, 'TV', '纸牌屋', 'DONE', 6.7, '每集都是电影质感。', '剧情,美剧', '', date_sub(curdate(), interval 131 day), date_sub(curdate(), interval 126 day), '0', 'shiguangji', date_sub(now(), interval 131 day), 'shiguangji', date_sub(now(), interval 126 day)),
(10055, 'TV', '真探 第一季', 'DONE', 9.7, '下饭神剧，轻松不费脑。', '悬疑,美剧', '', date_sub(curdate(), interval 131 day), date_sub(curdate(), interval 122 day), '0', 'admin', date_sub(now(), interval 131 day), 'admin', date_sub(now(), interval 122 day)),
(10056, 'TV', '切尔诺贝利', 'DONE', 8.5, '悬疑线抓人，感情线拉胯。', '剧情,经典', '', date_sub(curdate(), interval 127 day), date_sub(curdate(), interval 112 day), '0', 'admin', date_sub(now(), interval 127 day), 'admin', date_sub(now(), interval 112 day)),
(10057, 'TV', '傲骨贤妻', 'DONE', 7.8, '值得慢下来细品。', '律政,美剧', '', date_sub(curdate(), interval 130 day), date_sub(curdate(), interval 118 day), '0', 'admin', date_sub(now(), interval 130 day), 'admin', date_sub(now(), interval 118 day)),
(10058, 'TV', '豪斯医生', 'DONE', 7.2, '入坑不亏，推荐给同事了。', '医疗,美剧', '', date_sub(curdate(), interval 120 day), date_sub(curdate(), interval 116 day), '0', 'shiguangji', date_sub(now(), interval 120 day), 'shiguangji', date_sub(now(), interval 116 day)),
(10059, 'TV', '越狱', 'DONE', 9.2, '节奏拖沓，跳着看完的。', '犯罪,美剧', '', date_sub(curdate(), interval 123 day), date_sub(curdate(), interval 107 day), '0', 'admin', date_sub(now(), interval 123 day), 'admin', date_sub(now(), interval 107 day)),
(10060, 'TV', '迷失', 'WANT', null, '想去了很久，攒假期中。', '悬疑,美剧', '', null, null, '0', 'admin', date_sub(now(), interval 123 day), 'admin', date_sub(now(), interval 123 day)),
(10061, 'TV', '神探夏洛克', 'DONE', 6.6, '一口气刷完，停不下来。', '悬疑,英剧', '', date_sub(curdate(), interval 118 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 118 day), 'admin', date_sub(now(), interval 104 day)),
(10062, 'TV', '唐顿庄园', 'DONE', 9.7, '前半程封神，结局烂尾预警。', '剧情,英剧', '', date_sub(curdate(), interval 114 day), date_sub(curdate(), interval 101 day), '0', 'admin', date_sub(now(), interval 114 day), 'admin', date_sub(now(), interval 101 day)),
(10063, 'TV', '王冠 第一季', 'DONE', 7.2, '群像戏太精彩了。', '剧情,英剧', '', date_sub(curdate(), interval 111 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 111 day), 'admin', date_sub(now(), interval 104 day)),
(10064, 'TV', '怪奇物语', 'DONE', 8.6, '每集都是电影质感。', '科幻,美剧', '', date_sub(curdate(), interval 109 day), date_sub(curdate(), interval 95 day), '0', 'shiguangji', date_sub(now(), interval 109 day), 'shiguangji', date_sub(now(), interval 95 day)),
(10065, 'TV', '绝望主妇', 'DONE', 8.0, '下饭神剧，轻松不费脑。', '剧情,美剧', '', date_sub(curdate(), interval 108 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 108 day), 'admin', date_sub(now(), interval 104 day)),
(10066, 'TV', '摩登家庭', 'DONE', 7.7, '悬疑线抓人，感情线拉胯。', '喜剧,美剧', '', date_sub(curdate(), interval 101 day), date_sub(curdate(), interval 84 day), '0', 'admin', date_sub(now(), interval 101 day), 'admin', date_sub(now(), interval 84 day)),
(10067, 'TV', '破产姐妹', 'DONE', 8.0, '值得慢下来细品。', '喜剧,美剧', '', date_sub(curdate(), interval 93 day), date_sub(curdate(), interval 79 day), '0', 'admin', date_sub(now(), interval 93 day), 'admin', date_sub(now(), interval 79 day)),
(10068, 'TV', '汉尼拔', 'WANT', null, '等有整块时间再看。', '犯罪,悬疑', '', null, null, '0', 'shiguangji', date_sub(now(), interval 99 day), 'shiguangji', date_sub(now(), interval 99 day)),
(10069, 'TV', '火线', 'DONE', 9.7, '节奏拖沓，跳着看完的。', '犯罪,美剧', '', date_sub(curdate(), interval 96 day), date_sub(curdate(), interval 81 day), '0', 'admin', date_sub(now(), interval 96 day), 'admin', date_sub(now(), interval 81 day)),
(10070, 'TV', '风骚律师', 'DONE', 6.5, '设定有意思，执行差点意思。', '犯罪,美剧', '', date_sub(curdate(), interval 86 day), date_sub(curdate(), interval 73 day), '0', 'admin', date_sub(now(), interval 86 day), 'admin', date_sub(now(), interval 73 day)),
(10071, 'TV', '行尸走肉', 'DONE', 6.5, '一口气刷完，停不下来。', '悬疑,美剧', '', date_sub(curdate(), interval 86 day), date_sub(curdate(), interval 68 day), '0', 'admin', date_sub(now(), interval 86 day), 'admin', date_sub(now(), interval 68 day)),
(10072, 'TV', '九号秘事', 'WANT', null, '豆瓣高分，列入计划。', '悬疑,英剧', '', null, null, '2', 'admin', date_sub(now(), interval 87 day), 'admin', now()),
(10073, 'TV', '我的解放日志', 'DONE', 9.3, '群像戏太精彩了。', '剧情,韩剧', '', date_sub(curdate(), interval 74 day), date_sub(curdate(), interval 71 day), '0', 'admin', date_sub(now(), interval 74 day), 'admin', date_sub(now(), interval 71 day)),
(10074, 'TV', '信号', 'DONE', 7.3, '每集都是电影质感。', '悬疑,韩剧', '', date_sub(curdate(), interval 74 day), date_sub(curdate(), interval 59 day), '0', 'shiguangji', date_sub(now(), interval 74 day), 'shiguangji', date_sub(now(), interval 59 day)),
(10075, 'TV', '秘密森林', 'DONE', 7.1, '下饭神剧，轻松不费脑。', '悬疑,韩剧', '', date_sub(curdate(), interval 75 day), date_sub(curdate(), interval 72 day), '0', 'admin', date_sub(now(), interval 75 day), 'admin', date_sub(now(), interval 72 day)),
(10076, 'TV', '机智的医生生活', 'WANT', null, '朋友强烈推荐，先 mark。', '医疗,韩剧', '', null, null, '0', 'admin', date_sub(now(), interval 75 day), 'admin', date_sub(now(), interval 75 day)),
(10077, 'TV', '请回答1994', 'DONE', 8.7, '值得慢下来细品。', '剧情,韩剧', '', date_sub(curdate(), interval 62 day), date_sub(curdate(), interval 46 day), '0', 'admin', date_sub(now(), interval 62 day), 'admin', date_sub(now(), interval 46 day)),
(10078, 'TV', '孤单又灿烂的神：鬼怪', 'DONE', 8.2, '入坑不亏，推荐给同事了。', '奇幻,韩剧', '', date_sub(curdate(), interval 59 day), date_sub(curdate(), interval 50 day), '0', 'shiguangji', date_sub(now(), interval 59 day), 'shiguangji', date_sub(now(), interval 50 day)),
(10079, 'TV', '爱的迫降', 'DONE', 7.4, '节奏拖沓，跳着看完的。', '爱情,韩剧', '', date_sub(curdate(), interval 65 day), date_sub(curdate(), interval 48 day), '0', 'admin', date_sub(now(), interval 65 day), 'admin', date_sub(now(), interval 48 day)),
(10080, 'TV', '半泽直树', 'DONE', 8.2, '设定有意思，执行差点意思。', '职场,日剧', '', date_sub(curdate(), interval 57 day), date_sub(curdate(), interval 37 day), '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 37 day)),
(10081, 'TV', '非自然死亡', 'DONE', 8.0, '一口气刷完，停不下来。', '医疗,日剧', '', date_sub(curdate(), interval 59 day), date_sub(curdate(), interval 45 day), '0', 'admin', date_sub(now(), interval 59 day), 'admin', date_sub(now(), interval 45 day)),
(10082, 'TV', '逃避虽可耻但有用', 'DONE', 6.9, '前半程封神，结局烂尾预警。', '爱情,日剧', '', date_sub(curdate(), interval 52 day), date_sub(curdate(), interval 49 day), '0', 'admin', date_sub(now(), interval 52 day), 'admin', date_sub(now(), interval 49 day)),
(10083, 'TV', '重启人生', 'DONE', 7.6, '群像戏太精彩了。', '剧情,日剧', '', date_sub(curdate(), interval 48 day), date_sub(curdate(), interval 33 day), '0', 'admin', date_sub(now(), interval 48 day), 'admin', date_sub(now(), interval 33 day)),
(10084, 'TV', '知否知否应是绿肥红瘦', 'WANT', null, '看预告片种草了。', '古装,家庭', '', null, null, '0', 'shiguangji', date_sub(now(), interval 51 day), 'shiguangji', date_sub(now(), interval 51 day)),
(10085, 'TV', '父母爱情', 'DONE', 8.6, '下饭神剧，轻松不费脑。', '家庭,经典', '', date_sub(curdate(), interval 46 day), date_sub(curdate(), interval 41 day), '0', 'admin', date_sub(now(), interval 46 day), 'admin', date_sub(now(), interval 41 day)),
(10086, 'TV', '爱情公寓', 'DONE', 9.7, '悬疑线抓人，感情线拉胯。', '喜剧,青春', '', date_sub(curdate(), interval 36 day), date_sub(curdate(), interval 21 day), '0', 'admin', date_sub(now(), interval 36 day), 'admin', date_sub(now(), interval 21 day)),
(10087, 'TV', '武林外传', 'DONE', 9.2, '值得慢下来细品。', '喜剧,经典', '', date_sub(curdate(), interval 37 day), date_sub(curdate(), interval 25 day), '0', 'admin', date_sub(now(), interval 37 day), 'admin', date_sub(now(), interval 25 day)),
(10088, 'TV', '如懿传', 'DONE', 7.6, '入坑不亏，推荐给同事了。', '宫斗,古装', '', date_sub(curdate(), interval 38 day), date_sub(curdate(), interval 20 day), '0', 'shiguangji', date_sub(now(), interval 38 day), 'shiguangji', date_sub(now(), interval 20 day)),
(10089, 'TV', '步步惊心', 'DONE', 7.5, '节奏拖沓，跳着看完的。', '古装,经典', '', date_sub(curdate(), interval 32 day), date_sub(curdate(), interval 27 day), '0', 'admin', date_sub(now(), interval 32 day), 'admin', date_sub(now(), interval 27 day)),
(10090, 'TV', '隐秘的角落', 'DONE', 6.9, '设定有意思，执行差点意思。', '悬疑', '', date_sub(curdate(), interval 29 day), date_sub(curdate(), interval 17 day), '0', 'admin', date_sub(now(), interval 29 day), 'admin', date_sub(now(), interval 17 day)),
(10091, 'TV', '沉默的真相', 'DONE', 8.8, '一口气刷完，停不下来。', '悬疑', '', date_sub(curdate(), interval 20 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 20 day), 'admin', date_sub(now(), interval 0 day)),
(10092, 'TV', '白夜追凶', 'WANT', null, '豆瓣高分，列入计划。', '犯罪,悬疑', '', null, null, '0', 'admin', date_sub(now(), interval 27 day), 'admin', date_sub(now(), interval 27 day)),
(10093, 'TV', '狂飙', 'DONE', 8.1, '群像戏太精彩了。', '科幻', '', date_sub(curdate(), interval 23 day), date_sub(curdate(), interval 14 day), '0', 'admin', date_sub(now(), interval 23 day), 'admin', date_sub(now(), interval 14 day)),
(10094, 'TV', '三体', 'DONE', 9.3, '每集都是电影质感。', '剧情,群像', '', date_sub(curdate(), interval 17 day), date_sub(curdate(), interval 5 day), '0', 'shiguangji', date_sub(now(), interval 17 day), 'shiguangji', date_sub(now(), interval 5 day)),
(10095, 'TV', '繁花', 'DONE', 9.0, '下饭神剧，轻松不费脑。', '剧情,群像', '', date_sub(curdate(), interval 15 day), date_sub(curdate(), interval 8 day), '0', 'admin', date_sub(now(), interval 15 day), 'admin', date_sub(now(), interval 8 day)),
(10096, 'TV', '山海情', 'DONE', 7.8, '悬疑线抓人，感情线拉胯。', '历史,剧情', '', date_sub(curdate(), interval 7 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 7 day), 'admin', date_sub(now(), interval 0 day)),
(10097, 'TV', '觉醒年代', 'DONE', 9.2, '值得慢下来细品。', '剧情', '', date_sub(curdate(), interval 5 day), date_sub(curdate(), interval 2 day), '0', 'admin', date_sub(now(), interval 5 day), 'admin', date_sub(now(), interval 2 day)),
(10098, 'TV', '人世间', 'DONE', 7.1, '入坑不亏，推荐给同事了。', '悬疑', '', date_sub(curdate(), interval 3 day), date_sub(curdate(), interval 0 day), '0', 'shiguangji', date_sub(now(), interval 3 day), 'shiguangji', date_sub(now(), interval 0 day)),
(10099, 'TV', '漫长的季节', 'DONE', 7.7, '节奏拖沓，跳着看完的。', '古装,权谋', '', date_sub(curdate(), interval 4 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 4 day), 'admin', date_sub(now(), interval 0 day)),
(10100, 'TV', '庆余年 第二季', 'WANT', null, '想去了很久，攒假期中。', '剧情,家庭', '', null, null, '0', 'admin', date_sub(now(), interval 2 day), 'admin', date_sub(now(), interval 2 day)),
(10101, 'BOOK', '围城', 'DONE', 8.1, '读完心里很满，值得重读。', '小说,经典', '', date_sub(curdate(), interval 150 day), date_sub(curdate(), interval 143 day), '0', 'admin', date_sub(now(), interval 150 day), 'admin', date_sub(now(), interval 143 day)),
(10102, 'BOOK', '平凡的世界', 'DONE', 9.3, '翻译略涩，内容扎实。', '小说,经典', '', date_sub(curdate(), interval 137 day), date_sub(curdate(), interval 112 day), '0', 'admin', date_sub(now(), interval 137 day), 'admin', date_sub(now(), interval 112 day)),
(10103, 'BOOK', '白鹿原', 'DONE', 8.9, '前半本惊艳，后半本说教。', '小说,经典', '', date_sub(curdate(), interval 137 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 137 day), 'admin', date_sub(now(), interval 104 day)),
(10104, 'BOOK', '活着', 'DONE', 8.6, '枕边书，睡前翻几页很治愈。', '小说,治愈', '', date_sub(curdate(), interval 132 day), date_sub(curdate(), interval 118 day), '0', 'shiguangji', date_sub(now(), interval 132 day), 'shiguangji', date_sub(now(), interval 118 day)),
(10105, 'BOOK', '许三观卖血记', 'WANT', null, null, '小说', '', null, null, '2', 'admin', date_sub(now(), interval 138 day), 'admin', now()),
(10106, 'BOOK', '三体Ⅱ·黑暗森林', 'DONE', 6.9, '观点鲜明，论证稍弱。', '科幻,小说', '', date_sub(curdate(), interval 133 day), date_sub(curdate(), interval 108 day), '0', 'admin', date_sub(now(), interval 133 day), 'admin', date_sub(now(), interval 108 day)),
(10107, 'BOOK', '球状闪电', 'DONE', 7.0, '经典就是经典，常读常新。', '科幻,小说', '', date_sub(curdate(), interval 124 day), date_sub(curdate(), interval 100 day), '0', 'admin', date_sub(now(), interval 124 day), 'admin', date_sub(now(), interval 100 day)),
(10108, 'BOOK', '1984', 'WANT', null, '等有整块时间再看。', '小说,经典', '', null, null, '0', 'shiguangji', date_sub(now(), interval 129 day), 'shiguangji', date_sub(now(), interval 129 day)),
(10109, 'BOOK', '动物农场', 'DONE', 7.6, '入门友好，进阶不够。', '小说', '', date_sub(curdate(), interval 121 day), date_sub(curdate(), interval 115 day), '0', 'admin', date_sub(now(), interval 121 day), 'admin', date_sub(now(), interval 115 day)),
(10110, 'BOOK', '美丽新世界', 'DONE', 7.0, '被书名耽误的好书。', '小说,经典', '', date_sub(curdate(), interval 113 day), date_sub(curdate(), interval 106 day), '0', 'admin', date_sub(now(), interval 113 day), 'admin', date_sub(now(), interval 106 day)),
(10111, 'BOOK', '了不起的盖茨比', 'DONE', 6.8, '读完心里很满，值得重读。', '小说,经典', '', date_sub(curdate(), interval 117 day), date_sub(curdate(), interval 107 day), '0', 'admin', date_sub(now(), interval 117 day), 'admin', date_sub(now(), interval 107 day)),
(10112, 'BOOK', '月亮与六便士', 'DONE', 9.0, '翻译略涩，内容扎实。', '小说,经典', '', date_sub(curdate(), interval 114 day), date_sub(curdate(), interval 104 day), '0', 'admin', date_sub(now(), interval 114 day), 'admin', date_sub(now(), interval 104 day)),
(10113, 'BOOK', '刀锋', 'DONE', 9.4, '前半本惊艳，后半本说教。', '小说', '', date_sub(curdate(), interval 113 day), date_sub(curdate(), interval 96 day), '0', 'admin', date_sub(now(), interval 113 day), 'admin', date_sub(now(), interval 96 day)),
(10114, 'BOOK', '挪威的森林', 'DONE', 9.8, '枕边书，睡前翻几页很治愈。', '小说', '', date_sub(curdate(), interval 102 day), date_sub(curdate(), interval 82 day), '0', 'shiguangji', date_sub(now(), interval 102 day), 'shiguangji', date_sub(now(), interval 82 day)),
(10115, 'BOOK', '海边的卡夫卡', 'DONE', 8.6, '信息量大，做了不少笔记。', '小说,治愈', '', date_sub(curdate(), interval 101 day), date_sub(curdate(), interval 87 day), '0', 'admin', date_sub(now(), interval 101 day), 'admin', date_sub(now(), interval 87 day)),
(10116, 'BOOK', '解忧杂货店', 'WANT', null, '朋友强烈推荐，先 mark。', '推理,小说', '', null, null, '0', 'admin', date_sub(now(), interval 105 day), 'admin', date_sub(now(), interval 105 day)),
(10117, 'BOOK', '白夜行', 'DONE', 9.0, '经典就是经典，常读常新。', '推理,经典', '', date_sub(curdate(), interval 100 day), date_sub(curdate(), interval 84 day), '0', 'admin', date_sub(now(), interval 100 day), 'admin', date_sub(now(), interval 84 day)),
(10118, 'BOOK', '嫌疑人X的献身', 'DONE', 8.0, '案例略旧，方法论仍然好用。', '推理', '', date_sub(curdate(), interval 92 day), date_sub(curdate(), interval 72 day), '0', 'shiguangji', date_sub(now(), interval 92 day), 'shiguangji', date_sub(now(), interval 72 day)),
(10119, 'BOOK', '长夜难明', 'DONE', 7.1, '入门友好，进阶不够。', '推理', '', date_sub(curdate(), interval 95 day), date_sub(curdate(), interval 82 day), '0', 'admin', date_sub(now(), interval 95 day), 'admin', date_sub(now(), interval 82 day)),
(10120, 'BOOK', '无声告白', 'DONE', 6.5, '被书名耽误的好书。', '小说,治愈', '', date_sub(curdate(), interval 92 day), date_sub(curdate(), interval 70 day), '0', 'admin', date_sub(now(), interval 92 day), 'admin', date_sub(now(), interval 70 day)),
(10121, 'BOOK', '追风筝的人', 'DONE', 9.1, '读完心里很满，值得重读。', '小说,治愈', '', date_sub(curdate(), interval 82 day), date_sub(curdate(), interval 60 day), '0', 'admin', date_sub(now(), interval 82 day), 'admin', date_sub(now(), interval 60 day)),
(10122, 'BOOK', '灿烂千阳', 'DONE', 6.9, '翻译略涩，内容扎实。', '小说', '', date_sub(curdate(), interval 85 day), date_sub(curdate(), interval 75 day), '0', 'admin', date_sub(now(), interval 85 day), 'admin', date_sub(now(), interval 75 day)),
(10123, 'BOOK', '局外人', 'DONE', 9.1, '前半本惊艳，后半本说教。', '小说,经典', '', date_sub(curdate(), interval 75 day), date_sub(curdate(), interval 60 day), '0', 'admin', date_sub(now(), interval 75 day), 'admin', date_sub(now(), interval 60 day)),
(10124, 'BOOK', '鼠疫', 'WANT', null, '看预告片种草了。', '小说,经典', '', null, null, '0', 'shiguangji', date_sub(now(), interval 81 day), 'shiguangji', date_sub(now(), interval 81 day)),
(10125, 'BOOK', '悉达多', 'DONE', 9.1, '信息量大，做了不少笔记。', '小说,经典', '', date_sub(curdate(), interval 77 day), date_sub(curdate(), interval 65 day), '0', 'admin', date_sub(now(), interval 77 day), 'admin', date_sub(now(), interval 65 day)),
(10126, 'BOOK', '苏菲的世界', 'DONE', 8.1, '观点鲜明，论证稍弱。', '哲学', '', date_sub(curdate(), interval 66 day), date_sub(curdate(), interval 44 day), '0', 'admin', date_sub(now(), interval 66 day), 'admin', date_sub(now(), interval 44 day)),
(10127, 'BOOK', '沉思录', 'DONE', 9.3, '经典就是经典，常读常新。', '哲学', '', date_sub(curdate(), interval 70 day), date_sub(curdate(), interval 65 day), '0', 'admin', date_sub(now(), interval 70 day), 'admin', date_sub(now(), interval 65 day)),
(10128, 'BOOK', '被讨厌的勇气', 'DONE', 8.7, '案例略旧，方法论仍然好用。', '心理,成长', '', date_sub(curdate(), interval 69 day), date_sub(curdate(), interval 40 day), '0', 'shiguangji', date_sub(now(), interval 69 day), 'shiguangji', date_sub(now(), interval 40 day)),
(10129, 'BOOK', '乌合之众', 'DONE', 7.6, '入门友好，进阶不够。', '心理,经典', '', date_sub(curdate(), interval 65 day), date_sub(curdate(), interval 46 day), '0', 'admin', date_sub(now(), interval 65 day), 'admin', date_sub(now(), interval 46 day)),
(10130, 'BOOK', '自控力', 'DONE', 7.7, '被书名耽误的好书。', '心理,成长', '', date_sub(curdate(), interval 57 day), date_sub(curdate(), interval 24 day), '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 24 day)),
(10131, 'BOOK', '思考，快与慢', 'DONE', 6.6, '读完心里很满，值得重读。', '心理,经典', '', date_sub(curdate(), interval 53 day), date_sub(curdate(), interval 45 day), '0', 'admin', date_sub(now(), interval 53 day), 'admin', date_sub(now(), interval 45 day)),
(10132, 'BOOK', '穷查理宝典', 'WANT', null, '豆瓣高分，列入计划。', '经济,经典', '', null, null, '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 57 day)),
(10133, 'BOOK', '经济学原理：微观经济学分册', 'DONE', 7.9, '前半本惊艳，后半本说教。', '经济,方法论', '', date_sub(curdate(), interval 51 day), date_sub(curdate(), interval 35 day), '0', 'admin', date_sub(now(), interval 51 day), 'admin', date_sub(now(), interval 35 day)),
(10134, 'BOOK', '置身事内：中国政府与经济发展', 'DONE', 9.6, '枕边书，睡前翻几页很治愈。', '经济,方法论', '', date_sub(curdate(), interval 49 day), date_sub(curdate(), interval 20 day), '0', 'shiguangji', date_sub(now(), interval 49 day), 'shiguangji', date_sub(now(), interval 20 day)),
(10135, 'BOOK', '刻意练习', 'DONE', 8.9, '信息量大，做了不少笔记。', '方法论,成长', '', date_sub(curdate(), interval 41 day), date_sub(curdate(), interval 11 day), '0', 'admin', date_sub(now(), interval 41 day), 'admin', date_sub(now(), interval 11 day)),
(10136, 'BOOK', '算法导论（原书第3版）', 'DONE', 9.0, '观点鲜明，论证稍弱。', '算法,经典', '', date_sub(curdate(), interval 39 day), date_sub(curdate(), interval 20 day), '0', 'admin', date_sub(now(), interval 39 day), 'admin', date_sub(now(), interval 20 day)),
(10137, 'BOOK', '深入理解计算机系统（第3版）', 'DONE', 8.7, '经典就是经典，常读常新。', '编程,经典', '', date_sub(curdate(), interval 37 day), date_sub(curdate(), interval 16 day), '0', 'admin', date_sub(now(), interval 37 day), 'admin', date_sub(now(), interval 16 day)),
(10138, 'BOOK', '重构：改善既有代码的设计（第2版）', 'WANT', null, '等有整块时间再看。', '编程,工程', '', null, null, '2', 'shiguangji', date_sub(now(), interval 39 day), 'shiguangji', now()),
(10139, 'BOOK', '设计数据密集型应用', 'DONE', 8.4, '入门友好，进阶不够。', '编程,方法论', '', date_sub(curdate(), interval 35 day), date_sub(curdate(), interval 24 day), '0', 'admin', date_sub(now(), interval 35 day), 'admin', date_sub(now(), interval 24 day)),
(10140, 'BOOK', '万历十五年', 'WANT', null, '想去了很久，攒假期中。', '历史,经典', '', null, null, '0', 'admin', date_sub(now(), interval 33 day), 'admin', date_sub(now(), interval 33 day)),
(10141, 'BOOK', '人类简史', 'DONE', 8.3, '读完心里很满，值得重读。', '历史,科普', '', date_sub(curdate(), interval 30 day), date_sub(curdate(), interval 13 day), '0', 'admin', date_sub(now(), interval 30 day), 'admin', date_sub(now(), interval 13 day)),
(10142, 'BOOK', '未来简史', 'DONE', 8.1, '翻译略涩，内容扎实。', '历史,科普', '', date_sub(curdate(), interval 22 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 22 day), 'admin', date_sub(now(), interval 0 day)),
(10143, 'BOOK', '枪炮、病菌与钢铁', 'DONE', 8.8, '前半本惊艳，后半本说教。', '历史,经典', '', date_sub(curdate(), interval 21 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 21 day), 'admin', date_sub(now(), interval 0 day)),
(10144, 'BOOK', '邓小平时代', 'DONE', 8.4, '枕边书，睡前翻几页很治愈。', '历史,传记', '', date_sub(curdate(), interval 20 day), date_sub(curdate(), interval 12 day), '0', 'shiguangji', date_sub(now(), interval 20 day), 'shiguangji', date_sub(now(), interval 12 day)),
(10145, 'BOOK', '江城', 'DONE', 8.7, '信息量大，做了不少笔记。', '散文,生活', '', date_sub(curdate(), interval 17 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 17 day), 'admin', date_sub(now(), interval 0 day)),
(10146, 'BOOK', '寻路中国', 'DONE', 8.3, '观点鲜明，论证稍弱。', '散文,生活', '', date_sub(curdate(), interval 9 day), date_sub(curdate(), interval 3 day), '0', 'admin', date_sub(now(), interval 9 day), 'admin', date_sub(now(), interval 3 day)),
(10147, 'BOOK', '撒哈拉的故事', 'DONE', 6.7, '经典就是经典，常读常新。', '散文,治愈', '', date_sub(curdate(), interval 2 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 2 day), 'admin', date_sub(now(), interval 0 day)),
(10148, 'BOOK', '目送', 'WANT', null, '等有整块时间再看。', '散文,治愈', '', null, null, '0', 'shiguangji', date_sub(now(), interval 9 day), 'shiguangji', date_sub(now(), interval 9 day)),
(10149, 'BOOK', '我们仨', 'DONE', 6.8, '入门友好，进阶不够。', '散文,经典', '', date_sub(curdate(), interval 4 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 4 day), 'admin', date_sub(now(), interval 0 day)),
(10150, 'BOOK', '边城', 'DONE', 9.1, '被书名耽误的好书。', '小说,经典', '', date_sub(curdate(), interval 1 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 1 day), 'admin', date_sub(now(), interval 0 day)),
(10151, 'PLACE', '泰山', 'DONE', 9.2, '人少景美，值得专程来。', '山岳,自然', '', date_sub(curdate(), interval 145 day), date_sub(curdate(), interval 138 day), '0', 'admin', date_sub(now(), interval 145 day), 'admin', date_sub(now(), interval 138 day)),
(10152, 'PLACE', '黄山风景区', 'DONE', 7.7, '旺季人多，建议错峰。', '山岳,自然', '', date_sub(curdate(), interval 142 day), date_sub(curdate(), interval 137 day), '0', 'admin', date_sub(now(), interval 142 day), 'admin', date_sub(now(), interval 137 day)),
(10153, 'PLACE', '华山', 'DONE', 7.2, '拍照超出片，注意防晒。', '山岳,自然', '', date_sub(curdate(), interval 136 day), date_sub(curdate(), interval 132 day), '0', 'admin', date_sub(now(), interval 136 day), 'admin', date_sub(now(), interval 132 day)),
(10154, 'PLACE', '峨眉山', 'DONE', 7.9, '讲解很值，别省这个钱。', '山岳,自然', '', date_sub(curdate(), interval 133 day), date_sub(curdate(), interval 127 day), '0', 'shiguangji', date_sub(now(), interval 133 day), 'shiguangji', date_sub(now(), interval 127 day)),
(10155, 'PLACE', '武功山', 'DONE', 7.1, '住宿选景区里体验更好。', '山岳,自然', '', date_sub(curdate(), interval 132 day), date_sub(curdate(), interval 126 day), '0', 'admin', date_sub(now(), interval 132 day), 'admin', date_sub(now(), interval 126 day)),
(10156, 'PLACE', '长白山天池', 'WANT', null, '朋友强烈推荐，先 mark。', '湖泊,自然', '', null, null, '0', 'admin', date_sub(now(), interval 135 day), 'admin', date_sub(now(), interval 135 day)),
(10157, 'PLACE', '青海湖', 'DONE', 9.4, '交通不太方便，建议自驾。', '湖泊,自然', '', date_sub(curdate(), interval 128 day), date_sub(curdate(), interval 127 day), '0', 'admin', date_sub(now(), interval 128 day), 'admin', date_sub(now(), interval 127 day)),
(10158, 'PLACE', '纳木错', 'DONE', 9.3, '日出一定要看，早起值得。', '湖泊,人文', '', date_sub(curdate(), interval 122 day), date_sub(curdate(), interval 119 day), '0', 'shiguangji', date_sub(now(), interval 122 day), 'shiguangji', date_sub(now(), interval 119 day)),
(10159, 'PLACE', '鸣沙山月牙泉', 'DONE', 8.5, '体力消耗大，穿对鞋。', '沙漠,自然', '', date_sub(curdate(), interval 121 day), date_sub(curdate(), interval 115 day), '0', 'admin', date_sub(now(), interval 121 day), 'admin', date_sub(now(), interval 115 day)),
(10160, 'PLACE', '张掖七彩丹霞', 'DONE', 9.6, '适合慢悠悠逛一天。', '自然,湖泊', '', date_sub(curdate(), interval 116 day), date_sub(curdate(), interval 111 day), '0', 'admin', date_sub(now(), interval 116 day), 'admin', date_sub(now(), interval 111 day)),
(10161, 'PLACE', '喀纳斯湖', 'DONE', 9.6, '人少景美，值得专程来。', '湖泊,自然', '', date_sub(curdate(), interval 110 day), date_sub(curdate(), interval 106 day), '0', 'admin', date_sub(now(), interval 110 day), 'admin', date_sub(now(), interval 106 day)),
(10162, 'PLACE', '洱海', 'DONE', 7.0, '旺季人多，建议错峰。', '湖泊,古镇', '', date_sub(curdate(), interval 114 day), date_sub(curdate(), interval 111 day), '0', 'admin', date_sub(now(), interval 114 day), 'admin', date_sub(now(), interval 111 day)),
(10163, 'PLACE', '泸沽湖', 'DONE', 6.7, '拍照超出片，注意防晒。', '湖泊,古镇', '', date_sub(curdate(), interval 113 day), date_sub(curdate(), interval 109 day), '0', 'admin', date_sub(now(), interval 113 day), 'admin', date_sub(now(), interval 109 day)),
(10164, 'PLACE', '桂林漓江', 'WANT', null, '看预告片种草了。', '自然,古镇', '', null, null, '0', 'shiguangji', date_sub(now(), interval 111 day), 'shiguangji', date_sub(now(), interval 111 day)),
(10165, 'PLACE', '黄果树瀑布', 'DONE', 8.9, '住宿选景区里体验更好。', '自然,峡谷', '', date_sub(curdate(), interval 101 day), date_sub(curdate(), interval 98 day), '0', 'admin', date_sub(now(), interval 101 day), 'admin', date_sub(now(), interval 98 day)),
(10166, 'PLACE', '梵净山', 'DONE', 8.9, '本地美食出乎意料。', '山岳,自然', '', date_sub(curdate(), interval 95 day), date_sub(curdate(), interval 88 day), '0', 'admin', date_sub(now(), interval 95 day), 'admin', date_sub(now(), interval 88 day)),
(10167, 'PLACE', '鼓浪屿', 'DONE', 9.5, '交通不太方便，建议自驾。', '海岛,古镇', '', date_sub(curdate(), interval 99 day), date_sub(curdate(), interval 94 day), '0', 'admin', date_sub(now(), interval 99 day), 'admin', date_sub(now(), interval 94 day)),
(10168, 'PLACE', '武夷山', 'DONE', 8.8, '日出一定要看，早起值得。', '山岳,自然', '', date_sub(curdate(), interval 91 day), date_sub(curdate(), interval 85 day), '0', 'shiguangji', date_sub(now(), interval 91 day), 'shiguangji', date_sub(now(), interval 85 day)),
(10169, 'PLACE', '婺源篁岭', 'DONE', 9.0, '体力消耗大，穿对鞋。', '古镇,自然', '', date_sub(curdate(), interval 91 day), date_sub(curdate(), interval 86 day), '0', 'admin', date_sub(now(), interval 91 day), 'admin', date_sub(now(), interval 86 day)),
(10170, 'PLACE', '宏村', 'DONE', 9.8, '适合慢悠悠逛一天。', '古镇,建筑', '', date_sub(curdate(), interval 88 day), date_sub(curdate(), interval 87 day), '0', 'admin', date_sub(now(), interval 88 day), 'admin', date_sub(now(), interval 87 day)),
(10171, 'PLACE', '乌镇', 'WANT', null, null, '古镇,建筑', '', null, null, '2', 'admin', date_sub(now(), interval 90 day), 'admin', now()),
(10172, 'PLACE', '周庄古镇', 'WANT', null, '豆瓣高分，列入计划。', '古镇,古建', '', null, null, '0', 'admin', date_sub(now(), interval 87 day), 'admin', date_sub(now(), interval 87 day)),
(10173, 'PLACE', '平遥古城', 'DONE', 9.0, '拍照超出片，注意防晒。', '古镇,人文', '', date_sub(curdate(), interval 82 day), date_sub(curdate(), interval 76 day), '0', 'admin', date_sub(now(), interval 82 day), 'admin', date_sub(now(), interval 76 day)),
(10174, 'PLACE', '云冈石窟', 'DONE', 7.1, '讲解很值，别省这个钱。', '人文,古建', '', date_sub(curdate(), interval 73 day), date_sub(curdate(), interval 71 day), '0', 'shiguangji', date_sub(now(), interval 73 day), 'shiguangji', date_sub(now(), interval 71 day)),
(10175, 'PLACE', '莫高窟', 'DONE', 6.8, '住宿选景区里体验更好。', '人文,古建', '', date_sub(curdate(), interval 70 day), date_sub(curdate(), interval 66 day), '0', 'admin', date_sub(now(), interval 70 day), 'admin', date_sub(now(), interval 66 day)),
(10176, 'PLACE', '嘉峪关', 'DONE', 9.7, '本地美食出乎意料。', '人文,古建', '', date_sub(curdate(), interval 65 day), date_sub(curdate(), interval 61 day), '0', 'admin', date_sub(now(), interval 65 day), 'admin', date_sub(now(), interval 61 day)),
(10177, 'PLACE', '天坛公园', 'DONE', 9.7, '交通不太方便，建议自驾。', '人文,古建', '', date_sub(curdate(), interval 62 day), date_sub(curdate(), interval 55 day), '0', 'admin', date_sub(now(), interval 62 day), 'admin', date_sub(now(), interval 55 day)),
(10178, 'PLACE', '颐和园', 'DONE', 9.1, '日出一定要看，早起值得。', '人文,古建', '', date_sub(curdate(), interval 65 day), date_sub(curdate(), interval 63 day), '0', 'shiguangji', date_sub(now(), interval 65 day), 'shiguangji', date_sub(now(), interval 63 day)),
(10179, 'PLACE', '八达岭长城', 'DONE', 7.6, '体力消耗大，穿对鞋。', '人文,古建', '', date_sub(curdate(), interval 58 day), date_sub(curdate(), interval 51 day), '0', 'admin', date_sub(now(), interval 58 day), 'admin', date_sub(now(), interval 51 day)),
(10180, 'PLACE', '外滩', 'WANT', null, '想去了很久，攒假期中。', '人文,建筑', '', null, null, '0', 'admin', date_sub(now(), interval 63 day), 'admin', date_sub(now(), interval 63 day)),
(10181, 'PLACE', '广州塔', 'DONE', 7.7, '人少景美，值得专程来。', '建筑,人文', '', date_sub(curdate(), interval 57 day), date_sub(curdate(), interval 50 day), '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 50 day)),
(10182, 'PLACE', '中山陵', 'DONE', 7.6, '旺季人多，建议错峰。', '人文,古建', '', date_sub(curdate(), interval 57 day), date_sub(curdate(), interval 56 day), '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 56 day)),
(10183, 'PLACE', '夫子庙', 'DONE', 7.5, '拍照超出片，注意防晒。', '人文,古建', '', date_sub(curdate(), interval 48 day), date_sub(curdate(), interval 45 day), '0', 'admin', date_sub(now(), interval 48 day), 'admin', date_sub(now(), interval 45 day)),
(10184, 'PLACE', '趵突泉', 'DONE', 8.5, '讲解很值，别省这个钱。', '自然,人文', '', date_sub(curdate(), interval 47 day), date_sub(curdate(), interval 43 day), '0', 'shiguangji', date_sub(now(), interval 47 day), 'shiguangji', date_sub(now(), interval 43 day)),
(10185, 'PLACE', '栈桥', 'DONE', 8.1, '住宿选景区里体验更好。', '人文,建筑', '', date_sub(curdate(), interval 46 day), date_sub(curdate(), interval 39 day), '0', 'admin', date_sub(now(), interval 46 day), 'admin', date_sub(now(), interval 39 day)),
(10186, 'PLACE', '崂山', 'DONE', 6.8, '本地美食出乎意料。', '山岳,自然', '', date_sub(curdate(), interval 37 day), date_sub(curdate(), interval 36 day), '0', 'admin', date_sub(now(), interval 37 day), 'admin', date_sub(now(), interval 36 day)),
(10187, 'PLACE', '神农架', 'DONE', 9.7, '交通不太方便，建议自驾。', '自然,山岳', '', date_sub(curdate(), interval 38 day), date_sub(curdate(), interval 34 day), '0', 'admin', date_sub(now(), interval 38 day), 'admin', date_sub(now(), interval 34 day)),
(10188, 'PLACE', '恩施大峡谷', 'WANT', null, '等有整块时间再看。', '峡谷,自然', '', null, null, '0', 'shiguangji', date_sub(now(), interval 39 day), 'shiguangji', date_sub(now(), interval 39 day)),
(10189, 'PLACE', '凤凰古城', 'DONE', 7.7, '体力消耗大，穿对鞋。', '古镇,人文', '', date_sub(curdate(), interval 27 day), date_sub(curdate(), interval 24 day), '0', 'admin', date_sub(now(), interval 27 day), 'admin', date_sub(now(), interval 24 day)),
(10190, 'PLACE', '岳麓山', 'DONE', 7.5, '适合慢悠悠逛一天。', '山岳,人文', '', date_sub(curdate(), interval 26 day), date_sub(curdate(), interval 21 day), '0', 'admin', date_sub(now(), interval 26 day), 'admin', date_sub(now(), interval 21 day)),
(10191, 'PLACE', '涠洲岛', 'DONE', 9.2, '人少景美，值得专程来。', '海岛,自然', '', date_sub(curdate(), interval 21 day), date_sub(curdate(), interval 20 day), '0', 'admin', date_sub(now(), interval 21 day), 'admin', date_sub(now(), interval 20 day)),
(10192, 'PLACE', '亚龙湾', 'DONE', 7.3, '旺季人多，建议错峰。', '海岛,自然', '', date_sub(curdate(), interval 25 day), date_sub(curdate(), interval 24 day), '0', 'admin', date_sub(now(), interval 25 day), 'admin', date_sub(now(), interval 24 day)),
(10193, 'PLACE', '蜈支洲岛', 'DONE', 6.7, '拍照超出片，注意防晒。', '海岛,自然', '', date_sub(curdate(), interval 19 day), date_sub(curdate(), interval 18 day), '0', 'admin', date_sub(now(), interval 19 day), 'admin', date_sub(now(), interval 18 day)),
(10194, 'PLACE', '中央大街', 'DONE', 7.2, '讲解很值，别省这个钱。', '人文,建筑', '', date_sub(curdate(), interval 16 day), date_sub(curdate(), interval 10 day), '0', 'shiguangji', date_sub(now(), interval 16 day), 'shiguangji', date_sub(now(), interval 10 day)),
(10195, 'PLACE', '漠河北极村', 'DONE', 6.7, '住宿选景区里体验更好。', '自然,雪山', '', date_sub(curdate(), interval 14 day), date_sub(curdate(), interval 7 day), '0', 'admin', date_sub(now(), interval 14 day), 'admin', date_sub(now(), interval 7 day)),
(10196, 'PLACE', '那拉提草原', 'WANT', null, '朋友强烈推荐，先 mark。', '草原,自然', '', null, null, '0', 'admin', date_sub(now(), interval 15 day), 'admin', date_sub(now(), interval 15 day)),
(10197, 'PLACE', '大昭寺', 'DONE', 7.3, '交通不太方便，建议自驾。', '人文,古建', '', date_sub(curdate(), interval 8 day), date_sub(curdate(), interval 1 day), '0', 'admin', date_sub(now(), interval 8 day), 'admin', date_sub(now(), interval 1 day)),
(10198, 'PLACE', '珠峰大本营', 'DONE', 6.7, '日出一定要看，早起值得。', '雪山,自然', '', date_sub(curdate(), interval 9 day), date_sub(curdate(), interval 5 day), '0', 'shiguangji', date_sub(now(), interval 9 day), 'shiguangji', date_sub(now(), interval 5 day)),
(10199, 'PLACE', '埃菲尔铁塔', 'DONE', 6.6, '体力消耗大，穿对鞋。', '人文,建筑', '', date_sub(curdate(), interval 1 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 1 day), 'admin', date_sub(now(), interval 0 day)),
(10200, 'PLACE', '清水寺', 'DONE', 7.7, '适合慢悠悠逛一天。', '人文,古建', '', date_sub(curdate(), interval 1 day), date_sub(curdate(), interval 0 day), '0', 'admin', date_sub(now(), interval 1 day), 'admin', date_sub(now(), interval 0 day));

-- 2、电影扩展表（50 条）
insert into sgj_item_movie (item_id, director, actors, genre, region, `language`, release_year, duration_minutes, imdb_id, douban_id) values
(10001, '詹姆斯·卡梅隆', '萨姆·沃辛顿,佐伊·索尔达娜', '科幻', '欧美', '英语', 2009, 162, 'tt0499549', '1652587'),
(10002, '詹姆斯·卡梅隆', '莱昂纳多·迪卡普里奥,凯特·温丝莱特', '爱情', '欧美', '英语', 1997, 194, 'tt0120338', '1291572'),
(10003, '陈凯歌', '张国荣,张丰毅,巩俐', '剧情', '中国大陆', '国语', 1993, 171, 'tt0106332', '1291546'),
(10004, '罗伯特·泽米吉斯', '汤姆·汉克斯,罗宾·怀特', '剧情', '欧美', '英语', 1994, 142, 'tt0109830', '1292720'),
(10005, '吕克·贝松', '让·雷诺,娜塔莉·波特曼', '剧情', '欧美', '英语', 1994, 110, 'tt0110413', '1292869'),
(10006, '罗伯托·贝尼尼', '罗伯托·贝尼尼,尼可莱塔·布拉斯基', '剧情', '欧美', '意大利语', 1997, 116, 'tt0118799', '1292063'),
(10007, '奉俊昊', '宋康昊,李善均,曹如晶', '剧情', '日韩', '韩语', 2019, 132, 'tt6751668', '27010768'),
(10008, '柯克·德米科', '尼古拉斯·凯奇,艾玛·斯通', '动画', '欧美', '英语', 2013, 98, 'tt0496098', '2357687'),
(10009, '李·昂克里奇', '安东尼·冈萨雷斯,盖尔·加西亚·贝纳尔', '动画', '欧美', '英语', 2017, 105, 'tt2380307', '26000695'),
(10010, '宫崎骏', '日高法子,坂本千夏', '动画', '日韩', '日语', 1988, 86, 'tt0096283', '1295255'),
(10011, '宫崎骏', '田中真弓,横泽启子', '动画', '日韩', '日语', 1986, 125, 'tt0092067', '1295502'),
(10012, '宫崎骏', '倍赏千惠子,木村拓哉', '动画', '日韩', '日语', 2004, 119, 'tt0347149', '1304338'),
(10013, '北野武', '北野武,关口雄介', '剧情', '日韩', '日语', 1999, 121, 'tt0170888', '1292266'),
(10014, '奥利维·那卡什', '弗朗索瓦·克鲁塞,奥玛·赛', '剧情', '欧美', '法语', 2011, 112, 'tt1675434', '6786002'),
(10015, '拉库马·希拉尼', '阿米尔·汗,卡琳娜·卡普尔', '喜剧', '印度', '印地语', 2009, 171, 'tt1187043', '3793023'),
(10016, '涅提·蒂瓦里', '阿米尔·汗,法缇玛·萨那·纱卡', '剧情', '印度', '印地语', 2016, 161, 'tt5074352', '26387939'),
(10017, '是枝裕和', '中川雅也,安藤樱,松冈茉优', '剧情', '日韩', '日语', 2018, 121, 'tt8075192', '27622459'),
(10018, '黄东赫', '孔刘,郑有美', '剧情', '日韩', '韩语', 2011, 125, 'tt2039616', '5912992'),
(10019, '李濬益', '薛景求,严智媛', '剧情', '日韩', '韩语', 2013, 122, 'tt2874538', '26744434'),
(10020, '杨宇硕', '宋康昊,金英爱', '剧情', '日韩', '韩语', 2013, 127, 'tt3731908', '25812821'),
(10021, '金秉祐', '河正宇,李璟荣', '悬疑', '日韩', '韩语', 2013, 97, 'tt3554012', '23593001'),
(10022, '丹尼斯·维伦纽瓦', '提莫西·查拉梅,丽贝卡·弗格森', '科幻', '欧美', '英语', 2021, 155, 'tt1160419', '35297269'),
(10023, '史蒂文·斯皮尔伯格', '泰尔·谢里丹,奥利维亚·库克', '科幻', '欧美', '英语', 2018, 140, 'tt1677720', '4920389'),
(10024, '新海诚', '神木隆之介,上白石萌音', '动画', '日韩', '日语', 2016, 106, 'tt5311514', '26683290'),
(10025, '新海诚', '原菜乃华,松村北斗', '动画', '日韩', '日语', 2022, 122, 'tt6587042', '35334532'),
(10026, '孙海鹏', '大昕,大雄', '动画', '中国大陆', '国语', 2021, 104, '', '13024252'),
(10027, '田晓鹏', '苏鑫,王亭文', '动画', '中国大陆', '国语', 2023, 143, '', '26862829'),
(10028, '饺子', '吕艳婷,囧森瑟夫', '动画', '中国大陆', '国语', 2019, 110, '', '26794435'),
(10029, '黄家康,赵霁', '张喆,杨天翔', '动画', '中国大陆', '国语', 2019, 99, '', '30318633'),
(10030, '刘伟强,麦兆辉', '刘德华,梁朝伟', '犯罪', '中国香港', '粤语', 2002, 101, 'tt0338564', '1307914'),
(10031, '王家卫', '梁朝伟,张曼玉', '爱情', '中国香港', '粤语', 2000, 98, 'tt0118694', '1291839'),
(10032, '王家卫', '林青霞,金城武', '剧情', '中国香港', '粤语', 1994, 102, 'tt0109424', '1291818'),
(10033, '刘镇伟', '周星驰,朱茵,吴孟达', '喜剧', '中国香港', '粤语', 1995, 95, 'tt0150344', '1292269'),
(10034, '周星驰', '周星驰,元秋,黄圣依', '喜剧', '中国香港', '粤语', 2004, 99, 'tt0373078', '1291841'),
(10035, '曾国祥', '周冬雨,易烊千玺', '剧情', '中国大陆', '国语', 2019, 135, 'tt9812474', '30166972'),
(10036, '文牧野', '徐峥,王传君', '剧情', '中国大陆', '国语', 2018, 117, 'tt10121764', '26752088'),
(10037, '郭帆', '吴京,屈楚萧,李光洁', '科幻', '中国大陆', '国语', 2019, 125, 'tt7605074', '26266893'),
(10038, '谢君伟,邹靖', '杨天翔,凌振赫', '动画', '中国大陆', '国语', 2023, 168, '', '36167532'),
(10039, '张艺谋', '沈腾,易烊千玺', '悬疑', '中国大陆', '国语', 2023, 159, '', '35999266'),
(10040, '管虎', '王千源,张译,姜武', '剧情', '中国大陆', '国语', 2020, 147, '', '34987652'),
(10041, '李睿珺', '武仁林,海清', '剧情', '中国大陆', '方言', 2022, 133, '', '35465232'),
(10042, '邵艺辉', '徐峥,马伊琍', '喜剧', '中国大陆', '方言', 2021, 117, '', '35334653'),
(10043, '贾玲', '贾玲,雷佳音', '喜剧', '中国大陆', '国语', 2024, 129, '', '36239105'),
(10044, '张艺谋', '雷佳音,马丽', '剧情', '中国大陆', '国语', 2024, 141, '', '36028085'),
(10045, '费德·阿尔瓦雷兹', '卡莉·史派妮,伊莎贝拉·默塞德', '惊悚', '欧美', '英语', 2024, 119, 'tt12299672', '36603374'),
(10046, '克里斯托弗·诺兰', '基里安·墨菲,艾米莉·布朗特', '传记', '欧美', '英语', 2023, 180, 'tt15398776', '35520288'),
(10047, '格蕾塔·葛韦格', '玛格特·罗比,瑞恩·高斯林', '喜剧', '欧美', '英语', 2023, 114, 'tt1517268', '35593192'),
(10048, '华金·多斯·桑托斯', '沙梅克·摩尔,海莉·斯坦菲尔德', '动画', '欧美', '英语', 2023, 140, 'tt9362722', '35593976'),
(10049, '罗伯特·怀斯', '朱莉·安德鲁斯,克里斯托弗·普卢默', '音乐', '欧美', '英语', 1965, 174, 'tt0059742', '1293534'),
(10050, '休·赫德森', '本·克劳斯,伊安·查里森', '体育', '欧美', '英语', 1981, 124, 'tt0082382', '1299891');

-- 3、电视剧扩展表（50 条）
insert into sgj_item_tv (item_id, director, actors, genre, region, `language`, start_year, end_year, season_count, episode_count, imdb_id, douban_id) values
(10051, '大卫·贝尼奥夫,D·B·威斯', '彼得·丁拉基,艾米莉亚·克拉克', '奇幻', '欧美', '英语', 2011, 2019, 8, 73, 'tt0944947', '3016187'),
(10052, '凯文·布赖特', '詹妮弗·安妮斯顿,柯特妮·考克斯', '喜剧', '欧美', '英语', 1994, 2004, 10, 236, 'tt0108778', '9410640'),
(10053, '马克·森德罗维斯基', '吉姆·帕森斯,约翰尼·盖尔克奇', '喜剧', '欧美', '英语', 2007, 2019, 12, 279, 'tt0898266', '3228554'),
(10054, '大卫·芬奇', '凯文·史派西,罗宾·怀特', '剧情', '欧美', '英语', 2013, 2018, 6, 73, 'tt1856010', '2374090'),
(10055, '凯瑞·福永', '马修·麦康纳,伍迪·哈里森', '悬疑', '欧美', '英语', 2014, 2014, 1, 8, 'tt2356777', '25821455'),
(10056, '克雷格·麦辛', '杰瑞德·哈里斯,斯特兰·斯卡斯加德', '剧情', '欧美', '英语', 2019, 2019, 1, 5, 'tt7366338', '27624120'),
(10057, '米歇尔·金', '朱丽安娜·玛格丽丝,乔西·查尔斯', '律政', '欧美', '英语', 2009, 2016, 7, 156, 'tt1442462', '4717910'),
(10058, '大卫·肖', '休·劳瑞,丽莎·艾德尔斯汀', '医疗', '欧美', '英语', 2004, 2012, 8, 177, 'tt0412142', '1866459'),
(10059, '保罗·舒尔灵', '温特沃斯·米勒,多米尼克·珀塞尔', '犯罪', '欧美', '英语', 2005, 2017, 5, 90, 'tt0455275', '1458389'),
(10060, 'J·J·艾布拉姆斯', '马修·福克斯,伊万杰琳·莉莉', '悬疑', '欧美', '英语', 2004, 2010, 6, 121, 'tt0411008', '1458725'),
(10061, '保罗·麦圭根', '本尼迪克特·康伯巴奇,马丁·弗里曼', '悬疑', '英剧', '英语', 2010, 2017, 4, 13, 'tt1475582', '17663262'),
(10062, '朱利安·费罗斯', '休·博纳维尔,米歇尔·多克里', '剧情', '英剧', '英语', 2010, 2015, 6, 52, 'tt1606375', '5420334'),
(10063, '斯蒂芬·戴德利', '克莱尔·芙伊,马特·史密斯', '剧情', '英剧', '英语', 2016, 2016, 1, 10, 'tt4786824', '26890762'),
(10064, '达菲兄弟', '米莉·波比·布朗,芬·伍法德', '科幻', '欧美', '英语', 2016, 2025, 4, 34, 'tt4574334', '26363254'),
(10065, '马克·切利', '泰瑞·海切尔,菲丽西提·霍夫曼', '剧情', '欧美', '英语', 2004, 2012, 8, 180, 'tt0364569', '1458394'),
(10066, '克里斯托弗·劳伊德', '艾德·奥尼尔,索菲亚·维加拉', '喜剧', '欧美', '英语', 2009, 2020, 11, 250, 'tt1442437', '4334811'),
(10067, '迈克尔·帕特里克·金', '凯特·戴琳斯,贝丝·比厄', '喜剧', '欧美', '英语', 2011, 2017, 6, 138, 'tt1845307', '4698915'),
(10068, '布莱恩·富勒', '麦斯·米科尔森,休·丹西', '犯罪', '欧美', '英语', 2013, 2015, 3, 39, 'tt2243973', '20492780'),
(10069, '大卫·西蒙', '多米尼克·威斯特,约翰·道曼', '犯罪', '欧美', '英语', 2002, 2008, 5, 60, 'tt0306414', '3661269'),
(10070, '文斯·吉里根', '鲍勃·奥登科克,蕾亚·塞洪', '犯罪', '欧美', '英语', 2015, 2022, 6, 63, 'tt3032476', '26302414'),
(10071, '弗兰克·德拉邦特', '安德鲁·林肯,诺曼·瑞杜斯', '惊悚', '欧美', '英语', 2010, 2022, 11, 177, 'tt1520211', '4607880'),
(10072, '史蒂夫·佩姆伯顿', '里斯·谢尔史密斯,史蒂夫·佩姆伯顿', '悬疑', '英剧', '英语', 2014, 2024, 8, 55, 'tt3573028', '26295565'),
(10073, '金锡允', '李民基,金智媛', '剧情', '日韩', '韩语', 2022, 2022, 1, 16, 'tt21239852', '35649308'),
(10074, '金元锡', '李帝勋,赵震雄', '悬疑', '日韩', '韩语', 2016, 2016, 1, 16, 'tt5433138', '26743619'),
(10075, '安吉镐', '曹承佑,裴斗娜', '悬疑', '日韩', '韩语', 2017, 2020, 2, 32, 'tt6440676', '27035762'),
(10076, '申源浩', '曹政奭,柳演锡', '医疗', '日韩', '韩语', 2020, 2021, 2, 24, 'tt11525786', '34967299'),
(10077, '申源浩', '高雅拉,郑宇', '剧情', '日韩', '韩语', 2013, 2013, 1, 21, 'tt3551902', '25820547'),
(10078, '李应福', '孔刘,李栋旭', '奇幻', '日韩', '韩语', 2016, 2017, 1, 16, 'tt5870516', '26576933'),
(10079, '李政孝', '玄彬,孙艺珍', '爱情', '日韩', '韩语', 2019, 2020, 1, 16, 'tt10871690', '30437494'),
(10080, '福泽克雄', '堺雅人,上户彩', '职场', '日韩', '日语', 2013, 2020, 2, 20, 'tt3702490', '25663651'),
(10081, '冢原亚由子', '石原里美,井浦新', '医疗', '日韩', '日语', 2018, 2018, 1, 10, 'tt7985390', '27619826'),
(10082, '土井裕泰', '新垣结衣,星野源', '爱情', '日韩', '日语', 2016, 2021, 2, 13, 'tt6136728', '26894405'),
(10083, '坪井敏雄', '安藤樱,夏帆', '剧情', '日韩', '日语', 2023, 2023, 1, 10, 'tt26564405', '36172479'),
(10084, '张开宙', '赵丽颖,冯绍峰', '古装', '中国大陆', '国语', 2018, 2019, 1, 78, '', '30439330'),
(10085, '孔笙', '郭涛,梅婷', '家庭', '中国大陆', '国语', 2014, 2014, 1, 44, '', '25821453'),
(10086, '韦正', '陈赫,娄艺潇', '喜剧', '中国大陆', '国语', 2009, 2020, 5, 96, '', '4070417'),
(10087, '尚敬', '闫妮,沙溢,姚晨', '喜剧', '中国大陆', '国语', 2006, 2006, 1, 81, '', '1857423'),
(10088, '汪俊', '周迅,霍建华', '宫斗', '中国大陆', '国语', 2018, 2018, 1, 87, '', '27077277'),
(10089, '李国立', '刘诗诗,吴奇隆', '古装', '中国大陆', '国语', 2011, 2011, 1, 35, '', '4855835'),
(10090, '辛爽', '秦昊,王景春', '悬疑', '中国大陆', '国语', 2020, 2020, 1, 12, '', '34444898'),
(10091, '陈奕甫', '廖凡,白宇', '悬疑', '中国大陆', '国语', 2020, 2020, 1, 12, '', '34972659'),
(10092, '王伟', '潘粤明,王泷正', '犯罪', '中国大陆', '国语', 2017, 2017, 1, 32, '', '27060061'),
(10093, '徐纪周', '张译,张颂文', '犯罪', '中国大陆', '国语', 2023, 2023, 1, 39, '', '35465232'),
(10094, '杨磊', '张鲁一,于和伟', '科幻', '中国大陆', '国语', 2023, 2023, 1, 30, 'tt19902076', '35465231'),
(10095, '王家卫', '胡歌,马伊琍', '剧情', '中国大陆', '国语', 2023, 2024, 1, 30, '', '35593345'),
(10096, '孔笙', '黄轩,张嘉益', '剧情', '中国大陆', '方言', 2021, 2021, 1, 23, '', '35210415'),
(10097, '张永新', '于和伟,张桐', '历史', '中国大陆', '国语', 2021, 2021, 1, 43, '', '35465233'),
(10098, '李路', '雷佳音,辛柏青', '剧情', '中国大陆', '国语', 2022, 2022, 1, 58, '', '35334632'),
(10099, '辛爽', '范伟,秦昊', '悬疑', '中国大陆', '国语', 2023, 2023, 1, 12, '', '35819500'),
(10100, '孙皓', '张若昀,李沁', '古装', '中国大陆', '国语', 2024, 2024, 1, 36, '', '36167533');

-- 4、书籍扩展表（50 条）
insert into sgj_item_book (item_id, author, publisher, publish_date, isbn, pages, genre) values
(10101, '钱锺书', '人民文学出版社', '1991-02-01', '9787020024759', 359, '小说'),
(10102, '路遥', '北京十月文艺出版社', '2012-03-01', '9787530211250', 1631, '小说'),
(10103, '陈忠实', '人民文学出版社', '1993-06-01', '9787020008997', 680, '小说'),
(10104, '余华', '作家出版社', '2012-08-01', '9787506365437', 191, '小说'),
(10105, '余华', '作家出版社', '2012-09-01', '9787506366670', 260, '小说'),
(10106, '刘慈欣', '重庆出版社', '2008-05-01', '9787536693937', 470, '科幻'),
(10107, '刘慈欣', '四川科学技术出版社', '2016-08-01', '9787536482517', 352, '科幻'),
(10108, '乔治·奥威尔', '北京十月文艺出版社', '2010-04-01', '9787530210291', 304, '小说'),
(10109, '乔治·奥威尔', '上海译文出版社', '2009-06-01', '9787532747191', 118, '小说'),
(10110, '阿道司·赫胥黎', '上海译文出版社', '2017-09-01', '9787532774654', 288, '小说'),
(10111, '菲茨杰拉德', '南海出版公司', '2012-05-01', '9787544253901', 228, '小说'),
(10112, '毛姆', '上海译文出版社', '2014-06-01', '9787532766994', 310, '小说'),
(10113, '毛姆', '上海译文出版社', '2016-05-01', '9787532771899', 344, '小说'),
(10114, '村上春树', '上海译文出版社', '2007-09-01', '9787532742875', 380, '小说'),
(10115, '村上春树', '上海译文出版社', '2018-06-01', '9787532778256', 512, '小说'),
(10116, '东野圭吾', '南海出版公司', '2014-05-01', '9787544270878', 320, '推理'),
(10117, '东野圭吾', '南海出版公司', '2013-01-01', '9787544258609', 558, '推理'),
(10118, '东野圭吾', '南海出版公司', '2014-03-01', '9787544251716', 251, '推理'),
(10119, '紫金陈', '湖南文艺出版社', '2017-08-01', '9787540481582', 352, '推理'),
(10120, '伍绮诗', '江苏凤凰文艺出版社', '2015-08-01', '9787539983045', 290, '小说'),
(10121, '卡勒德·胡赛尼', '上海人民出版社', '2006-05-01', '9787208061644', 362, '小说'),
(10122, '卡勒德·胡赛尼', '上海人民出版社', '2007-09-01', '9787208074392', 428, '小说'),
(10123, '阿尔贝·加缪', '上海译文出版社', '2010-08-01', '9787532750801', 96, '小说'),
(10124, '阿尔贝·加缪', '上海译文出版社', '2013-08-01', '9787532761753', 306, '小说'),
(10125, '赫尔曼·黑塞', '天津人民出版社', '2017-01-01', '9787201112838', 200, '小说'),
(10126, '乔斯坦·贾德', '作家出版社', '2017-08-01', '9787506391974', 552, '哲学'),
(10127, '马可·奥勒留', '中央编译出版社', '2010-12-01', '9787511706195', 260, '哲学'),
(10128, '岸见一郎,古贺史健', '机械工业出版社', '2015-03-01', '9787111495482', 280, '心理'),
(10129, '古斯塔夫·勒庞', '中央编译出版社', '2011-05-01', '9787511706034', 208, '心理'),
(10130, '凯利·麦格尼格尔', '文化发展出版社', '2012-08-01', '9787514208103', 263, '心理'),
(10131, '丹尼尔·卡尼曼', '中信出版社', '2012-07-01', '9787508633558', 424, '心理'),
(10132, '彼得·考夫曼', '上海人民出版社', '2016-08-01', '9787208139916', 544, '经济'),
(10133, '曼昆', '北京大学出版社', '2015-05-01', '9787301256520', 588, '经济'),
(10134, '兰小欢', '上海人民出版社', '2021-08-01', '9787208171237', 352, '经济'),
(10135, '安德斯·艾利克森', '机械工业出版社', '2016-11-01', '9787111551287', 340, '方法论'),
(10136, 'Thomas H. Cormen', '机械工业出版社', '2013-01-01', '9787111407010', 764, '算法'),
(10137, 'Randal E. Bryant', '机械工业出版社', '2016-11-01', '9787111544937', 733, '编程'),
(10138, '马丁·福勒', '人民邮电出版社', '2019-05-01', '9787115214874', 448, '编程'),
(10139, 'Martin Kleppmann', '机械工业出版社', '2021-06-01', '9787111672538', 600, '编程'),
(10140, '黄仁宇', '中华书局', '2007-01-01', '9787101054491', 320, '历史'),
(10141, '尤瓦尔·赫拉利', '中信出版社', '2014-11-01', '9787508647357', 440, '历史'),
(10142, '尤瓦尔·赫拉利', '中信出版社', '2017-01-01', '9787508672069', 440, '历史'),
(10143, '贾雷德·戴蒙德', '上海译文出版社', '2016-07-01', '9787532769131', 528, '历史'),
(10144, '傅高义', '生活·读书·新知三联书店', '2013-01-01', '9787108041531', 764, '历史'),
(10145, '彼得·海斯勒', '上海译文出版社', '2012-01-01', '9787532756728', 410, '纪实'),
(10146, '彼得·海斯勒', '上海译文出版社', '2011-03-01', '9787532752805', 442, '纪实'),
(10147, '三毛', '北京十月文艺出版社', '2011-01-01', '9787530211045', 288, '散文'),
(10148, '龙应台', '广西师范大学出版社', '2014-01-01', '9787549504450', 288, '散文'),
(10149, '杨绛', '生活·读书·新知三联书店', '2012-09-01', '9787108042453', 165, '散文'),
(10150, '沈从文', '北京十月文艺出版社', '2014-07-01', '9787530212653', 144, '小说');

-- 5、地点扩展表（50 条，真实经纬度，可直接用于地图展示/选点）
insert into sgj_item_place (item_id, address, city, province, country, latitude, longitude, best_season, place_category) values
(10151, '山东省泰安市泰山区红门路', '泰安市', '山东省', '中国', 36.256, 117.101, '春秋', '山岳'),
(10152, '安徽省黄山市黄山区汤口镇', '黄山市', '安徽省', '中国', 30.131, 118.1632, '春秋', '山岳'),
(10153, '陕西省渭南市华阴市玉泉路', '渭南市', '陕西省', '中国', 34.475, 110.085, '春秋', '山岳'),
(10154, '四川省乐山市峨眉山市黄湾镇', '乐山市', '四川省', '中国', 29.52, 103.332, '春秋', '山岳'),
(10155, '江西省萍乡市芦溪县武功山乡', '萍乡市', '江西省', '中国', 27.483, 114.183, '春秋', '山岳'),
(10156, '吉林省延边朝鲜族自治州安图县', '延边州', '吉林省', '中国', 42.006, 128.057, '夏', '自然'),
(10157, '青海省海南藏族自治州共和县', '海南州', '青海省', '中国', 36.885, 100.225, '夏', '湖泊'),
(10158, '西藏自治区拉萨市当雄县', '拉萨市', '西藏', '中国', 30.709, 90.625, '夏', '湖泊'),
(10159, '甘肃省酒泉市敦煌市鸣山路', '敦煌市', '甘肃省', '中国', 40.096, 94.669, '春秋', '沙漠'),
(10160, '甘肃省张掖市临泽县倪家营乡', '张掖市', '甘肃省', '中国', 38.973, 100.013, '夏秋', '自然'),
(10161, '新疆维吾尔自治区阿勒泰地区布尔津县', '阿勒泰地区', '新疆', '中国', 48.714, 87.006, '秋', '湖泊'),
(10162, '云南省大理白族自治州大理市', '大理市', '云南省', '中国', 25.757, 100.183, '四季皆宜', '湖泊'),
(10163, '云南省丽江市宁蒗彝族自治县', '丽江市', '云南省', '中国', 27.751, 100.788, '春秋', '湖泊'),
(10164, '广西壮族自治区桂林市象山区', '桂林市', '广西', '中国', 25.274, 110.29, '秋冬', '自然'),
(10165, '贵州省安顺市镇宁布依族苗族自治县', '安顺市', '贵州省', '中国', 25.995, 105.668, '夏', '自然'),
(10166, '贵州省铜仁市江口县太平镇', '铜仁市', '贵州省', '中国', 27.918, 108.759, '春秋', '山岳'),
(10167, '福建省厦门市思明区鼓浪屿', '厦门市', '福建省', '中国', 24.445, 118.067, '春秋', '海岛'),
(10168, '福建省南平市武夷山市武夷街道', '南平市', '福建省', '中国', 27.757, 118.034, '春秋', '山岳'),
(10169, '江西省上饶市婺源县江湾镇篁岭村', '上饶市', '江西省', '中国', 29.364, 118.043, '春秋', '古镇'),
(10170, '安徽省黄山市黟县宏村镇', '黄山市', '安徽省', '中国', 30.007, 117.988, '春秋', '古镇'),
(10171, '浙江省嘉兴市桐乡市乌镇石佛南路', '嘉兴市', '浙江省', '中国', 30.748, 120.488, '春秋', '古镇'),
(10172, '江苏省苏州市昆山市周庄镇全福路', '苏州市', '江苏省', '中国', 31.115, 120.844, '春秋', '古镇'),
(10173, '山西省晋中市平遥县古城内', '晋中市', '山西省', '中国', 37.202, 112.176, '四季皆宜', '古镇'),
(10174, '山西省大同市云冈区武州山南麓', '大同市', '山西省', '中国', 40.112, 113.116, '四季皆宜', '人文'),
(10175, '甘肃省酒泉市敦煌市鸣沙山东麓', '敦煌市', '甘肃省', '中国', 40.036, 94.809, '四季皆宜', '人文'),
(10176, '甘肃省嘉峪关市峪泉镇', '嘉峪关市', '甘肃省', '中国', 39.802, 98.229, '春秋', '人文'),
(10177, '北京市东城区天坛内东里7号', '北京市', '北京市', '中国', 39.882, 116.407, '四季皆宜', '人文'),
(10178, '北京市海淀区新建宫门路19号', '北京市', '北京市', '中国', 39.999, 116.275, '春秋', '人文'),
(10179, '北京市延庆区军都山关沟古道北口', '北京市', '北京市', '中国', 40.359, 116.02, '春秋', '人文'),
(10180, '上海市黄浦区中山东一路', '上海市', '上海市', '中国', 31.24, 121.49, '四季皆宜', '城市地标'),
(10181, '广东省广州市海珠区阅江西路222号', '广州市', '广东省', '中国', 23.107, 113.323, '秋冬', '城市地标'),
(10182, '江苏省南京市玄武区石象路7号', '南京市', '江苏省', '中国', 32.058, 118.847, '春秋', '人文'),
(10183, '江苏省南京市秦淮区贡院西街', '南京市', '江苏省', '中国', 32.021, 118.788, '春秋', '人文'),
(10184, '山东省济南市历下区趵突泉南路1号', '济南市', '山东省', '中国', 36.668, 117.02, '四季皆宜', '自然'),
(10185, '山东省青岛市市南区太平路14号', '青岛市', '山东省', '中国', 36.059, 120.319, '夏', '城市地标'),
(10186, '山东省青岛市崂山区梅岭路29号', '青岛市', '山东省', '中国', 36.156, 120.624, '春夏', '山岳'),
(10187, '湖北省神农架林区红坪镇', '神农架林区', '湖北省', '中国', 31.744, 110.675, '夏', '自然'),
(10188, '湖北省恩施市屯堡乡和板桥镇', '恩施市', '湖北省', '中国', 30.393, 109.187, '春秋', '峡谷'),
(10189, '湖南省湘西土家族苗族自治州凤凰县', '湘西州', '湖南省', '中国', 27.95, 109.6, '春秋', '古镇'),
(10190, '湖南省长沙市岳麓区登高路58号', '长沙市', '湖南省', '中国', 28.185, 112.935, '秋冬', '山岳'),
(10191, '广西壮族自治区北海市海城区涠洲镇', '北海市', '广西', '中国', 21.035, 109.113, '秋冬', '海岛'),
(10192, '海南省三亚市吉阳区亚龙湾国家旅游度假区', '三亚市', '海南省', '中国', 18.229, 109.653, '秋冬', '海岛'),
(10193, '海南省三亚市海棠区林旺镇', '三亚市', '海南省', '中国', 18.314, 109.764, '秋冬', '海岛'),
(10194, '黑龙江省哈尔滨市道里区中央大街', '哈尔滨市', '黑龙江省', '中国', 45.779, 126.618, '冬', '城市地标'),
(10195, '黑龙江省大兴安岭地区漠河市北极村', '漠河市', '黑龙江省', '中国', 53.478, 122.347, '夏冬', '自然'),
(10196, '新疆维吾尔自治区伊犁哈萨克自治州新源县', '伊犁州', '新疆', '中国', 43.379, 83.756, '夏', '草原'),
(10197, '西藏自治区拉萨市城关区八廓西街', '拉萨市', '西藏', '中国', 29.65, 91.132, '四季皆宜', '人文'),
(10198, '西藏自治区日喀则市定日县扎西宗乡', '日喀则市', '西藏', '中国', 28.136, 86.854, '春秋', '雪山'),
(10199, 'Champ de Mars, 5 Avenue Anatole France', '巴黎', 'Île-de-France', '法国', 48.8584, 2.2945, '春秋', '城市地标'),
(10200, '日本京都府京都市东山区清水1丁目', '京都市', '京都府', '日本', 34.9949, 135.785, '春秋', '人文');

-- 6、学习笔记（50 条：34 条关联条目 + 16 条独立，公开/私密/回收站混合）
insert into sgj_note (note_id, item_id, title, content, tags, is_public, del_flag, create_by, create_time, update_by, update_time) values
(10501, 10105, '《许三观卖血记》读书笔记', '# 《许三观卖血记》读书笔记

## 摘抄
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '理财,年度总结', '1', '0', 'admin', date_sub(now(), interval 97 day), 'admin', date_sub(now(), interval 97 day)),
(10502, 10020, '《辩护人》观影笔记', '# 《辩护人》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 重要的东西，用眼睛是看不见的。

## 待办
- [ ] 补同系列的前作', '学习,英语', '0', '0', 'admin', date_sub(now(), interval 105 day), 'admin', date_sub(now(), interval 105 day)),
(10503, 10146, '《寻路中国》读书笔记', '# 《寻路中国》读书笔记

## 摘抄
> 凡是过往，皆为序章。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '编程,摘录', '0', '0', 'admin', date_sub(now(), interval 39 day), 'admin', date_sub(now(), interval 39 day)),
(10504, 10190, '岳麓山 行程记录', '# 岳麓山 行程记录

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '摘录,电影', '1', '0', 'admin', date_sub(now(), interval 119 day), 'admin', date_sub(now(), interval 119 day)),
(10505, 10139, '《设计数据密集型应用》读书笔记', '# 《设计数据密集型应用》读书笔记

## 摘抄
> 把每一天过成自己喜欢的样子。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '技术,摘录', '0', '2', 'admin', date_sub(now(), interval 65 day), 'admin', now()),
(10506, 10119, '《长夜难明》读书笔记', '# 《长夜难明》读书笔记

## 摘抄
> 认识你自己。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '模板,技术', '0', '0', 'admin', date_sub(now(), interval 63 day), 'admin', date_sub(now(), interval 63 day)),
(10507, 10127, '《沉思录》读书笔记', '# 《沉思录》读书笔记

## 摘抄
> 种一棵树最好的时间是十年前，其次是现在。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '旅行,学习', '1', '0', 'admin', date_sub(now(), interval 83 day), 'admin', date_sub(now(), interval 83 day)),
(10508, 10101, '《围城》读书笔记', '# 《围城》读书笔记

## 摘抄
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '英语,随想', '0', '0', 'admin', date_sub(now(), interval 25 day), 'admin', date_sub(now(), interval 25 day)),
(10509, 10049, '《音乐之声》观影笔记', '# 《音乐之声》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 重要的东西，用眼睛是看不见的。

## 待办
- [ ] 补同系列的前作', '编程,读书笔记', '0', '0', 'admin', date_sub(now(), interval 85 day), 'admin', date_sub(now(), interval 85 day)),
(10510, 10087, '《武林外传》追剧笔记', '# 《武林外传》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 凡是过往，皆为序章。

## 待办
- [ ] 补同系列的前作', '英语,灵感', '1', '0', 'admin', date_sub(now(), interval 55 day), 'admin', date_sub(now(), interval 55 day)),
(10511, 10045, '《异形：夺命舰》观影笔记', '# 《异形：夺命舰》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 待办
- [ ] 补同系列的前作', '灵感,工作', '0', '0', 'admin', date_sub(now(), interval 110 day), 'admin', date_sub(now(), interval 110 day)),
(10512, 10175, '莫高窟 行程记录', '# 莫高窟 行程记录

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '美食,观影笔记', '0', '0', 'admin', date_sub(now(), interval 29 day), 'admin', date_sub(now(), interval 29 day)),
(10513, 10144, '《邓小平时代》读书笔记', '# 《邓小平时代》读书笔记

## 摘抄
> 认识你自己。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '计划,学习', '1', '0', 'shiguangji', date_sub(now(), interval 19 day), 'shiguangji', date_sub(now(), interval 19 day)),
(10514, 10011, '《天空之城》观影笔记', '# 《天空之城》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 种一棵树最好的时间是十年前，其次是现在。

## 待办
- [ ] 补同系列的前作', '英语,复盘', '0', '0', 'admin', date_sub(now(), interval 70 day), 'admin', date_sub(now(), interval 70 day)),
(10515, 10095, '《繁花》追剧笔记', '# 《繁花》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 待办
- [ ] 补同系列的前作', '旅行,随想', '0', '0', 'admin', date_sub(now(), interval 25 day), 'admin', date_sub(now(), interval 25 day)),
(10516, 10043, '《热辣滚烫》观影笔记', '# 《热辣滚烫》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 重要的东西，用眼睛是看不见的。

## 待办
- [ ] 补同系列的前作', '电影,摘录', '1', '0', 'admin', date_sub(now(), interval 9 day), 'admin', date_sub(now(), interval 9 day)),
(10517, 10130, '《自控力》读书笔记', '# 《自控力》读书笔记

## 摘抄
> 凡是过往，皆为序章。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '灵感,工作', '0', '0', 'admin', date_sub(now(), interval 48 day), 'admin', date_sub(now(), interval 48 day)),
(10518, 10104, '《活着》读书笔记', '# 《活着》读书笔记

## 摘抄
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '旅行,模板', '0', '0', 'shiguangji', date_sub(now(), interval 140 day), 'shiguangji', date_sub(now(), interval 140 day)),
(10519, 10166, '梵净山 行程记录', '# 梵净山 行程记录

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '理财,英语', '1', '0', 'admin', date_sub(now(), interval 40 day), 'admin', date_sub(now(), interval 40 day)),
(10520, 10171, '乌镇 行程记录', '# 乌镇 行程记录

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '技术,旅行', '0', '0', 'admin', date_sub(now(), interval 45 day), 'admin', date_sub(now(), interval 45 day)),
(10521, 10067, '《破产姐妹》追剧笔记', '# 《破产姐妹》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 种一棵树最好的时间是十年前，其次是现在。

## 待办
- [ ] 补同系列的前作', '年度总结,美食', '0', '0', 'admin', date_sub(now(), interval 112 day), 'admin', date_sub(now(), interval 112 day)),
(10522, 10112, '《月亮与六便士》读书笔记', '# 《月亮与六便士》读书笔记

## 摘抄
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '工作,复盘', '1', '2', 'admin', date_sub(now(), interval 41 day), 'admin', now()),
(10523, 10106, '《三体Ⅱ·黑暗森林》读书笔记', '# 《三体Ⅱ·黑暗森林》读书笔记

## 摘抄
> 重要的东西，用眼睛是看不见的。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '年度总结,摘录', '0', '0', 'admin', date_sub(now(), interval 129 day), 'admin', date_sub(now(), interval 129 day)),
(10524, 10036, '《我不是药神》观影笔记', '# 《我不是药神》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 凡是过往，皆为序章。

## 待办
- [ ] 补同系列的前作', '灵感,电影', '0', '0', 'admin', date_sub(now(), interval 74 day), 'admin', date_sub(now(), interval 74 day)),
(10525, 10027, '《深海》观影笔记', '# 《深海》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 待办
- [ ] 补同系列的前作', '观影笔记,编程', '1', '0', 'admin', date_sub(now(), interval 51 day), 'admin', date_sub(now(), interval 51 day)),
(10526, 10033, '《大话西游之大圣娶亲》观影笔记', '# 《大话西游之大圣娶亲》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 把每一天过成自己喜欢的样子。

## 待办
- [ ] 补同系列的前作', '观影笔记,健身', '0', '0', 'admin', date_sub(now(), interval 52 day), 'admin', date_sub(now(), interval 52 day)),
(10527, 10103, '《白鹿原》读书笔记', '# 《白鹿原》读书笔记

## 摘抄
> 认识你自己。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '理财,英语', '0', '0', 'admin', date_sub(now(), interval 57 day), 'admin', date_sub(now(), interval 57 day)),
(10528, 10056, '《切尔诺贝利》追剧笔记', '# 《切尔诺贝利》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 种一棵树最好的时间是十年前，其次是现在。

## 待办
- [ ] 补同系列的前作', '模板,编程', '1', '0', 'admin', date_sub(now(), interval 100 day), 'admin', date_sub(now(), interval 100 day)),
(10529, 10092, '《白夜追凶》追剧笔记', '# 《白夜追凶》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 待办
- [ ] 补同系列的前作', '工作,复盘', '0', '0', 'admin', date_sub(now(), interval 67 day), 'admin', date_sub(now(), interval 67 day)),
(10530, 10114, '《挪威的森林》读书笔记', '# 《挪威的森林》读书笔记

## 摘抄
> 重要的东西，用眼睛是看不见的。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '健身,编程', '0', '0', 'shiguangji', date_sub(now(), interval 91 day), 'shiguangji', date_sub(now(), interval 91 day)),
(10531, 10128, '《被讨厌的勇气》读书笔记', '# 《被讨厌的勇气》读书笔记

## 摘抄
> 凡是过往，皆为序章。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '旅行,复盘', '1', '0', 'shiguangji', date_sub(now(), interval 86 day), 'shiguangji', date_sub(now(), interval 86 day)),
(10532, 10002, '《泰坦尼克号》观影笔记', '# 《泰坦尼克号》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 待办
- [ ] 补同系列的前作', '复盘,计划', '0', '0', 'admin', date_sub(now(), interval 120 day), 'admin', date_sub(now(), interval 120 day)),
(10533, 10060, '《迷失》追剧笔记', '# 《迷失》追剧笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 把每一天过成自己喜欢的样子。

## 待办
- [ ] 补同系列的前作', '观影笔记,技术', '0', '0', 'admin', date_sub(now(), interval 22 day), 'admin', date_sub(now(), interval 22 day)),
(10534, 10034, '《功夫》观影笔记', '# 《功夫》观影笔记

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 认识你自己。

## 待办
- [ ] 补同系列的前作', '技术,英语', '1', '0', 'shiguangji', date_sub(now(), interval 22 day), 'shiguangji', date_sub(now(), interval 22 day)),
(10535, null, '下季度阅读计划', '# 下季度阅读计划

## 摘抄
> 种一棵树最好的时间是十年前，其次是现在。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '技术,计划', '0', '0', 'admin', date_sub(now(), interval 7 day), 'admin', date_sub(now(), interval 7 day)),
(10536, null, '旅行装备清单', '# 旅行装备清单

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '学习,计划', '0', '0', 'admin', date_sub(now(), interval 13 day), 'admin', date_sub(now(), interval 13 day)),
(10537, null, '本月观影小结', '# 本月观影小结

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 重要的东西，用眼睛是看不见的。

## 待办
- [ ] 补同系列的前作', '工作,健身', '1', '0', 'shiguangji', date_sub(now(), interval 76 day), 'shiguangji', date_sub(now(), interval 76 day)),
(10538, null, '追剧清单整理', '# 追剧清单整理

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 凡是过往，皆为序章。

## 待办
- [ ] 补同系列的前作', '技术,模板', '0', '0', 'admin', date_sub(now(), interval 10 day), 'admin', date_sub(now(), interval 10 day)),
(10539, null, '下季度阅读计划', '# 下季度阅读计划

## 摘抄
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '计划,电影', '0', '2', 'admin', date_sub(now(), interval 97 day), 'admin', now()),
(10540, null, '旅行装备清单', '# 旅行装备清单

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '工作,技术', '1', '0', 'shiguangji', date_sub(now(), interval 114 day), 'shiguangji', date_sub(now(), interval 114 day)),
(10541, null, '本月观影小结', '# 本月观影小结

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 认识你自己。

## 待办
- [ ] 补同系列的前作', '摘录,旅行', '0', '0', 'admin', date_sub(now(), interval 97 day), 'admin', date_sub(now(), interval 97 day)),
(10542, null, '追剧清单整理', '# 追剧清单整理

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 种一棵树最好的时间是十年前，其次是现在。

## 待办
- [ ] 补同系列的前作', '学习,随想', '0', '0', 'admin', date_sub(now(), interval 122 day), 'admin', date_sub(now(), interval 122 day)),
(10543, null, '下季度阅读计划', '# 下季度阅读计划

## 摘抄
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '复盘,健身', '1', '0', 'shiguangji', date_sub(now(), interval 41 day), 'shiguangji', date_sub(now(), interval 41 day)),
(10544, null, '旅行装备清单', '# 旅行装备清单

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '读书笔记,工作', '0', '0', 'admin', date_sub(now(), interval 65 day), 'admin', date_sub(now(), interval 65 day)),
(10545, null, '本月观影小结', '# 本月观影小结

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 凡是过往，皆为序章。

## 待办
- [ ] 补同系列的前作', '摘录,理财', '0', '0', 'admin', date_sub(now(), interval 3 day), 'admin', date_sub(now(), interval 3 day)),
(10546, null, '追剧清单整理', '# 追剧清单整理

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 你必须只有内心丰富，才能摆脱这些生活表面的相似。

## 待办
- [ ] 补同系列的前作', '学习,复盘', '1', '0', 'shiguangji', date_sub(now(), interval 40 day), 'shiguangji', date_sub(now(), interval 40 day)),
(10547, null, '下季度阅读计划', '# 下季度阅读计划

## 摘抄
> 把每一天过成自己喜欢的样子。

## 三点收获
- 方法要在实际工作里用起来
- 和读过的同类书对照着看
- 输出一篇书评加深记忆

## 行动
- [ ] 把第 3 章的清单抄进待办', '电影,理财', '0', '0', 'admin', date_sub(now(), interval 58 day), 'admin', date_sub(now(), interval 58 day)),
(10548, null, '旅行装备清单', '# 旅行装备清单

## 路线
- Day1：下午到，傍晚看日落
- Day2：核心景区一整天

## 花费
- 交通 800 元，住宿 600 元

## 小贴士
- 门票提前一天在公众号预约', '年度总结,摘录', '0', '0', 'admin', date_sub(now(), interval 38 day), 'admin', date_sub(now(), interval 38 day)),
(10549, null, '本月观影小结', '# 本月观影小结

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 种一棵树最好的时间是十年前，其次是现在。

## 待办
- [ ] 补同系列的前作', '科幻,随想', '1', '0', 'shiguangji', date_sub(now(), interval 101 day), 'shiguangji', date_sub(now(), interval 101 day)),
(10550, null, '追剧清单整理', '# 追剧清单整理

## 印象最深的片段
- 开场十分钟就把人抓住
- 结尾反转，回味很久

## 一句话
> 我们一路奋战，不是为了改变世界，而是为了不让世界改变我们。

## 待办
- [ ] 补同系列的前作', '工作,随想', '0', '0', 'admin', date_sub(now(), interval 40 day), 'admin', date_sub(now(), interval 40 day));

-- 7、标签表（50 条：每模块 10 条，不与已有 70 条重名）
insert into sgj_tag (tag_id, module, tag_name, sort, create_by, create_time) values
(10601, 'MOVIE', '爱情', 14, 'admin', now()),
(10602, 'MOVIE', '动作', 15, 'admin', now()),
(10603, 'MOVIE', '犯罪', 16, 'admin', now()),
(10604, 'MOVIE', '惊悚', 17, 'admin', now()),
(10605, 'MOVIE', '战争', 18, 'admin', now()),
(10606, 'MOVIE', '音乐', 19, 'admin', now()),
(10607, 'MOVIE', '体育', 20, 'admin', now()),
(10608, 'MOVIE', '赛博朋克', 21, 'admin', now()),
(10609, 'MOVIE', '传记', 22, 'admin', now()),
(10610, 'MOVIE', '经典', 23, 'admin', now()),
(10611, 'TV', '喜剧', 13, 'admin', now()),
(10612, 'TV', '律政', 14, 'admin', now()),
(10613, 'TV', '医疗', 15, 'admin', now()),
(10614, 'TV', '奇幻', 16, 'admin', now()),
(10615, 'TV', '爱情', 17, 'admin', now()),
(10616, 'TV', '日剧', 18, 'admin', now()),
(10617, 'TV', '韩剧', 19, 'admin', now()),
(10618, 'TV', '历史', 20, 'admin', now()),
(10619, 'TV', '职场', 21, 'admin', now()),
(10620, 'TV', '群像', 22, 'admin', now()),
(10621, 'TV', '经典', 23, 'admin', now()),
(10622, 'BOOK', '小说', 23, 'admin', now()),
(10623, 'BOOK', '推理', 24, 'admin', now()),
(10624, 'BOOK', '哲学', 25, 'admin', now()),
(10625, 'BOOK', '心理', 26, 'admin', now()),
(10626, 'BOOK', '经济', 27, 'admin', now()),
(10627, 'BOOK', '传记', 28, 'admin', now()),
(10628, 'BOOK', '随笔', 29, 'admin', now()),
(10629, 'BOOK', '纪实', 30, 'admin', now()),
(10630, 'BOOK', '科幻', 31, 'admin', now()),
(10631, 'BOOK', '效率', 32, 'admin', now()),
(10632, 'PLACE', '古镇', 13, 'admin', now()),
(10633, 'PLACE', '海岛', 14, 'admin', now()),
(10634, 'PLACE', '草原', 15, 'admin', now()),
(10635, 'PLACE', '峡谷', 16, 'admin', now()),
(10636, 'PLACE', '沙漠', 17, 'admin', now()),
(10637, 'PLACE', '温泉', 18, 'admin', now()),
(10638, 'PLACE', '博物馆', 19, 'admin', now()),
(10639, 'PLACE', '教堂', 20, 'admin', now()),
(10640, 'PLACE', '湿地', 21, 'admin', now()),
(10641, 'PLACE', '城市地标', 22, 'admin', now()),
(10642, 'NOTE', '随想', 12, 'admin', now()),
(10643, 'NOTE', '摘录', 13, 'admin', now()),
(10644, 'NOTE', '年度总结', 14, 'admin', now()),
(10645, 'NOTE', '技术', 15, 'admin', now()),
(10646, 'NOTE', '英语', 16, 'admin', now()),
(10647, 'NOTE', '健身', 17, 'admin', now()),
(10648, 'NOTE', '美食', 18, 'admin', now()),
(10649, 'NOTE', '理财', 19, 'admin', now()),
(10650, 'NOTE', '灵感', 20, 'admin', now()),
(10651, 'NOTE', '杂谈', 21, 'admin', now());
