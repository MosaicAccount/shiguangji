package com.shiguangji.business.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.mapper.SgjReviewMapper;
import com.shiguangji.business.service.IAppReviewService;
import com.shiguangji.common.utils.DateUtils;

/**
 * 0.0.4 年度回顾 服务实现（纯只读聚合）
 *
 * <p>聚合键统一小写（movie / book / place），与前端契约一致。
 * 标签拆分是独立静态方法，便于脱离数据库单测（空串 / 空格 / 归并 / 排序）。</p>
 *
 * @author shiguangji
 */
@Service
public class AppReviewServiceImpl implements IAppReviewService
{
    /** 评分榜每栏条数（影视 / 书籍各取前 5） */
    private static final int TOP_N = 5;

    @Autowired
    private SgjReviewMapper reviewMapper;

    @Override
    public Map<String, Object> getYearData(int year, String createBy)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("year", year);
        result.put("years", resolveYears(createBy));
        result.put("summary", buildSummary(year, createBy));
        result.put("places", buildPlaces(year, createBy));
        result.put("topRated", buildTopRated(year, createBy));
        result.put("tags", aggregateTags(reviewMapper.selectYearTags(year, createBy)));
        return result;
    }

    /** 有数据的年份：条目 finish_date 年份 ∪ 笔记 create_time 年份，降序 */
    private List<Integer> resolveYears(String createBy)
    {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        reviewMapper.selectItemYears(createBy).forEach(years::add);
        reviewMapper.selectNoteYears(createBy).forEach(years::add);
        return new ArrayList<>(years);
    }

    private Map<String, Object> buildSummary(int year, String createBy)
    {
        Map<String, Long> typeCount = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : reviewMapper.selectYearTypeStats(year, createBy))
        {
            String type = cardType((String) row.get("itemType"));
            long cnt = ((Number) row.get("cnt")).longValue();
            typeCount.merge(type, cnt, Long::sum);
            total += cnt;
        }
        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("movie", typeCount.getOrDefault("movie", 0L));
        summary.put("book", typeCount.getOrDefault("book", 0L));
        summary.put("place", typeCount.getOrDefault("place", 0L));
        summary.put("note", reviewMapper.selectYearNoteCount(year, createBy));
        return summary;
    }

    /** 地点转契约结构：日期统一 yyyy-MM-dd，无坐标字段原样下发（前端在图内交代） */
    private List<Map<String, Object>> buildPlaces(int year, String createBy)
    {
        List<Map<String, Object>> places = new ArrayList<>();
        for (SgjItem p : reviewMapper.selectYearPlaces(year, createBy))
        {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", p.getItemId());
            m.put("title", p.getTitle());
            m.put("province", p.getProvince());
            m.put("country", p.getCountry());
            m.put("latitude", p.getLatitude());
            m.put("longitude", p.getLongitude());
            m.put("finishDate", dateStr(p.getFinishDate()));
            m.put("rating", p.getRating());
            m.put("comment", p.getComment());
            m.put("tags", p.getTags());
            m.put("coverUrl", p.getCoverUrl());
            m.put("photoCount", p.getPhotoCount() == null ? 0 : p.getPhotoCount());
            places.add(m);
        }
        return places;
    }

    private Map<String, Object> buildTopRated(int year, String createBy)
    {
        Map<String, Long> ratedCount = new HashMap<>();
        for (Map<String, Object> row : reviewMapper.selectYearRatedCounts(year, createBy))
        {
            ratedCount.merge(cardType((String) row.get("itemType")),
                    ((Number) row.get("cnt")).longValue(), Long::sum);
        }
        Map<String, Object> ratedCountOut = new HashMap<>();
        ratedCountOut.put("movie", ratedCount.getOrDefault("movie", 0L));
        ratedCountOut.put("book", ratedCount.getOrDefault("book", 0L));

        Map<String, Object> topRated = new HashMap<>();
        topRated.put("movie", topList(year, Arrays.asList("MOVIE", "TV"), createBy));
        topRated.put("book", topList(year, Collections.singletonList("BOOK"), createBy));
        topRated.put("ratedCount", ratedCountOut);
        return topRated;
    }

    private List<Map<String, Object>> topList(int year, List<String> itemTypes, String createBy)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (SgjItem it : reviewMapper.selectYearTopRated(year, itemTypes, TOP_N, createBy))
        {
            Map<String, Object> m = new HashMap<>();
            m.put("itemId", it.getItemId());
            m.put("title", it.getTitle());
            m.put("rating", it.getRating());
            m.put("finishDate", dateStr(it.getFinishDate()));
            m.put("coverUrl", it.getCoverUrl());
            list.add(m);
        }
        return list;
    }

    /**
     * 标签拆分聚合：英文逗号拆分 → 去首尾空格 → 滤空串 → 同名归并计数，
     * 按 count 降序、同 count 按名字字典序（LinkedHashMap 保序下发）。
     */
    static List<Map<String, Object>> aggregateTags(List<String> rawList)
    {
        Map<String, Long> counts = new HashMap<>();
        for (String raw : rawList)
        {
            if (raw == null)
            {
                continue;
            }
            for (String tag : raw.split(","))
            {
                String name = tag.trim();
                if (!name.isEmpty())
                {
                    counts.merge(name, 1L, Long::sum);
                }
            }
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            int byCount = Long.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : entries)
        {
            Map<String, Object> m = new HashMap<>();
            m.put("name", e.getKey());
            m.put("count", e.getValue());
            list.add(m);
        }
        return list;
    }

    /** 契约键：TV 并入 movie（页面统计卡口径「看过 · 电影/剧集」，评分榜同） */
    private static String cardType(String itemType)
    {
        return "TV".equals(itemType) ? "movie" : (itemType == null ? "" : itemType.toLowerCase());
    }

    private static String dateStr(Date d)
    {
        return d == null ? null : DateUtils.parseDateToStr("yyyy-MM-dd", d);
    }
}
