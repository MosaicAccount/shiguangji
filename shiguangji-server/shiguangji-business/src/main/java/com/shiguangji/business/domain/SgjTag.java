package com.shiguangji.business.domain;

import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 标签表 sgj_tag
 *
 * @author shiguangji
 */
public class SgjTag extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 标签ID */
    private Long tagId;

    /** 所属模块（MOVIE/TV/BOOK/PLACE/NOTE） */
    private String module;

    /** 标签名称 */
    private String tagName;

    /** 显示排序 */
    private Long sort;

    /** 状态（0启用 1停用） */
    private String status;

    public Long getTagId()
    {
        return tagId;
    }

    public void setTagId(Long tagId)
    {
        this.tagId = tagId;
    }

    public String getModule()
    {
        return module;
    }

    public void setModule(String module)
    {
        this.module = module;
    }

    public String getTagName()
    {
        return tagName;
    }

    public void setTagName(String tagName)
    {
        this.tagName = tagName;
    }

    public Long getSort()
    {
        return sort;
    }

    public void setSort(Long sort)
    {
        this.sort = sort;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
