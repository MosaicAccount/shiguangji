package com.shiguangji.business.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;

import com.shiguangji.business.constants.SgjNoteConstants;
import com.shiguangji.business.domain.NoteBacklink;
import com.shiguangji.business.domain.NoteLink;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.mapper.SgjNoteMapper;
import com.shiguangji.business.service.ISgjNoteDraftService;
import com.shiguangji.business.service.ISgjNoteService;
import com.shiguangji.business.utils.SgjNoteLinkUtils;
import com.shiguangji.business.utils.SgjNoteUtils;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.PageUtils;
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
        SgjNote note = sgjNoteMapper.selectSgjNoteById(noteId);

        note.setBody(SgjNoteUtils.buildBody(note.getContent(), note.getTitle()));
        return note;
    }

    @Override
    public List<SgjNote> selectSgjNoteList(SgjNote sgjNote) {
        SgjNoteUtils.cleanKeyword(sgjNote);
        return listWithKeywordLikeFallback(sgjNote, () -> sgjNoteMapper.selectSgjNoteList(sgjNote));
    }

    @Override
    public List<SgjNote> selectSgjNoteFrontList(SgjNote sgjNote) {
        SgjNoteUtils.cleanKeyword(sgjNote);
        List<SgjNote> list = listWithKeywordLikeFallback(sgjNote, () -> sgjNoteMapper.selectSgjNoteFrontList(sgjNote));
        if (!list.isEmpty()) {
            for (SgjNote note : list) {
                // 必须先剥离 Markdown 再截窗：截断点落在语法内部会留下半截标记（如 ![x](h），
                // 而剥离正则要求分隔符成对，匹配不上就会原样展示到卡片上
                String body = SgjNoteUtils.stripMarkdown(
                        SgjNoteUtils.buildBody(note.getContent(), note.getTitle()));

                String keyword = sgjNote.getKeyword();
                List<String> excerptList = SgjNoteUtils.extractSnippets(body, keyword);
                if (StringUtils.isNotBlank(keyword)) {

                    long hitTotal = excerptList.stream()
                            .map(item -> StringUtils.countMatches(item.toLowerCase(), keyword.toLowerCase()))
                            .mapToInt(Integer::intValue).sum();
                    hitTotal += StringUtils.countMatches(note.getTitle().toLowerCase(), keyword.toLowerCase());
                    note.setHitTotal(hitTotal);
                }

                String excerpt;
                if (CollectionUtils.isNotEmpty(excerptList)) {
                    excerpt = excerptList.get(0);
                } else {
                    // 剥离后为空（整篇只有代码块 / 只有 front-matter）：回退原始文本开头，卡片不空白；
                    // content 可空（DDL 允许），defaultString 兜住后 excerpt 落成空串
                    String fallback = StringUtils.isNotEmpty(body) ? body
                            : StringUtils.defaultString(note.getContent());
                    excerpt = SgjNoteUtils.truncateAtWordBoundary(fallback, SgjNoteConstants.EXCERPT_WINDOW_LENGTH);
                }
                note.setExcerpt(excerpt);
                // 前台列表不下发正文：正文只走详情接口
                note.setContent(null);
            }

        }
        return list;
    }

    /**
     * 关键词全文检索 0 命中时用 like 复核一次（issue #66）。
     *
     * <p>InnoDB 的 ngram 全文索引在 deleted-doc 比例升高后会自动重建，重建窗口内新写入的
     * 文档可能 match 不到（like 能查到、重跑即恢复，偶发时红时绿——排查记录见 issue）。
     * 兜底只在「带关键词且全文 0 命中」时触发：换 like 条件重查一次，可见范围过滤
     * （del_flag/is_public/create_by）原样复用。索引健康时 like 命中是 ngram 命中的子集，
     * 结果不变；索引处于重建窗口时结果从 0 行恢复为实际命中。</p>
     *
     * <p>ponytail: 0 命中那次复核是全表扫（content 为 longtext），个人项目量级可接受；
     * 笔记到十万级再换「索引健康探测」或放弃 ngram。首查已消费控制器 startPage() 的
     * 分页线程变量，复核前按同一请求参数重建分页，使返回形状（Page/total）与首查一致；
     * 无请求上下文（内部调用）时跳过重建，复核不截断。</p>
     */
    private List<SgjNote> listWithKeywordLikeFallback(SgjNote sgjNote, Supplier<List<SgjNote>> query) {
        List<SgjNote> list = query.get();
        if (list.isEmpty() && StringUtils.isNotEmpty(sgjNote.getKeyword())) {
            if (RequestContextHolder.getRequestAttributes() != null) {
                PageUtils.startPage();
            }
            sgjNote.getParams().put("keywordLike", Boolean.TRUE);
            list = query.get();
        }
        return list;
    }

    @Override
    public List<SgjNote> selectRecycleNoteList(SgjNote sgjNote) {
        return sgjNoteMapper.selectRecycleNoteList(sgjNote);
    }

    /**
     * 附加互链上下文（0.0.5 FR-005-1/2/3）：正文 [[标题]] 的目标解析、反向链接与同名计数。
     *
     * <p>判定逻辑收敛在 {@link SgjNoteLinkUtils} 的静态纯函数里；这里只做编排：
     * 一次标题候选查询（distinct 标题）解析 links，一次同名计数查询判定重名，
     * 重名时反链不列来源（指向本篇的链接此时全部降级，D-14），否则一次正文 LIKE 候选
     * 查询交给纯函数精确校验（代码块里的 [[...]] 不算引用）。</p>
     *
     * <p>ponytail: 反链是详情页一次 LIKE 扫描（content 为 longtext、前通配不走索引），
     * 个人量级（千篇以内）可接受、不做缓存；笔记量或公开访问量上来后新增 sgj_note_link
     * 关系表 + 一次性回填，本方法出参形状不变。</p>
     */
    @Override
    public void attachLinkContext(SgjNote note, String createBy, boolean anonymous) {
        List<SgjNoteLinkUtils.NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens(note.getBody());
        List<NoteLink> links;
        if (tokens.isEmpty()) {
            links = new ArrayList<>();
        } else {
            Set<String> titles = new LinkedHashSet<>();
            for (SgjNoteLinkUtils.NoteToken token : tokens) {
                titles.add(token.getTitle());
            }
            links = SgjNoteLinkUtils.resolveLinks(tokens,
                    sgjNoteMapper.selectNoteRefsByTitles(titles, createBy, anonymous));
        }
        note.setLinks(links);

        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        if (title.isEmpty() || note.getNoteId() == null) {
            // 存量脏数据兜底：无标题无法定位目标，反链空、不下发同名计数
            note.setBacklinks(new ArrayList<>());
            return;
        }
        List<SgjNote> sameTitleRefs =
                sgjNoteMapper.selectNoteRefsByTitles(Collections.singletonList(title), createBy, anonymous);
        note.setSameTitleCount(SgjNoteLinkUtils.countExactTitle(sameTitleRefs, title));
        if (note.getSameTitleCount() > 1) {
            // 标题重名：指向本篇的链接全部降级，反链不列来源（D-14）
            note.setBacklinks(new ArrayList<>());
            return;
        }
        note.setBacklinks(SgjNoteLinkUtils.buildBacklinks(
                sgjNoteMapper.selectBacklinkCandidates(SgjNoteLinkUtils.escapeLike(title),
                        note.getNoteId(), createBy, anonymous),
                title));
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
     * 生成导出用的 Markdown 全文（服务端现场生成，issue #41）。
     *
     * @param note 笔记（需要 noteId / title / tags / content）
     * @return 可直接下载的 Markdown 全文，以换行结尾
     */
    public static String buildExportMarkdown(SgjNote note) {
        String content = note.getContent() == null ? "" : note.getContent();
        content = SgjNoteUtils.writeYaml(content, "noteId", note.getNoteId() == null ? "" : note.getNoteId());
        content = SgjNoteUtils.writeYaml(content, "title", note.getTitle());
        content = SgjNoteUtils.writeYaml(content, "tags", note.getTags());

        // .md 文件以换行收尾：否则「最后一行」在部分查看器与再导入时会与后续内容黏在一起
        return content.endsWith("\n") ? content : content + "\n";
    }

}