package com.shiguangji.business.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.business.mapper.SgjDashboardMapper;
import com.shiguangji.business.service.IAppHomeService;

/**
 * 前台首页 服务层实现
 *
 * @author shiguangji
 */
@Service
public class AppHomeServiceImpl implements IAppHomeService
{
    /**
     * 首页时间线首屏条数（更多通过 /app/home/timeline 分页加载）
     */
    public static final int TIMELINE_FIRST_PAGE_SIZE = 10;

    @Autowired
    private SgjDashboardMapper sgjDashboardMapper;

    @Override
    public Map<String, Object> getHomeData(String createBy, boolean publicOnly)
    {
        Map<String, Object> result = new HashMap<>();

        // 汇总数据（publicOnly 时笔记数仅统计公开笔记，）
        result.put("summary", buildSummary(createBy, publicOnly));

        // 已完成时间线（仅首屏一页，避免一次拉全量；总数供前端“加载更多”）
        result.put("timeline", sgjDashboardMapper.selectTimeline(TIMELINE_FIRST_PAGE_SIZE, 0, createBy));
        result.put("timelineTotal", sgjDashboardMapper.countTimeline(createBy));

        // 心愿单
        result.put("wishlist", sgjDashboardMapper.selectTodoItems(20, createBy));

        // 最近笔记（publicOnly 时仅返回公开笔记，）
        result.put("recentNotes", sgjDashboardMapper.selectRecentNotes(5, createBy, publicOnly));

        return result;
    }

    @Override
    public Map<String, Object> getTimelinePage(String createBy, int pageNum, int pageSize)
    {
        int offset = (Math.max(pageNum, 1) - 1) * pageSize;
        Map<String, Object> result = new HashMap<>();
        result.put("rows", sgjDashboardMapper.selectTimeline(pageSize, offset, createBy));
        result.put("total", sgjDashboardMapper.countTimeline(createBy));
        return result;
    }

    private Map<String, Object> buildSummary(String createBy, boolean publicOnly)
    {
        List<Map<String, Object>> typeStats = sgjDashboardMapper.selectItemTypeStats(createBy);
        long noteTotal = sgjDashboardMapper.selectNoteTotal(createBy, publicOnly);

        Map<String, Long> total = new HashMap<>();
        Map<String, Long> done = new HashMap<>();
        Map<String, Long> wish = new HashMap<>();
        long itemTotal = 0;
        long doneTotal = 0;
        long wishTotal = 0;

        for (Map<String, Object> row : typeStats)
        {
            String type = (String) row.get("itemType");
            String status = (String) row.get("status");
            long cnt = ((Number) row.get("cnt")).longValue();
            total.merge(type, cnt, Long::sum);
            itemTotal += cnt;
            if ("DONE".equals(status))
            {
                done.merge(type, cnt, Long::sum);
                doneTotal += cnt;
            }
            else
            {
                wish.merge(type, cnt, Long::sum);
                wishTotal += cnt;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("done", done);
        summary.put("wish", wish);
        summary.put("itemTotal", itemTotal);
        summary.put("doneTotal", doneTotal);
        summary.put("wishTotal", wishTotal);
        summary.put("noteTotal", noteTotal);
        summary.put("allTotal", itemTotal + noteTotal);
        return summary;
    }
}