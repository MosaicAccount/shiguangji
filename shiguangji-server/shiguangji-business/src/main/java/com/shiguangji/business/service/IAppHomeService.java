package com.shiguangji.business.service;

import java.util.Map;

/**
 * 前台首页 服务层
 *
 * @author shiguangji
 */
public interface IAppHomeService
{
    /**
     * 获取前台首页数据
     *
     * @param createBy   创建者过滤（null 表示不限制； 用户数据隔离）
     * @param publicOnly 是否仅返回公开笔记（匿名访客为 true，仅可见 is_public='1' 的笔记）
     * @return 首页数据
     */
    public Map<String, Object> getHomeData(String createBy, boolean publicOnly);

    /**
     * 分页获取已完成时间线（我的时光）
     *
     * @param createBy   创建者过滤（null 表示不限制； 用户数据隔离）
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页数量
     * @return rows 条目集合 / total 总数
     */
    public Map<String, Object> getTimelinePage(String createBy, int pageNum, int pageSize);
}
