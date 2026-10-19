package com.shiguangji.business.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shiguangji.business.domain.NoteBacklink;
import com.shiguangji.business.domain.NoteLink;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.common.utils.StringUtils;

/**
 * 笔记互链（0.0.5 FR-005-1/2/3）的纯函数工具：等长掩码、[[标题]] 抽取、目标解析、反链精确校验。
 *
 * <p>「哪些 [[...]] 算链接、指向哪篇」只在服务端判定（架构总原则 1），前端只按
 * {@link NoteLink#getRaw()} 做字面量替换。判定逻辑全部收敛为本类的静态方法，
 * 可脱离数据库单测（沿 AppReviewServiceImpl.aggregateTags 的可测性先例）。</p>
 *
 * <p>核心手段是<b>等长掩码</b>：把不参与解析的区间（代码块、行内代码、链接文字与 URL、
 * 图片、引用式链接、HTML 标签）逐字符替换为空格、保留换行，再在掩码文本上跑正则——
 * 命中下标与原文对齐，反链片段可直接按掩码下标切原文。</p>
 *
 * @author shiguangji
 */
public final class SgjNoteLinkUtils {

    /** 互链标记：内层不允许出现 [ ] 与换行（含这些字符的标记按普通文本处理，不设转义） */
    private static final String TOKEN_OPEN = "[[";

    private static final String TOKEN_CLOSE = "]]";

    /** 行内代码（反引号 run 成对，允许跨行）：里面的 [[...]] 不解析 */
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("(`+)([\\s\\S]*?)\\1");

    /** 行内链接与图片：链接文字与 URL 都不解析（由括号配平扫描处理，见 {@link #maskBracketLinks}） */

    /** HTML 标签整体不解析 */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>\\n]+>");

    /** 反链片段前后各取的字符数 */
    private static final int SNIPPET_RADIUS = 28;

    private SgjNoteLinkUtils() {
    }

    /**
     * 返回与原文等长的掩码文本：不参与互链解析的区间替换为空格（保留换行）。
     * 掩码顺序：围栏代码 → 行内代码 → 链接/图片/引用式链接 → HTML 标签；
     * 任一顺序都是保守的（只会掩得更多），此顺序让代码内的伪链接语法先被保护。
     */
    public static String maskNonLinkText(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        char[] chars = body.toCharArray();
        maskFences(chars);
        maskMatches(chars, INLINE_CODE_PATTERN);
        maskBracketLinks(chars);
        maskMatches(chars, HTML_TAG_PATTERN);
        return new String(chars);
    }

    /**
     * 抽取正文里的互链标记（在掩码文本上匹配、按 distinct raw 去重、保留首现下标）。
     * raw 保留括号内空白原样（前端做字面量替换要靠它），title 为去空白后的标题。
     */
    public static List<NoteToken> extractNoteTokens(String body) {
        if (StringUtils.isEmpty(body)) {
            return Collections.emptyList();
        }
        String masked = maskNonLinkText(body);
        Map<String, NoteToken> byRaw = new LinkedHashMap<>();
        scanTokens(masked, (start, end, title) -> {
            if (title.isEmpty()) {
                return;
            }
            String raw = body.substring(start, end);
            byRaw.putIfAbsent(raw, new NoteToken(raw, title, start));
        });
        return new ArrayList<>(byRaw.values());
    }

    /**
     * 把候选笔记按标题逐字分组后解析每个 token 的落点。
     * 候选来自 SQL 的 title in (...)——库排序规则不区分大小写，逐字判定（含大小写）必须在这里收口。
     */
    public static List<NoteLink> resolveLinks(List<NoteToken> tokens, List<SgjNote> refs) {
        if (tokens == null || tokens.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<SgjNote>> byTitle = new HashMap<>();
        if (refs != null) {
            for (SgjNote ref : refs) {
                byTitle.computeIfAbsent(ref.getTitle(), key -> new ArrayList<>()).add(ref);
            }
        }
        List<NoteLink> links = new ArrayList<>();
        for (NoteToken token : tokens) {
            List<SgjNote> hits = byTitle.getOrDefault(token.getTitle(), Collections.emptyList());
            if (hits.size() == 1) {
                links.add(NoteLink.resolved(token.getRaw(), token.getTitle(), hits.get(0).getNoteId()));
            } else if (hits.isEmpty()) {
                links.add(NoteLink.broken(token.getRaw(), token.getTitle(), NoteLink.REASON_NOT_FOUND));
            } else {
                links.add(NoteLink.broken(token.getRaw(), token.getTitle(), NoteLink.REASON_AMBIGUOUS));
            }
        }
        return links;
    }

    /** 候选中与目标标题逐字相等的数量（同名计数，含本篇由调用方的查询范围决定） */
    public static int countExactTitle(List<SgjNote> refs, String title) {
        if (refs == null || StringUtils.isEmpty(title)) {
            return 0;
        }
        int count = 0;
        for (SgjNote ref : refs) {
            if (title.equals(ref.getTitle())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 逐篇校验反链候选：正文经 buildBody（与详情渲染同口径）后掩码，
     * 存在 token.title 逐字等于目标标题才算真引用——代码块里的 [[...]] 在这里被剔除。
     */
    public static List<NoteBacklink> buildBacklinks(List<SgjNote> candidates, String title) {
        List<NoteBacklink> backlinks = new ArrayList<>();
        if (candidates == null || candidates.isEmpty() || StringUtils.isEmpty(title)) {
            return backlinks;
        }
        for (SgjNote candidate : candidates) {
            String body = SgjNoteUtils.buildBody(candidate.getContent(), candidate.getTitle());
            int[] hit = countTitleReference(body, title);
            if (hit[0] <= 0) {
                continue;
            }
            backlinks.add(new NoteBacklink(candidate.getNoteId(), candidate.getTitle(),
                    candidate.getUpdateTime() != null ? candidate.getUpdateTime() : candidate.getCreateTime(),
                    snippetAround(body, hit[1], hit[2]), (long) hit[0]));
        }
        return backlinks;
    }

    /**
     * 统计正文中指向目标标题的有效引用：返回 {命中次数, 首现 start, 首现 end}，
     * 无命中时 start/end 为 -1。下标基于原文（掩码等长）。
     */
    public static int[] countTitleReference(String body, String title) {
        if (StringUtils.isEmpty(body) || StringUtils.isEmpty(title)) {
            return new int[] { 0, -1, -1 };
        }
        String masked = maskNonLinkText(body);
        int[] result = { 0, -1, -1 };
        scanTokens(masked, (start, end, tokenTitle) -> {
            if (title.equals(tokenTitle)) {
                if (result[1] < 0) {
                    result[1] = start;
                    result[2] = end;
                }
                result[0]++;
            }
        });
        return result;
    }

    /** 按下标切原文片段：前后各约 {@link #SNIPPET_RADIUS} 字符，折叠空白，越界侧补省略号 */
    public static String snippetAround(String body, int start, int end) {
        if (body == null || start < 0 || end <= start || start >= body.length()) {
            return "";
        }
        int from = Math.max(0, start - SNIPPET_RADIUS);
        int to = Math.min(body.length(), end + SNIPPET_RADIUS);
        String snippet = body.substring(from, to).replaceAll("\\s+", " ").trim();
        if (snippet.isEmpty()) {
            return "";
        }
        return (from > 0 ? "…" : "") + snippet + (to < body.length() ? "…" : "");
    }

    /** LIKE 通配符转义（SQL 侧统一 escape '\\'）：\ % _ */
    public static String escapeLike(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** 围栏代码：按行扫描状态机（``` 或 ~~~，容忍缩进与信息串；未闭合则掩到文末） */
    private static void maskFences(char[] chars) {
        char fenceChar = 0;
        int fenceLen = 0;
        int lineStart = 0;
        for (int i = 0; i <= chars.length; i++) {
            if (i < chars.length && chars[i] != '\n') {
                continue;
            }
            String trimmed = new String(chars, lineStart, i - lineStart).trim();
            if (fenceChar == 0) {
                String marker = fenceMarker(trimmed);
                if (marker != null) {
                    fenceChar = marker.charAt(0);
                    fenceLen = marker.length();
                    maskRange(chars, lineStart, i);
                }
            } else {
                maskRange(chars, lineStart, i);
                if (isFenceClose(trimmed, fenceChar, fenceLen)) {
                    fenceChar = 0;
                }
            }
            lineStart = i + 1;
        }
    }

    /** 行首围栏标记（≥3 个相同反引号/波浪线），返回该标记本身；不是围栏返回 null */
    private static String fenceMarker(String trimmedLine) {
        if (trimmedLine.isEmpty()) {
            return null;
        }
        char first = trimmedLine.charAt(0);
        if (first != '`' && first != '~') {
            return null;
        }
        int len = 0;
        while (len < trimmedLine.length() && trimmedLine.charAt(len) == first) {
            len++;
        }
        return len >= 3 ? trimmedLine.substring(0, len) : null;
    }

    /** 结束围栏：整行仅由同一围栏字符组成且长度不小于开栏长度 */
    private static boolean isFenceClose(String trimmedLine, char fenceChar, int fenceLen) {
        if (trimmedLine.length() < fenceLen) {
            return false;
        }
        for (int i = 0; i < trimmedLine.length(); i++) {
            if (trimmedLine.charAt(i) != fenceChar) {
                return false;
            }
        }
        return true;
    }

    private static void maskMatches(char[] chars, Pattern pattern) {
        Matcher matcher = pattern.matcher(new String(chars));
        while (matcher.find()) {
            maskRange(chars, matcher.start(), matcher.end());
        }
    }

    /** 区间内非换行字符替换为空格（保持长度与换行位置，下标才能与原文对齐） */
    private static void maskRange(char[] chars, int from, int to) {
        for (int i = from; i < to && i < chars.length; i++) {
            if (chars[i] != '\n') {
                chars[i] = ' ';
            }
        }
    }

    /**
     * 行内链接 [文](url)、图片 ![文](url) 与引用式链接 [文][ref]：整段掩掉。
     * 以 "](" / "][" 定位、向左做括号配平找起始 "["——配平让链接文字里嵌 [[...]]
     * （如 [看 [[x]]](u)）时也能整体掩掉，扁平正则做不到这一点。
     * 括号不配平（孤立的 "[["）时跳过，不影响其余文本。
     */
    private static void maskBracketLinks(char[] chars) {
        int i = 0;
        while (i < chars.length - 1) {
            boolean inline = chars[i] == ']' && chars[i + 1] == '(';
            boolean ref = chars[i] == ']' && chars[i + 1] == '[';
            if (!inline && !ref) {
                i++;
                continue;
            }
            int opener = matchOpenBracket(chars, i);
            if (opener < 0) {
                // 括号不配平：这不是链接，跳过这个 "]]"
                i += 2;
                continue;
            }
            int end = inline ? closeParen(chars, i + 2) : closeRefLabel(chars, i + 1);
            if (end < 0) {
                i += 2;
                continue;
            }
            int from = opener > 0 && chars[opener - 1] == '!' ? opener - 1 : opener;
            maskRange(chars, from, end);
            i = end;
        }
    }

    /** 从 closeIdx（一个 ']'）向左做括号配平，返回配对的 '[' 下标；不配平返回 -1 */
    private static int matchOpenBracket(char[] chars, int closeIdx) {
        int depth = 1;
        for (int j = closeIdx - 1; j >= 0; j--) {
            if (chars[j] == ']') {
                depth++;
            } else if (chars[j] == '[') {
                depth--;
                if (depth == 0) {
                    return j;
                }
            }
        }
        return -1;
    }

    /** 从 from（'(' 之后）向右找配对的 ')'（容忍一层嵌套括号），返回其下标 +1；未闭合返回 -1 */
    private static int closeParen(char[] chars, int from) {
        int depth = 1;
        for (int j = from; j < chars.length; j++) {
            if (chars[j] == '\n') {
                // 行内链接不跨行，遇到换行按未闭合处理，避免吞掉后文
                return -1;
            }
            if (chars[j] == '(') {
                depth++;
            } else if (chars[j] == ')') {
                depth--;
                if (depth == 0) {
                    return j + 1;
                }
            }
        }
        return -1;
    }

    /** 从 labelOpen（引用标签 '[' 的下标）向右找标签闭合 ']'，返回其下标 +1；同行未闭合返回 -1 */
    private static int closeRefLabel(char[] chars, int labelOpen) {
        if (labelOpen >= chars.length || chars[labelOpen] != '[') {
            return -1;
        }
        for (int j = labelOpen + 1; j < chars.length; j++) {
            if (chars[j] == '\n') {
                return -1;
            }
            if (chars[j] == ']') {
                return j + 1;
            }
        }
        return -1;
    }

    /**
     * 在掩码文本上扫描合法的 [[标题]] 标记区间。规则（需求 §3.3.1）：
     * 未闭合的 [[ 之后整段按普通文本；空内层（[[]]）跳过；
     * 嵌套括号（[[a [[b]] c]]，首个 ]] 之前又出现 [[）整段按普通文本，从该 ]] 之后继续。
     */
    private static void scanTokens(String masked, TokenVisitor visitor) {
        int i = 0;
        while (i < masked.length() - 1) {
            int open = masked.indexOf(TOKEN_OPEN, i);
            if (open < 0) {
                return;
            }
            int close = masked.indexOf(TOKEN_CLOSE, open + TOKEN_OPEN.length());
            if (close < 0) {
                // 未闭合：这个 opener 之后全部按普通文本
                return;
            }
            String inner = masked.substring(open + TOKEN_OPEN.length(), close);
            if (inner.indexOf('[') >= 0 || inner.indexOf('\n') >= 0) {
                // 嵌套 / 跨行：本 opener 不成立，整段按普通文本，从闭合处之后继续
                i = close + TOKEN_CLOSE.length();
                continue;
            }
            visitor.visit(open, close + TOKEN_CLOSE.length(), inner.trim());
            i = close + TOKEN_CLOSE.length();
        }
    }

    /** scanTokens 的回调：start/end 为含括号的区间下标，title 为去空白后的内层 */
    private interface TokenVisitor {

        void visit(int start, int end, String title);
    }

    /**
     * 正文里抽出的一个互链标记：raw 为原文片段（含括号内空白原样），title 为去空白标题。
     */
    public static final class NoteToken {

        private final String raw;

        private final String title;

        private final int start;

        public NoteToken(String raw, String title, int start) {
            this.raw = raw;
            this.title = title;
            this.start = start;
        }

        public String getRaw() {
            return raw;
        }

        public String getTitle() {
            return title;
        }

        public int getStart() {
            return start;
        }
    }
}
