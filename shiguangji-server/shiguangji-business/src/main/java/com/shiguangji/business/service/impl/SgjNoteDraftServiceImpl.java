package com.shiguangji.business.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.shiguangji.business.domain.SgjNoteDraft;
import com.shiguangji.business.mapper.SgjNoteDraftMapper;
import com.shiguangji.business.mapper.SgjNoteMapper;
import com.shiguangji.business.service.ISgjNoteDraftService;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 笔记草稿 服务层实现
 *
 * <b>刻意不加 @Transactional</b>：upsert 走「先查后写 + 唯一键冲突转更新」，
 * 这个 catch 一旦跨 @Transactional 边界，Spring 会把外层事务标记为 rollback-only，
 * 转为更新的分支反而必然失败；而单条 insert/update 本就不需要事务
 *
 * @author shiguangji
 */
@Service
public class SgjNoteDraftServiceImpl implements ISgjNoteDraftService
{
    /** 是否公开：私密（默认值） */
    private static final String IS_PUBLIC_NO = "0";

    /** 是否公开：公开 */
    private static final String IS_PUBLIC_YES = "1";

    @Autowired
    private SgjNoteDraftMapper sgjNoteDraftMapper;

    @Autowired
    private SgjNoteMapper sgjNoteMapper;

    @Override
    public List<SgjNoteDraft> selectBoxList(String createBy)
    {
        List<SgjNoteDraft> list = sgjNoteDraftMapper.selectBoxDraftList(createBy);
        for (SgjNoteDraft draft : list)
        {
            // 摘要复用笔记列表同一套剥离逻辑（标题为空时它就是展示行的内容）
            draft.setExcerpt(SgjNoteServiceImpl.buildExcerpt(draft.getExcerptSrc(), draft.getTitle()));
            draft.setExcerptSrc(null);
        }
        return list;
    }

    @Override
    public SgjNoteDraft selectByNoteId(String createBy, Long noteId)
    {
        // 编辑态草稿的身份就是笔记ID（draft_scope = note_id）
        return sgjNoteDraftMapper.selectDraftByScope(createBy, noteId);
    }

    /**
     * 草稿身份（写作对象）：编辑已有笔记是那篇笔记（正数），新建笔记是关联条目（负数，没选条目为 -1）。
     * 与建表脚本里 draft_scope 的生成表达式一致——两边算得不一样就会跟唯一键对不上
     */
    private long scopeOf(SgjNoteDraft draft)
    {
        if (draft.getNoteId() != null)
        {
            return draft.getNoteId();
        }
        return -(draft.getItemId() == null ? 0L : draft.getItemId()) - 1;
    }

    @Override
    public SgjNoteDraft selectOwnedDraft(Long draftId, String createBy)
    {
        return requireOwned(draftId, createBy);
    }

    @Override
    public SgjNoteDraft saveDraft(SgjNoteDraft draft, String createBy)
    {
        // 无条件覆盖：createBy 是 BaseEntity 上客户端可绑定的字段，写成「为空才填」则 ?createBy=他人 即可读他人草稿
        draft.setCreateBy(createBy);
        validateDraft(draft);

        // 带 noteId 的写入必须先确认笔记还在（del_flag='0'）：编辑页在笔记被删后仍会继续推草稿，
        // 缺这道校验就会写回一行挂在回收站笔记上、甚至永久悬空的孤儿草稿
        if (draft.getNoteId() != null && sgjNoteMapper.selectAliveNoteId(draft.getNoteId()) == null)
        {
            throw new ServiceException("这篇笔记已被删除，草稿无法继续保存");
        }

        Long draftId = draft.getDraftId();
        boolean insert = draftId == null;
        if (draftId != null)
        {
            requireOwned(draftId, createBy);
        }
        else
        {
            // 每个写作对象最多一份：先按「本人 + 身份」定位，避免两台设备各建一行；
            // 新建态同样走这里（note_id 为空时身份是关联条目的负数），不能只对编辑态做
            SgjNoteDraft existing = sgjNoteDraftMapper.selectDraftByScope(createBy, scopeOf(draft));
            if (existing != null)
            {
                draftId = existing.getDraftId();
                insert = false;
            }
        }

        if (insert)
        {
            try
            {
                sgjNoteDraftMapper.insertSgjNoteDraft(draft);
                draftId = draft.getDraftId();
            }
            catch (DuplicateKeyException e)
            {
                // 并发兜底：两台设备同时首推同一个写作对象时撞上 uk_sgj_note_draft_owner_scope，转更新
                SgjNoteDraft existing = sgjNoteDraftMapper.selectDraftByScope(createBy, scopeOf(draft));
                if (existing == null)
                {
                    throw e;
                }
                draftId = existing.getDraftId();
                insert = false;
            }
        }
        if (!insert)
        {
            draft.setDraftId(draftId);
            sgjNoteDraftMapper.updateSgjNoteDraft(draft);
        }
        // updateTime 回查数据库：客户端要拿它当 baseUpdateTime 与下一次服务端时间比，
        // 用 JVM 的 new Date() 会引入第三个钟（结论 10）
        return sgjNoteDraftMapper.selectDraftById(draftId);
    }

    @Override
    public int deleteOwnedDraft(Long draftId, String createBy)
    {
        requireOwned(draftId, createBy);
        return sgjNoteDraftMapper.deleteSgjNoteDraftById(draftId);
    }

    @Override
    public int deleteByNoteIds(Long[] noteIds)
    {
        return noteIds == null || noteIds.length == 0 ? 0 : sgjNoteDraftMapper.deleteByNoteIds(noteIds);
    }

    @Override
    public int deleteByItemIds(Long[] itemIds)
    {
        return itemIds == null || itemIds.length == 0 ? 0 : sgjNoteDraftMapper.deleteByItemIds(itemIds);
    }

    /**
     * 取本人草稿：不存在或属他人一律拒绝（归属判断拿库里那行的 create_by 比，不信请求体）
     */
    private SgjNoteDraft requireOwned(Long draftId, String createBy)
    {
        SgjNoteDraft draft = draftId == null ? null : sgjNoteDraftMapper.selectDraftById(draftId);
        if (draft == null || !createBy.equals(draft.getCreateBy()))
        {
            throw new ServiceException("草稿不存在或无权访问");
        }
        return draft;
    }

    /**
     * 参数校验：标题/正文/标签长度与公开状态取值。标题允许为空（草稿可能还没写标题）
     */
    private void validateDraft(SgjNoteDraft draft)
    {
        String title = draft.getTitle();
        if (title != null)
        {
            title = title.trim();
            if (title.length() > SgjNoteServiceImpl.TITLE_MAX_LENGTH)
            {
                throw new ServiceException("笔记标题长度不能超过" + SgjNoteServiceImpl.TITLE_MAX_LENGTH + "个字符");
            }
            draft.setTitle(title.isEmpty() ? null : title);
        }
        if (draft.getContent() != null && draft.getContent().length() > SgjNoteServiceImpl.CONTENT_MAX_LENGTH)
        {
            throw new ServiceException("笔记内容长度不能超过" + SgjNoteServiceImpl.CONTENT_MAX_LENGTH + "个字符");
        }
        if (draft.getTags() != null && draft.getTags().length() > SgjNoteServiceImpl.TEXT_MAX_LENGTH)
        {
            throw new ServiceException("标签长度不能超过" + SgjNoteServiceImpl.TEXT_MAX_LENGTH + "个字符");
        }
        String isPublic = draft.getIsPublic();
        if (StringUtils.isEmpty(isPublic))
        {
            draft.setIsPublic(IS_PUBLIC_NO);
        }
        else if (!IS_PUBLIC_NO.equals(isPublic) && !IS_PUBLIC_YES.equals(isPublic))
        {
            throw new ServiceException("公开状态取值非法（仅允许 0 私密 / 1 公开）");
        }
    }
}
