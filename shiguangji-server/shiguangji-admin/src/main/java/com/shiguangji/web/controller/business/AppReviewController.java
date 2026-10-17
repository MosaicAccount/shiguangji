package com.shiguangji.web.controller.business;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.service.IAppReviewService;
import com.shiguangji.common.constant.HttpStatus;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 0.0.4 前台年度回顾 操作处理
 *
 * <p>私人内容（D-11 仅登录可见）：不标 @Anonymous，匿名访问 401；
 * 聚合范围经 {@link AppScopeHelper#resolveCreateBy()} 解析，不另写过滤。</p>
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/review")
public class AppReviewController extends BaseController
{
    @Autowired
    private IAppReviewService reviewService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    /**
     * 某一年的回顾数据（年份列表 / 汇总 / 地点 / 评分榜 / 标签）
     *
     * @param year 自然年（四位数字，1900~2100）
     */
    @GetMapping("/year")
    public AjaxResult year(@RequestParam("year") String year)
    {
        Integer y = parseYear(year);
        if (y == null)
        {
            return AjaxResult.error(HttpStatus.BAD_REQUEST, "year 非法：须为 1900~2100 的四位年份");
        }
        return success(reviewService.getYearData(y, appScopeHelper.resolveCreateBy()));
    }

    /** 合法返回年份值，非法返回 null（非数字 / 越界） */
    private static Integer parseYear(String raw)
    {
        if (raw == null || raw.length() != 4)
        {
            return null;
        }
        try
        {
            int y = Integer.parseInt(raw);
            return (y >= 1900 && y <= 2100) ? y : null;
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
