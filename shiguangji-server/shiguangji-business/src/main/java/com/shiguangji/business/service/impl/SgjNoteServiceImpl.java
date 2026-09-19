package com.shiguangji.business.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** 摘要目标长度：无命中前缀取正文开头约 120 字 */
    private static final int EXCERPT_PREFIX_LENGTH = 120;

    /** 摘要目标长度：命中窗口保留命中前后各约 40 字（命中词本身居中，约 90 字上下） */
    private static final int EXCERPT_WINDOW_LENGTH = 96;

    /** 截断落在英文单词中间时最多向后扩的字符数（切到最近的空白或标点） */
    private static final int WORD_BOUNDARY_MAX_EXTRA = 10;

    /** 片段两端省略号（片段之外还有正文时由 SQL 拼接，见 SgjNoteMapper.xml selectSgjNoteFrontList） */
    private static final char ELLIPSIS = '…';

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
    public List<SgjNote> selectSgjNoteFrontList(SgjNote sgjNote)
    {
        cleanKeyword(sgjNote);
        List<SgjNote> list = sgjNoteMapper.selectSgjNoteFrontList(sgjNote);
        if (!list.isEmpty())
        {
            fillExcerptFragments(list, sgjNote.getKeywordWords());
        }
        return list;
    }

    /**
     * 按页内主键补查摘要原文片段与命中总数，并回填到列表行。
     * 单独一条小查询的原因：复杂表达式若直接写进列表查询，PageHelper 生成 count 语句时
     * jsqlparser 解析会灾难性回溯（实测 CPU 卡死），基础列表查询必须保持简单列清单
     */
    private void fillExcerptFragments(List<SgjNote> list, List<String> keywordWords)
    {
        List<Long> noteIds = list.stream().map(SgjNote::getNoteId).collect(Collectors.toList());
        Map<Long, SgjNote> fragments = sgjNoteMapper.selectSgjNoteExcerptFragments(noteIds, keywordWords)
                .stream().collect(Collectors.toMap(SgjNote::getNoteId, n -> n));
        for (SgjNote note : list)
        {
            SgjNote fragment = fragments.get(note.getNoteId());
            if (fragment != null)
            {
                note.setExcerptSrc(fragment.getExcerptSrc());
                note.setHitTotal(fragment.getHitTotal());
            }
        }
    }

    /**
     * 清洗检索关键词：剔除布尔模式的运算符字符后再传参。
     * 运算符会被当语法解释（如前导 - 使整个查询恒为 0 行），包引号屏蔽不掉（ngram 丢符号）；
     * ngram 分词本就忽略这些符号，剔除不损失匹配能力。清洗后为空白则视为未输入，不参与过滤。
     * 同时按空白切词（keywordWords），供前台命中窗口定位与命中计数 SQL 使用
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
        sgjNote.setKeywordWords(sgjNote.getKeyword() == null ? null : Arrays.asList(sgjNote.getKeyword().split("\\s+")));
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
    public static String buildBody(String content, String title)
    {
        if (StringUtils.isEmpty(content))
        {
            return content;
        }
        String body = content.substring(frontMatterEnd(content));
        if (StringUtils.isEmpty(title))
        {
            return body;
        }
        String h1 = "# " + title + "\n";
        // SQL 侧的 left(...) = concat(...) 走 MySQL 默认 *_ci 排序规则（大小写不敏感），用 regionMatches 对齐
        return body.regionMatches(true, 0, h1, 0, h1.length()) ? body.substring(h1.length()) : body;
    }

    /**
     * 正文起始下标（0 基）。语义与 SgjNoteMapper.xml 的 fmEnd 片段一致：
     * 首行以三连字符开头才认定有前言，找首个 '\n---' 作闭合（+5 跳过闭合行），找不到闭合按无前言处理
     */
    private static int frontMatterEnd(String content)
    {
        if (!content.startsWith("---\n") && !content.startsWith("---\r"))
        {
            return 0;
        }
        int close = content.indexOf("\n---");
        // SQL: coalesce(nullif(locate('\n---', content), 0) + 5, 1)；locate 为 1 基，转 0 基后即 close + 5
        return close < 0 ? 0 : Math.min(close + 5, content.length());
    }

    /**
     * 由 SQL 截出的原文片段生成前台摘要（供 AppNoteController.list 使用）。
     *
     * 片段带定位标记：开头/结尾的 … 表示片段之外还有正文（SQL 依据截窗几何位置拼接）。
     * 处理顺序：剥离 Markdown →（片段起于正文开头时）移除与标题全等的首行 → 折叠空白 →
     * 按目标长度截断（命中窗口 96 / 无命中前缀 120，以开头是否有 … 区分）→ 按需补回省略号。
     * 剥离后为空（如整篇只有代码块）时回退原文，保证卡片不空白
     *
     * @param fragment SQL 截出的原文片段（可带两端 …），可为 null
     * @param title    笔记标题（用于首行去重）
     * @return 纯文本摘要；无内容时为空串
     */
    public static String buildExcerpt(String fragment, String title)
    {
        if (StringUtils.isEmpty(fragment))
        {
            return "";
        }
        boolean leadingEllipsis = fragment.charAt(0) == ELLIPSIS;
        boolean trailingEllipsis = fragment.charAt(fragment.length() - 1) == ELLIPSIS;
        // 片段仅剩一个省略号字符（正文为空行的边界产物）时内部为空串
        String inner = fragment.length() == 1 ? ""
                : fragment.substring(leadingEllipsis ? 1 : 0, fragment.length() - (trailingEllipsis ? 1 : 0));

        String strippedLines = stripMarkdownLines(inner);
        String text;
        if (collapseWhitespace(strippedLines).isEmpty())
        {
            // 整篇只有代码块等剥完即空的情况：回退原文开头
            text = collapseWhitespace(inner);
        }
        else
        {
            // 命中窗口可能起于正文开头（命中落在前 40 字内），此时同样做标题去重
            text = leadingEllipsis ? strippedLines : removeDuplicatedTitle(strippedLines, title);
        }
        String collapsed = collapseWhitespace(text);
        if (collapsed.isEmpty())
        {
            // 正文以同名一级标题开头且仅此一行：摘要为空
            return "";
        }
        int target = leadingEllipsis ? EXCERPT_WINDOW_LENGTH : EXCERPT_PREFIX_LENGTH;
        String cut = truncateAtWordBoundary(collapsed, target);
        boolean truncated = cut.length() < collapsed.length();
        return (leadingEllipsis ? String.valueOf(ELLIPSIS) : "")
                + cut + ((truncated || trailingEllipsis) ? String.valueOf(ELLIPSIS) : "");
    }

    /**
     * 剥离 Markdown 为纯文本：围栏代码块整块移除、图片移除、链接保留文字、行内代码去反引号、
     * 加粗/斜体/删除线去标记、HTML 标签去标签、标题/引用/列表/任务列表去前缀、表格分隔行移除、
     * 连续空白折叠为单个空格
     *
     * @param text Markdown 文本，可为 null
     * @return 单行纯文本；无内容时为空串
     */
    public static String stripMarkdown(String text)
    {
        return collapseWhitespace(stripMarkdownLines(text));
    }

    /**
     * 剥离 Markdown，保留换行（供摘要按行做标题去重后再折叠）
     */
    private static String stripMarkdownLines(String text)
    {
        if (text == null || text.isEmpty())
        {
            return "";
        }
        // 前言（YAML front-matter）已由 SQL 在截窗前跳过（SgjNoteMapper.xml fmEnd 片段），此处不重复处理。
        // ponytail: 截窗落进代码块内部时开头无开场围栏，其代码文本会按普通文字保留——
        // 命中本就在代码行里，展示出来反而可读，不为此加围栏状态机
        String s = text.replaceAll("(?s)```.*?```", "\n");
        // 图片移除、链接保留文字、行内代码去反引号（图/链接先于行内代码，语法统一被清掉）
        s = s.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        s = s.replaceAll("`([^`]*)`", "$1");
        // 加粗/斜体/删除线去标记；__ 仅在成对包裹且不贴词字符时去除（避免破坏 snake_case）
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("~~([^~]+)~~", "$1");
        s = s.replaceAll("(?<![\\w_])__([^_\\n][\\s\\S]*?)__(?![\\w_])", "$1");
        s = s.replaceAll("\\*([^*\\n]+)\\*", "$1");
        // HTML 标签去标签保留文字
        s = s.replaceAll("<[^>]+>", " ");
        StringBuilder sb = new StringBuilder(s.length());
        for (String line : s.split("\n", -1))
        {
            // 表格分隔行（形如 |---|---:|）整行移除
            if (isTableSeparator(line))
            {
                continue;
            }
            String t = line;
            t = t.replaceFirst("^\\s{0,3}#{1,6}\\s+", "");          // 标题
            t = t.replaceFirst("^\\s{0,3}>\\s?", "");               // 引用
            t = t.replaceFirst("^\\s{0,3}[-*+]\\s+\\[[ xX]\\]\\s+", ""); // 任务列表
            t = t.replaceFirst("^\\s{0,3}[-*+]\\s+", "");           // 无序列表
            t = t.replaceFirst("^\\s{0,3}\\d{1,9}\\.\\s+", "");     // 有序列表
            sb.append(t).append('\n');
        }
        return sb.toString();
    }

    /** 表格分隔行：整行仅由空白、|、:、- 组成且至少含一个 | 与一个 - */
    private static boolean isTableSeparator(String line)
    {
        String trimmed = line.trim();
        return trimmed.indexOf('|') >= 0 && trimmed.indexOf('-') >= 0 && trimmed.matches("[\\s:|-]+");
    }

    /** 首行与标题全等（正文以同名一级标题开头）时移除该行，避免摘要与卡片标题重复 */
    private static String removeDuplicatedTitle(String strippedLines, String title)
    {
        if (StringUtils.isEmpty(title))
        {
            return strippedLines;
        }
        String[] parts = strippedLines.split("\n", 2);
        if (parts.length > 0 && title.trim().equals(parts[0].trim()))
        {
            return parts.length > 1 ? parts[1] : "";
        }
        return strippedLines;
    }

    /** 连续空白折叠为单个空格并去首尾 */
    private static String collapseWhitespace(String text)
    {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 截断到目标长度；截断点落在英文单词中间时最多向后扩 {@link #WORD_BOUNDARY_MAX_EXTRA}
     * 字符到最近的单词边界（中文按字符切，不受影响）
     */
    private static String truncateAtWordBoundary(String text, int max)
    {
        if (text.length() <= max)
        {
            return text;
        }
        if (!isAsciiWordChar(text.charAt(max - 1)) || !isAsciiWordChar(text.charAt(max)))
        {
            return text.substring(0, max);
        }
        int end = max;
        int limit = Math.min(text.length(), max + WORD_BOUNDARY_MAX_EXTRA);
        while (end < limit && isAsciiWordChar(text.charAt(end)))
        {
            end++;
        }
        return text.substring(0, end);
    }

    /** ASCII 字母/数字视为英文单词字符（中文按字符切，不参与单词边界扩展） */
    private static boolean isAsciiWordChar(char c)
    {
        return c < 128 && Character.isLetterOrDigit(c);
    }
}