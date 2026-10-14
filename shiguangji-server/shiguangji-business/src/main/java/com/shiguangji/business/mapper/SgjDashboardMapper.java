package com.shiguangji.business.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjNote;

/**
 * 后台首页统计 数据层
 *
 * @author shiguangji
 */
public interface SgjDashboardMapper
{
    /**
     * 查询各类型各状态数量
     *
     * @param createBy 创建者过滤（null/空 表示不限制）
     * @return 统计集合
     */
    public List<Map<String, Object>> selectItemTypeStats(@Param("createBy") String createBy);

    /**
     * 查询近6个月条目新增趋势
     *
     * @return 趋势集合
     */
    public List<Map<String, Object>> selectItemMonthTrend();

    /**
     * 查询近6个月笔记新增趋势
     *
     * @return 趋势集合
     */
    public List<Map<String, Object>> selectNoteMonthTrend();

    /**
     * 查询笔记总数
     *
     * @param createBy   创建者过滤（null/空 表示不限制）
     * @param publicOnly 是否仅统计公开笔记
     * @return 笔记总数
     */
    public long selectNoteTotal(@Param("createBy") String createBy, @Param("publicOnly") boolean publicOnly);

    /**
     * 查询本月笔记新增数
     *
     * @return 本月笔记新增数
     */
    public long selectNoteMonthCount();

    /**
     * 查询最近新增条目
     *
     * @param limit 数量限制
     * @return 条目集合
     */
    public List<SgjItem> selectRecentItems(@Param("limit") int limit);

    /**
     * 查询心愿单条目（想看/想读/想去 + 在看/在读；0.0.3 纳入 DOING，D-8）
     *
     * <p>排序：有计划日期的按日期升序在前，无日期的按添加时间兜底置后。</p>
     *
     * @param limit 数量限制
     * @param createBy 创建者过滤（null/空 表示不限制）
     * @return 条目集合
     */
    public List<SgjItem> selectTodoItems(@Param("limit") int limit, @Param("createBy") String createBy);

    /**
     * 分页查询已完成时间线条目（看过/读过/去过）
     *
     * @param limit    每页数量
     * @param offset   偏移量（(pageNum-1)*pageSize）
     * @param createBy 创建者过滤（null/空 表示不限制）
     * @return 条目集合
     */
    public List<SgjItem> selectTimeline(@Param("limit") int limit, @Param("offset") int offset,
            @Param("createBy") String createBy);

    /**
     * 统计已完成时间线条目总数
     *
     * @param createBy 创建者过滤（null/空 表示不限制）
     * @return 总数
     */
    public long countTimeline(@Param("createBy") String createBy);

    /**
     * 查询最近笔记
     *
     * @param limit      数量限制
     * @param createBy   创建者过滤（null/空 表示不限制）
     * @param publicOnly 是否仅返回公开笔记
     * @return 笔记集合
     */
    public List<SgjNote> selectRecentNotes(@Param("limit") int limit, @Param("createBy") String createBy,
            @Param("publicOnly") boolean publicOnly);
}