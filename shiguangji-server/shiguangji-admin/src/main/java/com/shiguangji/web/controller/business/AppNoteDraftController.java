package com.shiguangji.web.controller.business;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shiguangji.business.domain.SgjNoteDraft;
import com.shiguangji.business.service.ISgjNoteDraftService;
import com.shiguangji.common.core.controller.BaseController;
import com.shiguangji.common.core.domain.AjaxResult;
import com.shiguangji.common.utils.SecurityUtils;

/**
 * 前台笔记草稿 操作处理（服务端草稿箱，issue #35 / #37）
 *
 * <b>可见范围只按当前登录用户</b>：这里刻意不使用 {@link AppScopeHelper}——它的规则是
 * 「站长与管理员可见全部数据」，而草稿是「还没写完的东西」，被管理员读到比正式内容更不合适，
 * 所以草稿接口一律按当前登录用户过滤，没有管理员旁路。这是本版唯一不沿用 AppScopeHelper 的接口组。
 * 相应地，写入路径上 createBy 必须<b>无条件覆盖</b>为当前登录用户（createBy 是 BaseEntity 上
 * 客户端可绑定的字段，写成「为空才填」则 {@code ?createBy=他人} 即可读到别人的草稿）。
 *
 * 全部接口要求登录（不加 @Anonymous）：草稿是未完成的私密文字，匿名访问应被拒（业务码 401）。
 *
 * @author shiguangji
 */
@RestController
@RequestMapping("/app/note/draft")
public class AppNoteDraftController extends BaseController
{
    @Autowired
    private ISgjNoteDraftService sgjNoteDraftService;

    /**
     * 草稿列表
     *
     * 不带参数：当前用户的新笔记草稿（草稿箱用，只回 excerpt 与 itemName，不下发正文）；
     * 带 noteId：该篇笔记未保存完的改动（编辑页静默恢复用，含完整正文）
     */
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(required = false) Long noteId)
    {
        String owner = SecurityUtils.getUsername();
        if (noteId != null)
        {
            return success(sgjNoteDraftService.selectByNoteId(owner, noteId));
        }
        return success(sgjNoteDraftService.selectBoxList(owner));
    }

    /**
     * 取本人的**空白草稿**（还没成为笔记的那一份，每人一份；含完整正文）
     *
     * 进「写笔记」时按身份先拿它：本机 localStorage 里没有 draftId 时（换设备 / 清过缓存）
     * 也能把上一份未写完的东西接回来——不拿的话，写第二篇会静默覆盖第一篇
     */
    @GetMapping("/blank")
    public AjaxResult blank()
    {
        return success(sgjNoteDraftService.selectBlankDraft(SecurityUtils.getUsername()));
    }

    /**
     * 按ID取单条草稿（含完整正文）：草稿箱「继续写」与冷启动的 /note/edit?draftId= 用
     */
    @GetMapping("/{draftId}")
    public AjaxResult getInfo(@PathVariable Long draftId)
    {
        return success(sgjNoteDraftService.selectOwnedDraft(draftId, SecurityUtils.getUsername()));
    }

    /**
     * 保存草稿：带 draftId 则更新、不带则新建。
     * 返回 draftId 与 updateTime（updateTime 取自数据库，客户端拿它当 baseUpdateTime）
     */
    @PutMapping
    public AjaxResult save(@RequestBody SgjNoteDraft sgjNoteDraft)
    {
        SgjNoteDraft saved = sgjNoteDraftService.saveDraft(sgjNoteDraft, SecurityUtils.getUsername());
        // 只回 draftId 与 updateTime（不回整份正文：自动保存每 15 秒一次，回显十万字没有意义）
        Map<String, Object> data = new HashMap<>();
        data.put("draftId", saved.getDraftId());
        data.put("updateTime", saved.getUpdateTime());
        return success(data);
    }

    /**
     * 删除草稿（硬删除，不进回收站）
     */
    @DeleteMapping("/{draftId}")
    public AjaxResult remove(@PathVariable Long draftId)
    {
        return toAjax(sgjNoteDraftService.deleteOwnedDraft(draftId, SecurityUtils.getUsername()));
    }
}
