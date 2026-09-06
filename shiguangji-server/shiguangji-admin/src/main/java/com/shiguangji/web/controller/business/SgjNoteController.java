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

import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.common.annotation.Log;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.core.page.TableDataInfo;
import com.shiguangji.common.core.text.Convert;
import com.shiguangji.common.enums.BusinessType;

/**
 * 学习笔记 操作处理
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/business/note")
public class SgjNoteController extends BaseController
{
    @Autowired
    private ISgjNoteService sgjNoteService;

    /**
     * 获取学习笔记列表
     */
    @PreAuthorize("@ss.hasPermi('sgj:note:list')")
    @GetMapping("/list")
    public TableDataInfo list(SgjNote sgjNote)
    {
        startPage();
        List<SgjNote> list = sgjNoteService.selectSgjNoteList(sgjNote);
        return getDataTable(list);
    }

    /**
     * 获取学习笔记详细信息
     */
    @PreAuthorize("@ss.hasPermi('sgj:note:query')")
    @GetMapping(value = "/{noteId}")
    public AjaxResult getInfo(@PathVariable Long noteId)
    {
        return success(sgjNoteService.selectSgjNoteById(noteId));
    }

    /**
     * 新增学习笔记
     */
    @PreAuthorize("@ss.hasPermi('sgj:note:add')")
    @Log(title = "学习笔记", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SgjNote sgjNote)
    {
        sgjNote.setCreateBy(getUsername());
        return toAjax(sgjNoteService.insertSgjNote(sgjNote));
    }

    /**
     * 修改学习笔记
     */
    @PreAuthorize("@ss.hasPermi('sgj:note:edit')")
    @Log(title = "学习笔记", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SgjNote sgjNote)
    {
        sgjNote.setUpdateBy(getUsername());
        return toAjax(sgjNoteService.updateSgjNote(sgjNote));
    }

    /**
     * 删除学习笔记
     */
    @PreAuthorize("@ss.hasPermi('sgj:note:remove')")
    @Log(title = "学习笔记", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noteIds}")
    public AjaxResult remove(@PathVariable Long[] noteIds)
    {
        return toAjax(sgjNoteService.deleteSgjNoteByIds(noteIds));
    }
}