package com.shiguangji.business.service.impl;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shiguangji.business.constants.SgjNoteConstants;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.mapper.SgjNoteMapper;
import com.shiguangji.business.service.ISgjNoteDraftService;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.business.utils.SgjNoteUtils;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 学习笔记 服务层实现
 *
 * @author shiguangji
 */
@Service
public class SgjNoteServiceImpl implements ISgjNoteService {
    /** 笔记标题最大长度（与表结构 varchar(200) 一致；草稿服务复用同一常量，避免两处定义漂移） */
    static final int TITLE_MAX_LENGTH = 200;

    /** 长文本字段最大长度 */
    static final int TEXT_MAX_LENGTH = 500;

    /** 笔记内容最大长度（longtext 列无压力；上限用于拦住会拖慢编辑页预览与草稿同步的超长正文） */
    static final int CONTENT_MAX_LENGTH = 100000;

    /** 是否公开：私密（默认值） */
    private static final String IS_PUBLIC_NO = "0";

    /** 是否公开：公开（访客可见） */
    private static final String IS_PUBLIC_YES = "1";

    @Autowired
    private SgjNoteMapper sgjNoteMapper;

    @Autowired
    private ISgjNoteDraftService sgjNoteDraftService;

    @Override
    public SgjNote selectSgjNoteById(Long noteId) {
        return sgjNoteMapper.selectSgjNoteById(noteId);
    }

    @Override
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote) {
        SgjNoteUtils.cleanKeyword(sgjNote);
        return sgjNoteMapper.selectSgjNoteList(sgjNote);
    }

    @Override
    public List<SgjNote> selectSgjNoteFrontList(SgjNote sgjNote) {
        SgjNoteUtils.cleanKeyword(sgjNote);
        List<SgjNote> list = sgjNoteMapper.selectSgjNoteFrontList(sgjNote);
        if (!list.isEmpty()) {
            for (SgjNote note : list) {
                String body = SgjNoteUtils.removeDuplicatedTitle(SgjNoteUtils.removeYamlFrontFormat(note.getContent()),
                        note.getTitle());

                String keyword = sgjNote.getKeyword();
                List<String> excerptList = SgjNoteUtils.extractSnippets(body, sgjNote.getKeyword());
                if (StringUtils.isNotBlank(sgjNote.getKeyword())) {

                    long hitTotal = excerptList.stream().map(item -> StringUtils.countMatches(item.toLowerCase(), keyword.toLowerCase()))
                            .mapToInt(Integer::intValue).sum();
                    hitTotal += StringUtils.countMatches(note.getTitle().toLowerCase(), keyword.toLowerCase());
                    note.setHitTotal(hitTotal);
                }

                note.setExcerpt(SgjNoteUtils.stripMarkdown(CollectionUtils.isNotEmpty(excerptList) ? excerptList.get(0)
                        : SgjNoteUtils.truncateAtWordBoundary(body, SgjNoteConstants.EXCERPT_WINDOW_LENGTH)));
            }

        }
        return list;
    }

    @Override
    public List<SgjNote> selectRecycleNoteList(SgjNote sgjNote) {
        return sgjNoteMapper.selectRecycleNoteList(sgjNote);
    }

    @Override
    @Transactional
    public int insertSgjNote(SgjNote sgjNote) {
        validateNote(sgjNote, true);
        // 草稿身份：新增笔记时它是空白草稿（库里 note_id = 0，这里传 null）——insert 会把 noteId 回填成
        // 新笔记的 ID，所以必须在写入前取一次
        Long blankDraftTarget = sgjNote.getNoteId();
        // 新增笔记未传公开状态时默认私密
        if (StringUtils.isEmpty(sgjNote.getIsPublic())) {
            sgjNote.setIsPublic(IS_PUBLIC_NO);
        }
        int rows = sgjNoteMapper.insertSgjNote(sgjNote);
        // 保存成功与删除草稿必须在同一事务里：留着旧草稿，编辑页下次自动恢复会把它盖回刚保存好的正文。
        // 身份要在插入前算：insert 会把 noteId 回填成新笔记的 ID，而这份草稿的身份是「新建笔记」（按关联条目）
        deleteDraftOnSave(sgjNote, sgjNote.getCreateBy(), blankDraftTarget);
        return rows;
    }

    @Override
    @Transactional
    public int updateSgjNote(SgjNote sgjNote) {
        if (sgjNote.getNoteId() == null) {
            throw new ServiceException("笔记ID不能为空");
        }
        validateNote(sgjNote, false);
        int rows = sgjNoteMapper.updateSgjNote(sgjNote);
        if (rows > 0) {
            String owner = StringUtils.isNotEmpty(sgjNote.getUpdateBy()) ? sgjNote.getUpdateBy()
                    : sgjNote.getCreateBy();
            deleteDraftOnSave(sgjNote, owner, sgjNote.getNoteId());
        }
        return rows;
    }

    /**
     * 保存笔记成功后删除对应草稿：带的是他人草稿时抛错（整个请求失败，不写任何数据）。
     * 与笔记写入同事务，不存在「笔记保存了但草稿还在」的中间状态
     */
    /**
     * 保存成功时清掉这份写作对象的草稿。两道：
     *
     * <ol>
     * <li>带 `draftId` 就按 id 删——前端的正常路径，也顺便守住「带他人 draftId 整个请求失败」的约定；</li>
     * <li>再按「写作对象身份」删一次兜底：换设备 / 清过本地缓冲时前端手里没有 draftId，
     * 不兜这一刀，那条「已经保存过」的草稿会一直挂在草稿箱里，下次写笔记还会撞上它。</li>
     * </ol>
     *
     * 两道都在保存的同一个事务里（调用方已 `@Transactional`），不会出现「笔记存了、草稿还在」的中间态。
     *
     * @param draftTargetNoteId 草稿身份：编辑态是那篇笔记的 ID；新增态传 null（空白草稿，库里是 0）。
     *                          调用方要在写入**之前**取好（新增时 insert 会回填 noteId）
     */
    private void deleteDraftOnSave(SgjNote sgjNote, String owner, Long draftTargetNoteId) {
        if (sgjNote.getDraftId() != null) {
            sgjNoteDraftService.deleteOwnedDraft(sgjNote.getDraftId(), owner);
        }
        sgjNoteDraftService.deleteByNoteId(owner, draftTargetNoteId);
    }

    @Override
    @Transactional
    public int deleteSgjNoteByIds(Long[] noteIds) {
        // 笔记进回收站时一并清掉它的草稿：否则留下用户看不到、也清不掉的孤儿行
        sgjNoteDraftService.deleteByNoteIds(noteIds);
        return sgjNoteMapper.deleteSgjNoteByIds(noteIds);
    }

    @Override
    public int restoreSgjNoteByIds(Long[] noteIds) {
        return sgjNoteMapper.restoreSgjNoteByIds(noteIds);
    }

    @Override
    @Transactional
    public int purgeSgjNoteByIds(Long[] noteIds) {
        sgjNoteDraftService.deleteByNoteIds(noteIds);
        return sgjNoteMapper.purgeSgjNoteByIds(noteIds);
    }

    /**
     * 参数校验：标题必填/长度、字段长度
     *
     * @param sgjNote       笔记
     * @param titleRequired 新增时标题必填；编辑时允许不传标题（部分更新）
     */
    private void validateNote(SgjNote sgjNote, boolean titleRequired) {
        // 标题去除首尾空白后校验必填/长度，并回写 trim 值（修复：避免全空格标题或首尾空白入库）
        String title = sgjNote.getTitle();
        if (titleRequired && StringUtils.isBlank(title)) {
            throw new ServiceException("笔记标题不能为空");
        }
        if (title != null && title.trim().length() > TITLE_MAX_LENGTH) {
            throw new ServiceException("笔记标题长度不能超过" + TITLE_MAX_LENGTH + "个字符");
        }
        if (title != null && !title.equals(title.trim())) {
            sgjNote.setTitle(title.trim());
        }
        if (sgjNote.getContent() != null && sgjNote.getContent().length() > CONTENT_MAX_LENGTH) {
            throw new ServiceException("笔记内容长度不能超过" + CONTENT_MAX_LENGTH + "个字符");
        }
        if (sgjNote.getTags() != null && sgjNote.getTags().length() > TEXT_MAX_LENGTH) {
            throw new ServiceException("标签长度不能超过" + TEXT_MAX_LENGTH + "个字符");
        }
        if (sgjNote.getRemark() != null && sgjNote.getRemark().length() > TEXT_MAX_LENGTH) {
            throw new ServiceException("备注长度不能超过" + TEXT_MAX_LENGTH + "个字符");
        }
        // 公开状态仅允许 0/1；编辑时不传表示不修改公开状态
        String isPublic = sgjNote.getIsPublic();
        if (!StringUtils.isEmpty(isPublic) && !IS_PUBLIC_NO.equals(isPublic) && !IS_PUBLIC_YES.equals(isPublic)) {
            throw new ServiceException("公开状态取值非法（仅允许 0 私密 / 1 公开）");
        }
    }

    /**
     * 生成详情页渲染用正文（供 AppNoteController.getInfo 使用）：
     * 剥离 YAML 前言（front-matter）整块，再去掉与标题全等重复的首个一级标题行。
     *
     * 口径与列表摘要/命中计数完全一致（SgjNoteMapper.xml 的 fmEnd 与 selectSgjNoteExcerptFragments），
     * 改这里必须同步改那边，否则「列表次数 = 详情可跳转处数」会失配
     *
     * @param content 笔记原文（可带前言），可为 null
     * @param title   笔记标题（用于首行去重），可为 null
     * @return 去前言、去重标题行后的正文；content 为空时原样返回
     */
    public static String buildBody(String content, String title) {
        if (StringUtils.isEmpty(content)) {
            return content;
        }
        String body = content.substring(frontMatterEnd(content));
        if (StringUtils.isEmpty(title)) {
            return body;
        }
        String h1 = "# " + title + "\n";
        // SQL 侧的 left(...) = concat(...) 走 MySQL 默认 *_ci 排序规则（大小写不敏感），用 regionMatches
        // 对齐
        return body.regionMatches(true, 0, h1, 0, h1.length()) ? body.substring(h1.length()) : body;
    }

    /**
     * 正文起始下标（0 基）。语义与 SgjNoteMapper.xml 的 fmEnd 片段一致：
     * 首行以三连字符开头才认定有前言，找首个 '\n---' 作闭合（+5 跳过闭合行），找不到闭合按无前言处理
     */
    private static int frontMatterEnd(String content) {
        if (!content.startsWith("---\n") && !content.startsWith("---\r")) {
            return 0;
        }
        int close = content.indexOf("\n---");
        // SQL: coalesce(nullif(locate('\n---', content), 0) + 5, 1)；locate 为 1 基，转 0
        // 基后即 close + 5
        return close < 0 ? 0 : Math.min(close + 5, content.length());
    }

    /**
     * 生成导出用的 Markdown 全文（服务端现场生成，issue #41）。
     *
     * @param note 笔记（需要 noteId / title / tags / content）
     * @return 可直接下载的 Markdown 全文，以换行结尾
     */
    public static String buildExportMarkdown(SgjNote note) {
        String content = note.getContent() == null ? "" : note.getContent();
        String body = content.substring(frontMatterEnd(content));
        // front-matter 与正文之间固定空一行：正文自己开头的换行先去掉，否则会出现两个空行
        int start = 0;
        while (start < body.length() && (body.charAt(start) == '\n' || body.charAt(start) == '\r')) {
            start++;
        }
        body = body.substring(start);

        body = SgjNoteUtils.writeYaml(body, "noteId", note.getNoteId() == null ? "" : note.getNoteId());
        body = SgjNoteUtils.writeYaml(body, "title", note.getTitle());
        body = SgjNoteUtils.writeYaml(body, "tags", note.getTags());

        return body;
    }

}