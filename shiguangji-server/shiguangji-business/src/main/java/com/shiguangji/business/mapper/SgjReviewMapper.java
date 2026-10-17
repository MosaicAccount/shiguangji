package com.shiguangji.business.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.shiguangji.business.domain.SgjItem;

/**
 * 0.0.4 年度回顾 数据层（只读聚合，不改任何既有 mapper）
 *
 * <p>where 前缀与 SgjDashboardMapper.selectTimeline 逐字对齐：
 * del_flag='0' + status='DONE' + finish_date is not null + create_by 动态。
 * 条目按 finish_date 归年、笔记按 create_time 归年。</p>
 *
 * @author shiguangji
 */
public interface SgjReviewMapper
{
    /** 有数据的条目年份（finish_date 归年，升序去重） */
    List<Integer> selectItemYears(@Param("createBy") String createBy);

    /** 有数据的笔记年份（create_time 归年，升序去重） */
    List<Integer> selectNoteYears(@Param("createBy") String createBy);

    /** 该年各类型 DONE 条目数：item_type as itemType, cnt */
    List<Map<String, Object>> selectYearTypeStats(@Param("year") int year, @Param("createBy") String createBy);

    /** 该年笔记数（create_time 归年） */
    long selectYearNoteCount(@Param("year") int year, @Param("createBy") String createBy);

    /** 该年 DONE 地点（含 place 联查字段与照片数），按 finish_date 升序 */
    List<SgjItem> selectYearPlaces(@Param("year") int year, @Param("createBy") String createBy);

    /** 该年若干类型的评分榜（rating 非空，rating desc / finish_date desc），前 limit 条 */
    List<SgjItem> selectYearTopRated(@Param("year") int year, @Param("itemTypes") List<String> itemTypes,
            @Param("limit") int limit, @Param("createBy") String createBy);

    /** 该年已评分条目数（rating 非空）：item_type as itemType, cnt */
    List<Map<String, Object>> selectYearRatedCounts(@Param("year") int year, @Param("createBy") String createBy);

    /** 该年 DONE 条目的原始 tags 串（Java 侧拆分聚合） */
    List<String> selectYearTags(@Param("year") int year, @Param("createBy") String createBy);
}
