package com.shiguangji.web.controller.business;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.domain.SgjTag;
import com.shiguangji.business.service.ISgjTagService;
import com.shiguangji.common.annotation.Log;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.core.page.TableDataInfo;
import com.shiguangji.common.enums.BusinessType;

/**
 * 标签管理 操作处理
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/business/tag")
public class SgjTagController extends BaseController
{
    @Autowired
    private ISgjTagService sgjTagService;

    /**
     * 获取标签列表
     */
    @PreAuthorize("@ss.hasPermi('sgj:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(SgjTag sgjTag)
    {
        startPage();
        List<SgjTag> list = sgjTagService.selectSgjTagList(sgjTag);
        return getDataTable(list);
    }

    /**
     * 获取标签详细信息
     */
    @PreAuthorize("@ss.hasPermi('sgj:tag:query')")
    @GetMapping(value = "/{tagId}")
    public AjaxResult getInfo(@PathVariable Long tagId)
    {
        return success(sgjTagService.selectSgjTagById(tagId));
    }

    /**
     * 新增标签
     */
    @PreAuthorize("@ss.hasPermi('sgj:tag:add')")
    @Log(title = "标签管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SgjTag sgjTag)
    {
        sgjTag.setCreateBy(getUsername());
        return toAjax(sgjTagService.insertSgjTag(sgjTag));
    }

    /**
     * 修改标签
     */
    @PreAuthorize("@ss.hasPermi('sgj:tag:edit')")
    @Log(title = "标签管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SgjTag sgjTag)
    {
        sgjTag.setUpdateBy(getUsername());
        return toAjax(sgjTagService.updateSgjTag(sgjTag));
    }

    /**
     * 删除标签（软删除，不回写已引用记录）
     */
    @PreAuthorize("@ss.hasPermi('sgj:tag:remove')")
    @Log(title = "标签管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tagIds}")
    public AjaxResult remove(@PathVariable Long[] tagIds)
    {
        return toAjax(sgjTagService.deleteSgjTagByIds(tagIds));
    }
}
