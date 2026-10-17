package com.shiguangji.business.service;

import java.util.Map;

/**
 * 0.0.4 年度回顾 服务层
 *
 * @author shiguangji
 */
public interface IAppReviewService
{
    /**
     * 某一年的全部回顾数据（年份列表、汇总、地点、评分榜、标签分布）
     *
     * @param createBy 创建者过滤（null/空 表示不限制；经 AppScopeHelper.resolveCreateBy 解析）
     * @param year     自然年
     * @return 见 docs/design/review-design.md §1 契约
     */
    Map<String, Object> getYearData(int year, String createBy);
}
