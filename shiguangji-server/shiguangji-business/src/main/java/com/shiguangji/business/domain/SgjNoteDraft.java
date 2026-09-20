package com.shiguangji.business.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 笔记草稿表 sgj_note_draft
 *
 * noteId 为空表示「新笔记草稿」（只有这类进草稿箱）；不为空表示某篇笔记未保存完的改动。
 * 草稿只本人可见，删除为硬删除（不进回收站）。
 *
 * @author shiguangji
 */
public class SgjNoteDraft extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 草稿ID */
    private Long draftId;

    /** 编辑来源笔记ID；空=新笔记草稿（只有这类进草稿箱） */
    private Long noteId;

    /** 关联条目ID，空为独立笔记 */
    private Long itemId;

    /** 关联条目名称（联表查询展示用，非表字段；查不到时前端回退 #id） */
    private String itemName;

    /** 标题（草稿可为空） */
    private String title;

    /** 正文（Markdown） */
    private String content;

    /** 标签 */
    private String tags;

    /** 公开状态（0私密 1公开，保存后沿用到笔记） */
    private String isPublic;

    /** 草稿箱展示用摘要原文片段（SQL 截出的正文前缀，生成 excerpt 后置空不下发，非表字段） */
    private String excerptSrc;

    /** 草稿箱展示用摘要（纯文本；标题为空时展示它，非表字段） */
    private String excerpt;

    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }

    public Long getNoteId()
    {
        return noteId;
    }

    public void setNoteId(Long noteId)
    {
        this.noteId = noteId;
    }

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public String getItemName()
    {
        return itemName;
    }

    public void setItemName(String itemName)
    {
        this.itemName = itemName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    /** 草稿箱列表不下发正文（只给 excerpt），故为 null 时不序列化；编辑页按 id / noteId 取单条时才有值 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getTags()
    {
        return tags;
    }

    public void setTags(String tags)
    {
        this.tags = tags;
    }

    public String getIsPublic()
    {
        return isPublic;
    }

    public void setIsPublic(String isPublic)
    {
        this.isPublic = isPublic;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getExcerptSrc()
    {
        return excerptSrc;
    }

    public void setExcerptSrc(String excerptSrc)
    {
        this.excerptSrc = excerptSrc;
    }

    public String getExcerpt()
    {
        return excerpt;
    }

    public void setExcerpt(String excerpt)
    {
        this.excerpt = excerpt;
    }

}
