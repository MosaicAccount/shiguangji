package com.shiguangji.business.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.shiguangji.business.domain.SgjNoteDraft;

/**
 * 笔记草稿表 数据层
 *
 * 全部查询按 create_by（本人）过滤，不做管理员旁路：草稿是未完成的私密文字。
 *
 * @author shiguangji
 */
public interface SgjNoteDraftMapper
{
    /**
     * 查询草稿箱列表（当前用户的全部草稿：新建态 + 编辑态，每个写作对象一行），按更新时间倒序
     *
     * 只取摘要所需的正文前缀（excerpt_src），不取全文——列表页免登录即可打开，
     * 入口条每次都要拉这份列表，不能把每份十万字的正文带上
     *
     * @param createBy 当前登录用户
     * @return 草稿集合（含 itemName 与 excerptSrc，content 为空）
     */
    public List<SgjNoteDraft> selectBoxDraftList(String createBy);

    /**
     * 按「写作对象」查询本人的那份草稿（取最新一行）
     *
     * @param createBy 当前登录用户
     * @param scope    写作对象身份：编辑态 = 笔记ID；新建态 = 其关联条目的负数（未选条目 = -1）
     * @return 草稿（含完整正文）；无则返回 null
     */
    public SgjNoteDraft selectDraftByScope(@Param("createBy") String createBy, @Param("scope") long scope);

    /**
     * 按草稿ID查询（含完整正文）
     *
     * @param draftId 草稿ID
     * @return 草稿；不存在返回 null
     */
    public SgjNoteDraft selectDraftById(Long draftId);

    /**
     * 新增草稿
     *
     * @param sgjNoteDraft 草稿
     * @return 结果
     */
    public int insertSgjNoteDraft(SgjNoteDraft sgjNoteDraft);

    /**
     * 修改草稿
     *
     * @param sgjNoteDraft 草稿
     * @return 结果
     */
    public int updateSgjNoteDraft(SgjNoteDraft sgjNoteDraft);

    /**
     * 删除草稿（硬删除，不进回收站）
     *
     * @param draftId 草稿ID
     * @return 结果
     */
    public int deleteSgjNoteDraftById(Long draftId);

    /**
     * 删除这些笔记的编辑态草稿（笔记软删 / 彻底删除时清理，三处清理点共用）
     *
     * @param noteIds 笔记ID
     * @return 结果
     */
    public int deleteByNoteIds(Long[] noteIds);

    /**
     * 删除这些条目下所有笔记的编辑态草稿。
     * 必须在删除条目前调用：条目删掉后 sgj_note 已被外键级联清空，按 item_id 反查将查不到任何行
     *
     * @param itemIds 条目ID
     * @return 结果
     */
    public int deleteByItemIds(Long[] itemIds);
}
