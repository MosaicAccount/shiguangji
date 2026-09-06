package com.shiguangji.business.service;

import java.util.List;

import com.shiguangji.business.domain.SgjNote;

/**
 * 学习笔记 服务层
 *
 * @author shiguangji
 */
public interface ISgjNoteService
{
    /**
     * 查询笔记
     *
     * @param noteId 笔记ID
     * @return 笔记
     */
    public SgjNote selectSgjNoteById(Long noteId);

    /**
     * 查询笔记列表
     *
     * @param sgjNote 笔记
     * @return 笔记集合
     */
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote);

    /**
     * 查询回收站笔记列表（del_flag='2'）
     *
     * @param sgjNote 笔记（支持 noteId/createBy/title 过滤）
     * @return 回收站笔记集合
     */
    public List<SgjNote> selectRecycleNoteList(SgjNote sgjNote);

    /**
     * 新增笔记
     *
     * @param sgjNote 笔记
     * @return 结果
     */
    public int insertSgjNote(SgjNote sgjNote);

    /**
     * 修改笔记
     *
     * @param sgjNote 笔记
     * @return 结果
     */
    public int updateSgjNote(SgjNote sgjNote);

    /**
     * 批量软删除笔记（标记 del_flag='2'，）
     *
     * @param noteIds 需要删除的笔记ID
     * @return 结果
     */
    public int deleteSgjNoteByIds(Long[] noteIds);

    /**
     * 恢复回收站笔记（del_flag='2' -> '0'，）
     *
     * @param noteIds 需要恢复的笔记ID
     * @return 结果
     */
    public int restoreSgjNoteByIds(Long[] noteIds);

    /**
     * 彻底删除回收站笔记（物理删除，）
     *
     * @param noteIds 需要彻底删除的笔记ID
     * @return 结果
     */
    public int purgeSgjNoteByIds(Long[] noteIds);
}