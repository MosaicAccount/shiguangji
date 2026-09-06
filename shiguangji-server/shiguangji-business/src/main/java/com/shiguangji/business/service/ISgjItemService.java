package com.shiguangji.business.service;

import java.util.List;

import com.shiguangji.business.domain.SgjItem;

/**
 * 内容条目 服务层
 *
 * @author shiguangji
 */
public interface ISgjItemService
{
    /**
     * 查询条目（含扩展字段）
     *
     * @param itemId 条目ID
     * @return 条目
     */
    public SgjItem selectSgjItemById(Long itemId);

    /**
     * 查询条目列表
     *
     * @param sgjItem 条目
     * @return 条目集合
     */
    public List<SgjItem> selectSgjItemList(SgjItem sgjItem);

    /**
     * 查询回收站条目列表（del_flag='2'）
     *
     * @param sgjItem 条目（支持 itemId/createBy/itemType/title 过滤）
     * @return 回收站条目集合
     */
    public List<SgjItem> selectRecycleItemList(SgjItem sgjItem);

    /**
     * 新增条目（主表 + 对应扩展表）
     *
     * @param sgjItem 条目
     * @return 结果
     */
    public int insertSgjItem(SgjItem sgjItem);

    /**
     * 修改条目（主表 + 对应扩展表）
     *
     * @param sgjItem 条目
     * @return 结果
     */
    public int updateSgjItem(SgjItem sgjItem);

    /**
     * 批量软删除条目（标记 del_flag='2'，）
     *
     * @param itemIds 需要删除的条目ID
     * @return 结果
     */
    public int deleteSgjItemByIds(Long[] itemIds);

    /**
     * 恢复回收站条目（del_flag='2' -> '0'，）
     *
     * @param itemIds 需要恢复的条目ID
     * @return 结果
     */
    public int restoreSgjItemByIds(Long[] itemIds);

    /**
     * 彻底删除回收站条目（物理删除，）
     *
     * @param itemIds 需要彻底删除的条目ID
     * @return 结果
     */
    public int purgeSgjItemByIds(Long[] itemIds);
}