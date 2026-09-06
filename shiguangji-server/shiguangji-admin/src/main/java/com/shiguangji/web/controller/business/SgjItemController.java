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

import com.shiguangji.business.domain.SgjItem;
import com.shiguangji.business.service.ISgjItemService;
import com.shiguangji.common.annotation.Log;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.core.page.TableDataInfo;
import com.shiguangji.common.core.text.Convert;
import com.shiguangji.common.enums.BusinessType;

/**
 * 内容条目 操作处理
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/business/item")
public class SgjItemController extends BaseController
{
    @Autowired
    private ISgjItemService sgjItemService;

    /**
     * 获取内容条目列表
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:list')")
    @GetMapping("/list")
    public TableDataInfo list(SgjItem sgjItem)
    {
        startPage();
        List<SgjItem> list = sgjItemService.selectSgjItemList(sgjItem);
        return getDataTable(list);
    }

    /**
     * 获取内容条目详细信息
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:query')")
    @GetMapping(value = "/{itemId}")
    public AjaxResult getInfo(@PathVariable Long itemId)
    {
        return success(sgjItemService.selectSgjItemById(itemId));
    }

    /**
     * 新增内容条目
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:add')")
    @Log(title = "内容条目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SgjItem sgjItem)
    {
        sgjItem.setCreateBy(getUsername());
        return toAjax(sgjItemService.insertSgjItem(sgjItem));
    }

    /**
     * 修改内容条目
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:edit')")
    @Log(title = "内容条目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SgjItem sgjItem)
    {
        sgjItem.setUpdateBy(getUsername());
        return toAjax(sgjItemService.updateSgjItem(sgjItem));
    }

    /**
     * 删除内容条目
     */
    @PreAuthorize("@ss.hasPermi('sgj:item:remove')")
    @Log(title = "内容条目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(sgjItemService.deleteSgjItemByIds(itemIds));
    }
}