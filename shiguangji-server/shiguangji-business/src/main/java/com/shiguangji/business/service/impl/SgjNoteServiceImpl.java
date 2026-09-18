package com.shiguangji.business.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.mapper.SgjNoteMapper;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 学习笔记 服务层实现
 *
 * @author shiguangji
 */
@Service
public class SgjNoteServiceImpl implements ISgjNoteService
{
    /** 笔记标题最大长度（与表结构 varchar(200) 一致） */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 长文本字段最大长度 */
    private static final int TEXT_MAX_LENGTH = 500;

    /** 笔记内容最大长度（longtext 列无压力；上限用于拦住会拖慢编辑页预览与草稿同步的超长正文） */
    private static final int CONTENT_MAX_LENGTH = 100000;

    /** 是否公开：私密（默认值） */
    private static final String IS_PUBLIC_NO = "0";

    /** 是否公开：公开（访客可见） */
    private static final String IS_PUBLIC_YES = "1";

    @Autowired
    private SgjNoteMapper sgjNoteMapper;

    @Override
    public SgjNote selectSgjNoteById(Long noteId)
    {
        return sgjNoteMapper.selectSgjNoteById(noteId);
    }

    @Override
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote)
    {
        cleanKeyword(sgjNote);
        return sgjNoteMapper.selectSgjNoteList(sgjNote);
    }

    @Override
    public List<SgjNote> selectAppNoteList(SgjNote sgjNote)
    {
        cleanKeyword(sgjNote);
        return sgjNoteMapper.selectAppNoteList(sgjNote);
    }

    /**
     * 清洗检索关键词：剔除布尔模式的运算符字符后再传参。
     * 运算符会被当语法解释（如前导 - 使整个查询恒为 0 行），包引号屏蔽不掉（ngram 丢符号）；
     * ngram 分词本就忽略这些符号，剔除不损失匹配能力。清洗后为空白则视为未输入，不参与过滤
     */
    private void cleanKeyword(SgjNote sgjNote)
    {
        String keyword = sgjNote.getKeyword();
        if (StringUtils.isEmpty(keyword))
        {
            return;
        }
        String cleaned = keyword.replaceAll("[+\\-*\"()~<>@]", " ").trim();
        sgjNote.setKeyword(StringUtils.isEmpty(cleaned) ? null : cleaned);
    }

    @Override
    public List<SgjNote> selectRecycleNoteList(SgjNote sgjNote)
    {
        return sgjNoteMapper.selectRecycleNoteList(sgjNote);
    }

    @Override
    public int insertSgjNote(SgjNote sgjNote)
    {
        validateNote(sgjNote, true);
        // 新增笔记未传公开状态时默认私密
        if (StringUtils.isEmpty(sgjNote.getIsPublic()))
        {
            sgjNote.setIsPublic(IS_PUBLIC_NO);
        }
        return sgjNoteMapper.insertSgjNote(sgjNote);
    }

    @Override
    public int updateSgjNote(SgjNote sgjNote)
    {
        if (sgjNote.getNoteId() == null)
        {
            throw new ServiceException("笔记ID不能为空");
        }
        validateNote(sgjNote, false);
        return sgjNoteMapper.updateSgjNote(sgjNote);
    }

    @Override
    public int deleteSgjNoteByIds(Long[] noteIds)
    {
        return sgjNoteMapper.deleteSgjNoteByIds(noteIds);
    }

    @Override
    public int restoreSgjNoteByIds(Long[] noteIds)
    {
        return sgjNoteMapper.restoreSgjNoteByIds(noteIds);
    }

    @Override
    public int purgeSgjNoteByIds(Long[] noteIds)
    {
        return sgjNoteMapper.purgeSgjNoteByIds(noteIds);
    }

    /**
     * 参数校验：标题必填/长度、字段长度
     *
     * @param sgjNote       笔记
     * @param titleRequired 新增时标题必填；编辑时允许不传标题（部分更新）
     */
    private void validateNote(SgjNote sgjNote, boolean titleRequired)
    {
        // 标题去除首尾空白后校验必填/长度，并回写 trim 值（修复：避免全空格标题或首尾空白入库）
        String title = sgjNote.getTitle();
        if (titleRequired && StringUtils.isBlank(title))
        {
            throw new ServiceException("笔记标题不能为空");
        }
        if (title != null && title.trim().length() > TITLE_MAX_LENGTH)
        {
            throw new ServiceException("笔记标题长度不能超过" + TITLE_MAX_LENGTH + "个字符");
        }
        if (title != null && !title.equals(title.trim()))
        {
            sgjNote.setTitle(title.trim());
        }
        if (sgjNote.getContent() != null && sgjNote.getContent().length() > CONTENT_MAX_LENGTH)
        {
            throw new ServiceException("笔记内容长度不能超过" + CONTENT_MAX_LENGTH + "个字符");
        }
        if (sgjNote.getTags() != null && sgjNote.getTags().length() > TEXT_MAX_LENGTH)
        {
            throw new ServiceException("标签长度不能超过" + TEXT_MAX_LENGTH + "个字符");
        }
        if (sgjNote.getRemark() != null && sgjNote.getRemark().length() > TEXT_MAX_LENGTH)
        {
            throw new ServiceException("备注长度不能超过" + TEXT_MAX_LENGTH + "个字符");
        }
        // 公开状态仅允许 0/1；编辑时不传表示不修改公开状态
        String isPublic = sgjNote.getIsPublic();
        if (!StringUtils.isEmpty(isPublic) && !IS_PUBLIC_NO.equals(isPublic) && !IS_PUBLIC_YES.equals(isPublic))
        {
            throw new ServiceException("公开状态取值非法（仅允许 0 私密 / 1 公开）");
        }
    }
}