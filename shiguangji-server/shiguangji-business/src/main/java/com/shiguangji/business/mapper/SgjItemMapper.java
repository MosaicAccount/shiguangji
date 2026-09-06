package com.shiguangji.business.mapper;

import java.util.List;

import com.shiguangji.business.domain.SgjItem;

/**
 * 内容条目主表 数据层
 *
 * @author shiguangji
 */
public interface SgjItemMapper
{
    /**
     * 查询条目（含扩展字段）
     *
     * @param itemId 条目ID
     * @return 条目信息
     */
    public SgjItem selectSgjItemById(Long itemId);

    /**
     * 查询条目列表（主表字段）
     *
     * @param sgjItem 条目信息
     * @return 条目集合
     */
    public List<SgjItem> selectSgjItemList(SgjItem sgjItem);

    /**
     * 查询回收站条目列表（del_flag='2'）
     *
     * @param sgjItem 条目信息（支持 itemId/createBy/itemType/title 过滤）
     * @return 回收站条目集合
     */
    public List<SgjItem> selectRecycleItemList(SgjItem sgjItem);

    /**
     * 新增条目主表
     *
     * @param sgjItem 条目信息
     * @return 结果
     */
    public int insertSgjItem(SgjItem sgjItem);

    /**
     * 修改条目主表
     *
     * @param sgjItem 条目信息
     * @return 结果
     */
    public int updateSgjItem(SgjItem sgjItem);

    /**
     * 新增条目扩展表（根据 itemType 自动路由）
     *
     * @param sgjItem 条目信息
     * @return 结果
     */
    public int insertSgjItemExt(SgjItem sgjItem);

    /**
     * 修改条目扩展表（根据 itemType 自动路由）
     *
     * @param sgjItem 条目信息
     * @return 结果
     */
    public int updateSgjItemExt(SgjItem sgjItem);

    /**
     * 软删除条目（标记 del_flag='2'）
     *
     * @param itemId 条目ID
     * @return 结果
     */
    public int deleteSgjItemById(Long itemId);

    /**
     * 批量软删除条目（标记 del_flag='2'）
     *
     * @param itemIds 需要删除的条目ID
     * @return 结果
     */
    public int deleteSgjItemByIds(Long[] itemIds);

    /**
     * 恢复回收站条目（del_flag='2' -> '0'）
     *
     * @param itemIds 需要恢复的条目ID
     * @return 结果
     */
    public int restoreSgjItemByIds(Long[] itemIds);

    /**
     * 彻底删除回收站条目（物理删除，扩展表依赖外键级联删除）
     *
     * @param itemIds 需要彻底删除的条目ID
     * @return 结果
     */
    public int purgeSgjItemByIds(Long[] itemIds);
}