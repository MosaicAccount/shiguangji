package com.shiguangji.business.service;

import java.util.List;

import com.shiguangji.business.domain.SgjTag;

/**
 * 标签 服务层
 *
 * @author shiguangji
 */
public interface ISgjTagService
{
    /**
     * 查询标签
     *
     * @param tagId 标签ID
     * @return 标签
     */
    public SgjTag selectSgjTagById(Long tagId);

    /**
     * 查询标签列表
     *
     * @param sgjTag 标签信息
     * @return 标签集合
     */
    public List<SgjTag> selectSgjTagList(SgjTag sgjTag);

    /**
     * 新增标签
     *
     * @param sgjTag 标签信息
     * @return 结果
     */
    public int insertSgjTag(SgjTag sgjTag);

    /**
     * 修改标签
     *
     * @param sgjTag 标签信息
     * @return 结果
     */
    public int updateSgjTag(SgjTag sgjTag);

    /**
     * 批量软删除标签（不回写已引用记录）
     *
     * @param tagIds 需要删除的标签ID
     * @return 结果
     */
    public int deleteSgjTagByIds(Long[] tagIds);
}
