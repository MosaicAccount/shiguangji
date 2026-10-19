package com.shiguangji.business.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.mapper.SgjItemMapper;
import com.shiguangji.business.service.IAppSearchService;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.business.utils.SgjNoteLinkUtils;

/**
 * 全站聚合检索 服务层实现（0.0.5 FR-005-5）。
 *
 * <p>口径复用（架构总原则 2）：条目走 {@link SgjItemMapper#selectSearchItems}（标题 LIKE、
 * update_time 倒序，与列表页同源）；笔记走 {@link ISgjNoteService#selectSgjNoteFrontList}
 * （标题+正文全文检索、0 命中 like 兜底、摘要与命中数全复用）。搜索结果只用于展示与跳转，
 * 条目与笔记的私人字段（comment / remark）一律不下发，因此无访客脱敏分支。</p>
 *
 * <p>ponytail: 每组固定取前 10 条（PageHelper limit），不做分页与组内展开；
 * total 如实下发由前端组头展示，关键词更具体即可收窄。</p>
 *
 * @author shiguangji
 */
@Service
public class AppSearchServiceImpl implements IAppSearchService
{
    /** 每组下发的最大条数（需求 §3.3.5：每组最多 10 条，不分页） */
    private static final int GROUP_LIMIT = 10;

    @Autowired
    private SgjItemMapper sgjItemMapper;

    @Autowired
    private ISgjNoteService sgjNoteService;

    @Override
    public Map<String, Object> search(String keyword, String createBy, boolean anonymous)
    {
        List<Map<String, Object>> groups = new ArrayList<>();
        addIfNotEmpty(groups, itemGroup("movie", Arrays.asList("MOVIE", "TV"), keyword, createBy));
        addIfNotEmpty(groups, itemGroup("book", Collections.singletonList("BOOK"), keyword, createBy));
        addIfNotEmpty(groups, itemGroup("place", Collections.singletonList("PLACE"), keyword, createBy));
        addIfNotEmpty(groups, noteGroup(keyword, createBy, anonymous));

        Map<String, Object> data = new HashMap<>();
        data.put("keyword", keyword);
        data.put("groups", groups);
        return data;
    }

    /** 条目组：影单为 MOVIE+TV 混排（type in 一把查，天然混排），私人字段置空 */
    private Map<String, Object> itemGroup(String key, List<String> itemTypes, String keyword, String createBy)
    {
        PageMethod.startPage(1, GROUP_LIMIT);
        List<SgjItem> items = sgjItemMapper.selectSearchItems(itemTypes, SgjNoteLinkUtils.escapeLike(keyword), createBy);
        long total = ((Page<?>) items).getTotal();
        for (SgjItem item : items)
        {
            // 搜索结果不下发私人短评与备注（对任何查看者一致，故无脱敏分叉）
            item.setComment(null);
            item.setRemark(null);
        }
        return group(key, total, items);
    }

    /** 笔记组：复用前台笔记列表（全文检索 + like 兜底 + 摘要 + 命中数），匿名仅公开 */
    private Map<String, Object> noteGroup(String keyword, String createBy, boolean anonymous)
    {
        SgjNote query = new SgjNote();
        query.setKeyword(keyword);
        query.setCreateBy(createBy);
        if (anonymous)
        {
            query.setIsPublic("1");
        }
        PageMethod.startPage(1, GROUP_LIMIT);
        List<SgjNote> notes = sgjNoteService.selectSgjNoteFrontList(query);
        long total = ((Page<?>) notes).getTotal();
        for (SgjNote note : notes)
        {
            note.setRemark(null);
        }
        return group("note", total, notes);
    }

    private Map<String, Object> group(String key, long total, List<?> rows)
    {
        Map<String, Object> group = new HashMap<>();
        group.put("key", key);
        group.put("total", total);
        group.put("rows", rows);
        return group;
    }

    /** 空组不下发（需求 §3.3.5：空组不显示），由本方法统一收口 */
    private void addIfNotEmpty(List<Map<String, Object>> groups, Map<String, Object> group)
    {
        if (((Long) group.get("total")) > 0)
        {
            groups.add(group);
        }
    }
}
