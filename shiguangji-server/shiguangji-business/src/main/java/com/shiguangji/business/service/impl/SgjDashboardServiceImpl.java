package com.shiguangji.business.service.impl;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.business.mapper.SgjDashboardMapper;
import com.shiguangji.business.service.ISgjDashboardService;

/**
 * 后台首页统计 服务层实现
 *
 * @author shiguangji
 */
@Service
public class SgjDashboardServiceImpl implements ISgjDashboardService
{
    @Autowired
    private SgjDashboardMapper sgjDashboardMapper;

    @Override
    public Map<String, Object> getStatistics()
    {
        Map<String, Object> result = new HashMap<>();

        // 1. 各类型/状态数量
        List<Map<String, Object>> typeStats = sgjDashboardMapper.selectItemTypeStats(null);
        Map<String, Long> total = new HashMap<>();
        Map<String, Map<String, Long>> status = new HashMap<>();
        for (Map<String, Object> row : typeStats)
        {
            String type = (String) row.get("itemType");
            String st = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            total.merge(type, cnt, Long::sum);
            status.computeIfAbsent(type, k -> new HashMap<>()).merge(st, cnt, Long::sum);
        }
        long itemTotal = total.values().stream().mapToLong(Long::longValue).sum();
        result.put("total", total);
        result.put("status", status);
        result.put("itemTotal", itemTotal);

        // 2. 笔记统计（后台管理端不按公开状态过滤）
        long noteTotal = sgjDashboardMapper.selectNoteTotal(null, false);
        long noteMonth = sgjDashboardMapper.selectNoteMonthCount();
        result.put("noteTotal", noteTotal);
        result.put("noteMonth", noteMonth);
        result.put("allTotal", itemTotal + noteTotal);

        // 3. 近6个月趋势
        result.put("trend", buildTrend());

        // 4. 最近数据
        result.put("recentItems", sgjDashboardMapper.selectRecentItems(10));
        result.put("todoItems", sgjDashboardMapper.selectTodoItems(10, null));
        result.put("recentNotes", sgjDashboardMapper.selectRecentNotes(5, null, false));

        return result;
    }

    private List<Map<String, Object>> buildTrend()
    {
        List<Map<String, Object>> itemTrend = sgjDashboardMapper.selectItemMonthTrend();
        List<Map<String, Object>> noteTrend = sgjDashboardMapper.selectNoteMonthTrend();

        Map<String, Map<String, Object>> trendMap = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--)
        {
            YearMonth ym = current.minusMonths(i);
            String month = ym.toString();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("movie", 0L);
            item.put("tv", 0L);
            item.put("book", 0L);
            item.put("place", 0L);
            item.put("total", 0L);
            item.put("note", 0L);
            trendMap.put(month, item);
        }

        for (Map<String, Object> row : itemTrend)
        {
            String month = (String) row.get("month");
            Map<String, Object> target = trendMap.get(month);
            if (target != null)
            {
                putLong(target, "movie", row.get("movie"));
                putLong(target, "tv", row.get("tv"));
                putLong(target, "book", row.get("book"));
                putLong(target, "place", row.get("place"));
                putLong(target, "total", row.get("total"));
            }
        }

        for (Map<String, Object> row : noteTrend)
        {
            String month = (String) row.get("month");
            Map<String, Object> target = trendMap.get(month);
            if (target != null)
            {
                putLong(target, "note", row.get("cnt"));
            }
        }

        return new ArrayList<>(trendMap.values());
    }

    private void putLong(Map<String, Object> map, String key, Object value)
    {
        if (value != null)
        {
            map.put(key, ((Number) value).longValue());
        }
    }
}