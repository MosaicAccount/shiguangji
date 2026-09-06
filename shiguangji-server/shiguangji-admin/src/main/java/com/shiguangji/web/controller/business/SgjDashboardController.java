package com.shiguangji.web.controller.business;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.service.ISgjDashboardService;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 后台首页统计 操作处理
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/business/dashboard")
public class SgjDashboardController extends BaseController
{
    @Autowired
    private ISgjDashboardService dashboardService;

    /**
     * 获取后台首页统计数据
     */
    @GetMapping("/statistics")
    public AjaxResult statistics()
    {
        Map<String, Object> data = dashboardService.getStatistics();
        return success(data);
    }
}