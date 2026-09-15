package com.shiguangji.business.mapper;

import java.util.List;

import com.shiguangji.business.domain.SgjItemPhoto;

/**
 * 条目照片 数据层
 *
 * @author shiguangji
 */
public interface SgjItemPhotoMapper
{
    /**
     * 查询条目照片（按排序号、照片ID升序）
     *
     * @param itemId 条目ID
     * @return 照片集合
     */
    public List<SgjItemPhoto> selectPhotosByItemId(Long itemId);

    /**
     * 批量查询多个条目的照片（按条目、排序号升序），供轨迹/列表聚合照片数与封面
     *
     * @param itemIds 条目ID集合
     * @return 照片集合
     */
    public List<SgjItemPhoto> selectPhotosByItemIds(List<Long> itemIds);

    /**
     * 批量新增条目照片
     *
     * @param photos 照片集合
     * @return 结果
     */
    public int insertPhotos(List<SgjItemPhoto> photos);

    /**
     * 删除条目全部照片（整体替换保存的前半段）
     *
     * @param itemId 条目ID
     * @return 结果
     */
    public int deletePhotosByItemId(Long itemId);
}
