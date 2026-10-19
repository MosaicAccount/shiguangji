package com.shiguangji.business.mapper;

import java.util.Collection;
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

    /**
     * 按标题集合取互链解析候选（0.0.5 FR-005-1）。
     *
     * <p>只做候选：库排序规则不区分大小写，标题的逐字判定（含大小写）在
     * SgjNoteLinkUtils.resolveLinks 收口。titles 为空集合时返回空（1=0 兜底）。</p>
     *
     * @param titles 目标标题集合（已去重）
     * @param createBy 可见范围（null＝不限，博主/管理员）
     * @param publicOnly 仅公开笔记（匿名访客）
     * @return 候选笔记（noteId/title/isPublic/createBy/updateTime/createTime）
     */
    public List<SgjNote> selectNoteRefsByTitles(@Param("titles") Collection<String> titles,
            @Param("createBy") String createBy, @Param("publicOnly") boolean publicOnly);

    /**
     * 反链来源候选（0.0.5 FR-005-2）：正文包含目标标题的笔记。
     *
     * <p>LIKE 只做候选（通配符已由 SgjNoteLinkUtils.escapeLike 转义）；
     * 代码块里的假引用由 SgjNoteLinkUtils.buildBacklinks 精确校验剔除。</p>
     *
     * @param likeTitle 已转义的目标标题（LIKE 模式片段）
     * @param excludeNoteId 排除的笔记ID（本篇自身）
     * @param createBy 可见范围（null＝不限）
     * @param publicOnly 仅公开笔记（匿名访客）
     * @return 候选笔记（含 content），按更新时间倒序
     */
    public List<SgjNote> selectBacklinkCandidates(@Param("likeTitle") String likeTitle,
            @Param("excludeNoteId") Long excludeNoteId, @Param("createBy") String createBy,
            @Param("publicOnly") boolean publicOnly);
}