package com.shiguangji.business.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /** 关联条目名称（联表查询展示用，非表字段） */
    private String itemName;

    /** 笔记标题 */
    private String title;

    /** 笔记内容（Markdown） */
    private String content;

    /** 标签 */
    private String tags;

    /** 是否公开（0私密 1公开，访客仅可见公开笔记） */
    private String isPublic;

    /** 检索关键词（标题+正文全文匹配，列表查询入参，非表字段；title 保留仅标题原义） */
    private String keyword;

    /** 检索词按空白切分（服务层由 keyword 派生的入参，供前台命中窗口/计数 SQL 使用，非表字段） */
    private List<String> keywordWords;

    /** 摘要原文片段（SQL 定位截出的「命中窗口/前缀」，生成 excerpt 后置空不下发，非表字段） */
    private String excerptSrc;

    /** 前台列表摘要（纯文本，非表字段；正文不再随前台列表下发） */
    private String excerpt;

    /** 全文命中次数（出参，非表字段；供「共 N 处命中」提示，仅关键词检索时有值） */
    private Long hitTotal;

    /** 详情渲染用正文（已剥离 YAML 前言、去掉与标题重复的首个一级标题行，出参，非表字段） */
    private String body;

    /** 保存时携带的草稿ID（入参，非表字段）：保存成功后在同一事务里删除该草稿 */
    private Long draftId;

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

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword(String keyword)
    {
        this.keyword = keyword;
    }

    public List<String> getKeywordWords()
    {
        return keywordWords;
    }

    public void setKeywordWords(List<String> keywordWords)
    {
        this.keywordWords = keywordWords;
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getHitTotal()
    {
        return hitTotal;
    }

    public void setHitTotal(Long hitTotal)
    {
        this.hitTotal = hitTotal;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getBody()
    {
        return body;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }
}