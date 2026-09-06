package com.shiguangji.business.service;

import java.util.Map;

/**
 * 后台首页统计 服务层
 *
 * @author shiguangji
 */
public interface ISgjDashboardService
{
    /**
     * 获取后台首页统计数据
     *
     * @return 统计结果
     */
    public Map<String, Object> getStatistics();
}