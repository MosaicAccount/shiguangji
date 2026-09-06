package com.shiguangji.web.controller.business;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.service.IAppHomeService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 前台首页 操作处理
 *
 * 只读接口，保持匿名访问
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/home")
public class AppHomeController extends BaseController
{
    /**
     * 时间线分页每页条数上限
     */
    private static final int MAX_TIMELINE_PAGE_SIZE = 50;

    @Autowired
    private IAppHomeService appHomeService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    /**
     * 获取前台首页数据（按可见范围过滤，）
     *
     * ：匿名访客的笔记相关数据仅含公开笔记（is_public='1'）
     */
    @Anonymous
    @GetMapping("/index")
    public AjaxResult index()
    {
        boolean guestMode = appScopeHelper.isAnonymous();
        Map<String, Object> data = appHomeService.getHomeData(appScopeHelper.resolveCreateBy(), guestMode);
        if (guestMode)
        {
            // 访客响应脱敏：时间线/心愿单不返回 remark/comment，最近笔记不返回私人备注
            appScopeHelper.maskItemsForGuest((List<SgjItem>) data.get("timeline"));
            appScopeHelper.maskItemsForGuest((List<SgjItem>) data.get("wishlist"));
            appScopeHelper.maskNotesForGuest((List<SgjNote>) data.get("recentNotes"));
        }
        return success(data);
    }

    /**
     * 分页获取已完成时间线（我的时光“加载更多”）
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页条数（默认 10，上限 50）
     */
    @Anonymous
    @GetMapping("/timeline")
    public AjaxResult timeline(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        if (pageSize < 1)
        {
            pageSize = 1;
        }
        else if (pageSize > MAX_TIMELINE_PAGE_SIZE)
        {
            pageSize = MAX_TIMELINE_PAGE_SIZE;
        }
        boolean guestMode = appScopeHelper.isAnonymous();
        Map<String, Object> data = appHomeService.getTimelinePage(appScopeHelper.resolveCreateBy(), pageNum, pageSize);
        if (guestMode)
        {
            appScopeHelper.maskItemsForGuest((List<SgjItem>) data.get("rows"));
        }
        AjaxResult result = success(data.get("rows"));
        result.put("total", data.get("total"));
        return result;
    }
}