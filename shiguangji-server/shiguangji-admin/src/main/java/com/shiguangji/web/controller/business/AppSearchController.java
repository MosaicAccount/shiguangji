package com.shiguangji.web.controller.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.service.IAppSearchService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 前台全站检索 操作处理（0.0.5 FR-005-5）
 *
 * <p>跨「条目 + 笔记」聚合：条目按标题匹配、笔记按标题+正文全文检索；
 * 可见性复用 {@link AppScopeHelper} 口径（匿名仅 publicOwner 的条目与公开笔记），
 * 与其他前台读接口同源，不另写过滤。</p>
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/search")
public class AppSearchController extends BaseController
{
    @Autowired
    private IAppSearchService appSearchService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    /**
     * 全站聚合检索：影单（电影+电视剧混排）/ 书单 / 足迹 / 笔记 四组，
     * 每组前 10 条 + 总数，空组不下发；关键词至少 2 个字（ngram 按 2 字切词，单字搜不到）。
     */
    @Anonymous
    @GetMapping
    public AjaxResult search(@RequestParam String keyword)
    {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < 2)
        {
            return error("关键词至少 2 个字");
        }
        return success(appSearchService.search(trimmed,
                appScopeHelper.resolveCreateBy(), appScopeHelper.isAnonymous()));
    }
}
