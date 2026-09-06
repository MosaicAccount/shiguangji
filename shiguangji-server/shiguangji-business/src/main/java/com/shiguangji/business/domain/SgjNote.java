package com.shiguangji.business.domain;

import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 学习笔记表 sgj_note
 *
 * @author shiguangji
 */
public class SgjNote extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 笔记ID */
    private Long noteId;

    /** 关联条目ID，空为独立笔记 */
    private Long itemId;

    /** 笔记标题 */
    private String title;

    /** 笔记内容（Markdown） */
    private String content;

    /** 标签 */
    private String tags;

    /** 是否公开（0私密 1公开，访客仅可见公开笔记） */
    private String isPublic;

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

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

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
}