package com.shiguangji.business.service;

import java.util.List;

import com.shiguangji.business.domain.SgjNoteDraft;

/**
 * 笔记草稿 服务层
 *
 * @author shiguangji
 */
public interface ISgjNoteDraftService
{
    /**
     * 查询草稿箱列表（当前用户的新笔记草稿）
     *
     * @param createBy 当前登录用户
     * @return 草稿集合（含 excerpt，不含正文）
     */
    public List<SgjNoteDraft> selectBoxList(String createBy);

    /**
     * 查询某篇笔记未保存完的改动（当前用户，最新一行，含正文）
     *
     * @param createBy 当前登录用户
     * @param noteId   笔记ID
     * @return 草稿；无则返回 null
     */
    public SgjNoteDraft selectByNoteId(String createBy, Long noteId);

    /**
     * 按草稿ID查询本人的草稿（含正文）
     *
     * @param draftId  草稿ID
     * @param createBy 当前登录用户
     * @return 草稿
     * @throws com.shiguangji.common.exception.ServiceException 草稿不存在或不属于本人
     */
    public SgjNoteDraft selectOwnedDraft(Long draftId, String createBy);

    /**
     * 保存草稿：带 draftId 则更新，不带则新建。
     * 带 noteId 时按「本人 + 笔记」先查后写（两台设备都离线时不会产生第二行），
     * 并校验该笔记仍存在且未删除
     *
     * @param draft    草稿（createBy 被无条件覆盖为当前登录用户）
     * @param createBy 当前登录用户
     * @return 落库后的草稿（updateTime 为数据库时钟，供客户端作 baseUpdateTime）
     */
    public SgjNoteDraft saveDraft(SgjNoteDraft draft, String createBy);

    /**
     * 删除本人的草稿（硬删除，不进回收站）
     *
     * @param draftId  草稿ID
     * @param createBy 当前登录用户
     * @return 结果
     * @throws com.shiguangji.common.exception.ServiceException 草稿不存在或不属于本人
     */
    public int deleteOwnedDraft(Long draftId, String createBy);

    /**
     * 清理这些笔记的编辑态草稿（笔记软删 / 彻底删除时调用）
     *
     * @param noteIds 笔记ID
     * @return 结果
     */
    public int deleteByNoteIds(Long[] noteIds);

    /**
     * 清理这些条目下所有笔记的编辑态草稿（条目彻底删除前调用）
     *
     * @param itemIds 条目ID
     * @return 结果
     */
    public int deleteByItemIds(Long[] itemIds);
}
