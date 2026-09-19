package com.shiguangji.business.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

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
     * 判断笔记是否存在且未删除（草稿写入的存活校验用，不取正文）
     *
     * @param noteId 笔记ID
     * @return 存活时返回笔记ID；不存在 / 在回收站（del_flag='2'）返回 null
     */
    public Long selectAliveNoteId(Long noteId);

    /**
     * 查询笔记列表
     *
     * @param sgjNote 笔记信息
     * @return 笔记集合
     */
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote);

    /**
     * 查询前台笔记列表（不带全文：正文与 remark 不下发；摘要片段由 selectSgjNoteExcerptFragments 补查）
     *
     * @param sgjNote 笔记信息
     * @return 笔记集合
     */
    public List<SgjNote> selectSgjNoteFrontList(SgjNote sgjNote);

    /**
     * 按页内主键补查摘要原文片段与命中总数（excerpt_src 为 SQL 定位截出的原文片段，未剥离 Markdown）
     *
     * @param noteIds      页内笔记ID
     * @param keywordWords 检索词（按空白切分；空表示无关键词，取正文前缀）
     * @return 仅含 noteId/excerptSrc/hitTotal 的笔记集合
     */
    public List<SgjNote> selectSgjNoteExcerptFragments(@Param("noteIds") List<Long> noteIds,
                                                      @Param("keywordWords") List<String> keywordWords);

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