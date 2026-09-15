package com.shiguangji.web.controller.business;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.domain.SgjTag;
import com.shiguangji.business.service.ISgjTagService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;

/**
 * 前台标签 操作处理
 *
 * 供用户端选择标签的下拉数据源：仅返回指定模块下启用状态的标签，
 * 标签定义本身非敏感数据，浏览接口与 AppItemController 一致匿名放行
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/tag")
public class AppTagController extends BaseController
{
    @Autowired
    private ISgjTagService sgjTagService;

    /**
     * 查询指定模块的启用标签列表（按 sort 排序）
     *
     * @param sgjTag 查询参数（module 必填）
     */
    @Anonymous
    @GetMapping("/list")
    public AjaxResult list(SgjTag sgjTag)
    {
        if (sgjTag.getModule() == null || sgjTag.getModule().isEmpty())
        {
            return error("module 参数不能为空");
        }
        sgjTag.setStatus("0");
        List<SgjTag> list = sgjTagService.selectSgjTagList(sgjTag);
        return success(list);
    }
}
