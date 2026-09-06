package com.shiguangji.web.controller.business;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.pagehelper.Page;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.common.annotation.Anonymous;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.utils.SecurityUtils;

/**
 * 前台学习笔记 操作处理
 *
 * 浏览接口（GET）保持匿名只读；新增/修改/删除等写操作要求登录后本人操作
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/note")
public class AppNoteController extends BaseController
{
    @Autowired
    private ISgjNoteService sgjNoteService;

    @Autowired
    private AppScopeHelper appScopeHelper;

    /**
     * 查询前台笔记列表（按可见范围过滤 ；分页 ，默认第1页每页10条）
     *
     * ：匿名访客仅可见公开笔记（is_public='1'），覆盖请求参数防止越权取到私密笔记
     */
    @Anonymous
    @GetMapping("/list")
    public AjaxResult list(SgjNote sgjNote)
    {
        // 强制按当前请求的可见范围过滤，防止越权查看他人数据
        sgjNote.setCreateBy(appScopeHelper.resolveCreateBy());
        if (appScopeHelper.isAnonymous())
        {
            sgjNote.setIsPublic("1");
        }
        startPage();
        List<SgjNote> list = sgjNoteService.selectSgjNoteList(sgjNote);
        // 访客响应脱敏：清空笔记私人备注
        appScopeHelper.maskNotesForGuest(list);
        AjaxResult result = success(list);
        result.put("total", ((Page<?>) list).getTotal());
        return result;
    }

    /**
     * 查询前台笔记详情（按可见范围过滤，）
     *
     * ：非公开笔记对匿名访客不可见
     */
    @Anonymous
    @GetMapping("/{noteId}")
    public AjaxResult getInfo(@PathVariable Long noteId)
    {
        SgjNote note = sgjNoteService.selectSgjNoteById(noteId);
        if (note == null || !isVisible(note.getCreateBy())
                || (appScopeHelper.isAnonymous() && !"1".equals(note.getIsPublic())))
        {
            return error("笔记不存在或无权访问");
        }
        // 访客响应脱敏：清空笔记私人备注
        appScopeHelper.maskNoteForGuest(note);
        return success(note);
    }

    /**
     * 判断当前请求是否可见该创建者对应的数据
     */
    private boolean isVisible(String createBy)
    {
        String scope = appScopeHelper.resolveCreateBy();
        return scope == null || createBy == null || createBy.isEmpty() || scope.equals(createBy);
    }

    /**
     * 新增前台笔记
     */
    @PostMapping
    public AjaxResult add(@RequestBody SgjNote sgjNote)
    {
        sgjNote.setCreateBy(SecurityUtils.getUsername());
        return toAjax(sgjNoteService.insertSgjNote(sgjNote));
    }

    /**
     * 修改前台笔记（仅本人或管理员，）
     */
    @PutMapping
    public AjaxResult edit(@RequestBody SgjNote sgjNote)
    {
        SgjNote oldNote = sgjNoteService.selectSgjNoteById(sgjNote.getNoteId());
        if (oldNote == null)
        {
            return error("笔记不存在");
        }
        if (!appScopeHelper.canOperate(oldNote.getCreateBy()))
        {
            return error("无权操作该笔记");
        }
        // 未传关联条目时保留原关联，避免被置空
        if (sgjNote.getItemId() == null)
        {
            sgjNote.setItemId(oldNote.getItemId());
        }
        sgjNote.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(sgjNoteService.updateSgjNote(sgjNote));
    }

    /**
     * 删除前台笔记（仅本人或管理员，）
     */
    @DeleteMapping("/{noteId}")
    public AjaxResult remove(@PathVariable Long noteId)
    {
        SgjNote oldNote = sgjNoteService.selectSgjNoteById(noteId);
        if (oldNote == null)
        {
            return error("笔记不存在");
        }
        if (!appScopeHelper.canOperate(oldNote.getCreateBy()))
        {
            return error("无权操作该笔记");
        }
        return toAjax(sgjNoteService.deleteSgjNoteByIds(new Long[] { noteId }));
    }

    /**
     * 查询回收站笔记列表（需登录，按可见范围过滤，）
     */
    @GetMapping("/recycle/list")
    public AjaxResult recycleList(SgjNote sgjNote)
    {
        sgjNote.setCreateBy(appScopeHelper.resolveCreateBy());
        List<SgjNote> list = sgjNoteService.selectRecycleNoteList(sgjNote);
        return success(list);
    }

    /**
     * 恢复回收站笔记（，仅本人或管理员）
     */
    @PostMapping("/recycle/restore/{noteId}")
    public AjaxResult recycleRestore(@PathVariable Long noteId)
    {
        if (!canOperateRecycleNote(noteId))
        {
            return error("回收站中不存在该笔记或无权操作");
        }
        return toAjax(sgjNoteService.restoreSgjNoteByIds(new Long[] { noteId }));
    }

    /**
     * 彻底删除回收站笔记（，仅本人或管理员）
     */
    @PostMapping("/recycle/purge/{noteId}")
    public AjaxResult recyclePurge(@PathVariable Long noteId)
    {
        if (!canOperateRecycleNote(noteId))
        {
            return error("回收站中不存在该笔记或无权操作");
        }
        return toAjax(sgjNoteService.purgeSgjNoteByIds(new Long[] { noteId }));
    }

    /**
     * 校验回收站笔记归属（本人或管理员）
     */
    private boolean canOperateRecycleNote(Long noteId)
    {
        SgjNote query = new SgjNote();
        query.setNoteId(noteId);
        List<SgjNote> notes = sgjNoteService.selectRecycleNoteList(query);
        if (notes.isEmpty())
        {
            return false;
        }
        return appScopeHelper.canOperate(notes.get(0).getCreateBy());
    }
}