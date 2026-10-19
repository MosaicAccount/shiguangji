package com.shiguangji.business.service;

import java.util.Map;

/**
 * 全站聚合检索 服务层（0.0.5 FR-005-5）
 *
 * @author shiguangji
 */
public interface IAppSearchService
{
    /**
     * 跨「条目 + 笔记」聚合检索：影单（电影+电视剧混排）/ 书单 / 足迹 / 笔记 四组，
     * 每组前 10 条 + 命中总数；空组不下发。
     *
     * @param keyword 关键词（已 trim、长度 >= 2）
     * @param createBy 可见范围（AppScopeHelper.resolveCreateBy()，null＝不限）
     * @param anonymous 是否匿名访客（匿名仅公开笔记）
     * @return data 结构：{ keyword, groups: [ { key, total, rows } ] }
     */
    public Map<String, Object> search(String keyword, String createBy, boolean anonymous);
}
