package com.shiguangji.business.mapper;

import java.util.List;

import com.shiguangji.business.domain.SgjNote;

/**
 * 学习笔记表 数据层
 *
 * @author shiguangji
 */
public interface SgjNoteMapper
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
     * @param sgjNote 笔记信息
     * @return 笔记集合
     */
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote);

    /**
     * 前台查询笔记列表（与 selectSgjNoteList 同过滤同排序，但正文只回传一段几百字符的
     * 短文本：有关键词取以命中处为中心的窗口，否则取开头，不再回传全文）
     *
     * @param sgjNote 笔记信息
     * @return 笔记集合（content 为截取片段）
     */
    public List<SgjNote> selectAppNoteList(SgjNote sgjNote);

    /**
     * 查询回收站笔记列表（del_flag='2'）
     *
     * @param sgjNote 笔记信息（支持 noteId/createBy/title 过滤）
     * @return 回收站笔记集合
     */
    public List<SgjNote> selectRecycleNoteList(SgjNote sgjNote);

    /**
     * 新增笔记
     *
     * @param sgjNote 笔记信息
     * @return 结果
     */
    public int insertSgjNote(SgjNote sgjNote);

    /**
     * 修改笔记
     *
     * @param sgjNote 笔记信息
     * @return 结果
     */
    public int updateSgjNote(SgjNote sgjNote);

    /**
     * 软删除笔记（标记 del_flag='2'）
     *
     * @param noteId 笔记ID
     * @return 结果
     */
    public int deleteSgjNoteById(Long noteId);

    /**
     * 批量软删除笔记（标记 del_flag='2'）
     *
     * @param noteIds 需要删除的笔记ID
     * @return 结果
     */
    public int deleteSgjNoteByIds(Long[] noteIds);

    /**
     * 恢复回收站笔记（del_flag='2' -> '0'）
     *
     * @param noteIds 需要恢复的笔记ID
     * @return 结果
     */
    public int restoreSgjNoteByIds(Long[] noteIds);

    /**
     * 彻底删除回收站笔记（物理删除）
     *
     * @param noteIds 需要彻底删除的笔记ID
     * @return 结果
     */
    public int purgeSgjNoteByIds(Long[] noteIds);
}