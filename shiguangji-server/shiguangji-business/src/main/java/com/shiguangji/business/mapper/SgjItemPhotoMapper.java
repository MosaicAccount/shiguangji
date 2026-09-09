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
