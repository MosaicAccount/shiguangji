package com.shiguangji.business.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

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
     * 查询计划页条目（0.0.3：未完成 WANT/DOING 的全类型汇总，按计划日期升序、无日期置后）
     *
     * <p>与 {@link #selectSgjItemList} 分开而不是加排序开关：后者有前台三个列表页、后台管理页、
     * 封面服务五类调用方，都不该背上这个排序口径（设计 结论 77）。</p>
     *
     * @param sgjItem 条目信息（createBy/itemType/title/tags 过滤）
     * @return 条目集合
     */
    public List<SgjItem> selectPlanItems(SgjItem sgjItem);

    /**
     * 统计计划页条目分态数量（issue #92：胶囊数字口径，全量分档、不受当前筛选影响）
     *
     * @param sgjItem 条目信息（createBy 过滤）
     * @return status/cnt 行集合
     */
    public List<Map<String, Object>> selectPlanStatusCount(SgjItem sgjItem);

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

    /**
     * 全局搜索的条目检索（0.0.5 FR-005-5）：item_type 集合内混排、标题 LIKE、按更新时间倒序。
     * 分页由调用方 PageHelper 控制（每组取前 N 条 + total）。
     *
     * @param itemTypes 条目类型集合（影单组为 MOVIE+TV 混排）
     * @param titleLike 已转义的标题 LIKE 片段
     * @param createBy 可见范围（null＝不限）
     * @return 条目集合（含扩展字段；comment/remark 由调用方置空）
     */
    public List<SgjItem> selectSearchItems(@Param("itemTypes") List<String> itemTypes,
            @Param("titleLike") String titleLike, @Param("createBy") String createBy);
}