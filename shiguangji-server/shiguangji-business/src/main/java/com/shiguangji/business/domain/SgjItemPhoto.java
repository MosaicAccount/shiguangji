package com.shiguangji.business.domain;

import com.shiguangji.common.core.domain.BaseEntity;

/**
 * 条目照片表 sgj_item_photo
 *
 * 照片随条目整体替换保存（按 item_id 删后按顺序重插），sort_order 由提交顺序生成
 *
 * @author shiguangji
 */
public class SgjItemPhoto extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 照片ID */
    private Long photoId;

    /** 关联条目ID */
    private Long itemId;

    /** 照片URL（/profile前缀相对路径） */
    private String url;

    /** 排序号（小在前） */
    private Integer sortOrder;

    public Long getPhotoId()
    {
        return photoId;
    }

    public void setPhotoId(Long photoId)
    {
        this.photoId = photoId;
    }

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString()
    {
        return "SgjItemPhoto [photoId=" + photoId + ", itemId=" + itemId + ", url=" + url + ", sortOrder=" + sortOrder + "]";
    }
}
