package com.shiguangji.business.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Strings;

import com.shiguangji.business.constants.SgjNoteConstants;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.common.utils.StringUtils;

import jakarta.validation.constraints.NotNull;

/**
 * @author JLian
 * @date 2026年09月23日
 */
public class SgjNoteUtils {

    /** 截断落在英文单词中间时最多向后扩的字符数（切到最近的空白或标点） */
    private static final int WORD_BOUNDARY_MAX_EXTRA = 10;

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile(
            "\\A\\x{FEFF}?---[ \\t]*\\r?\\n" + // 开头 ---，可选 BOM
                    "(?:.*\\r?\\n)*?" + // YAML 内容，非贪婪
                    "(?:---|\\.\\.\\.)[ \\t]*" + // 结束行 --- 或 ...
                    "(?:\\r?\\n(?:[ \\t]*\\r?\\n)*)?" // 吃掉一个换行 + 后续的空行（可选，兼容字符串末尾）
    );

    // 正文标题匹配规则，例如 # Hello World
    private static final Pattern FIRST_LINE_TITLE = Pattern
            .compile("^[ \\t]*#{1,6}(?!#)[ \\t]*([^\\r\\n]*)(?:\\r?\\n|$)");

    /**
     * 清洗检索关键词：剔除布尔模式的运算符字符后再传参。
     * 运算符会被当语法解释（如前导 - 使整个查询恒为 0 行），包引号屏蔽不掉（ngram 丢符号）；
     * ngram 分词本就忽略这些符号，剔除不损失匹配能力。清洗后为空白则视为未输入，不参与过滤。
     * 同时按空白切词（keywordWords），供前台命中窗口定位与命中计数 SQL 使用
     */
    public static void cleanKeyword(SgjNote sgjNote) {
        String keyword = sgjNote.getKeyword();
        if (StringUtils.isEmpty(keyword)) {
            return;
        }
        String cleaned = keyword.replaceAll("[+\\-*\"()~<>@]", " ").trim();
        sgjNote.setKeyword(StringUtils.isEmpty(cleaned) ? null : cleaned);
    }

    /**
     * 根据关键词从文本中获取对应的片段，片段大小根据窗口大小来截取
     * 
     * @param body    源文本
     * @param keyword 关键词
     * @return 摘要片段
     */
    public static List<String> extractSnippets(String body, String keyword) {

        if (StringUtils.isEmpty(body)) {
            return Collections.emptyList();
        }
        int bodyLen = body.length();
        // 如果关键词为空，就只取开头
        if (StringUtils.isEmpty(keyword)) {
            return Collections.singletonList(
                    truncateAtWordBoundary(body, Math.min(bodyLen, SgjNoteConstants.EXCERPT_WINDOW_LENGTH)));
        }

        ArrayList<Integer> keyWordIndexList = new ArrayList<>();

        int startIndex = 0;
        while ((startIndex = Strings.CI.indexOf(body, keyword, startIndex)) != -1) {
            keyWordIndexList.add(startIndex);
            startIndex += keyword.length();
        }

        if (keyWordIndexList.size() == 0) {
            return Collections.emptyList();
        }

        ArrayList<String> snippetList = new ArrayList<>();
        // 判断同一个窗口的关键词，合并到一个窗口中，取同一份摘要，不重复展示摘要
        int i = 0;
        startIndex = 0;
        // 如果检索关键字超过窗口长度，或者窗口长度超过笔记内容长度，要调整窗口大小
        int windowSize = Math.min(bodyLen, Math.max(SgjNoteConstants.EXCERPT_WINDOW_LENGTH, keyword.length()));
        while (i < keyWordIndexList.size()) {
            int keywordIndex = keyWordIndexList.get(i);
            // 获取窗口最左边的位置
            int windowLeft = Math.max(startIndex, keywordIndex - windowSize / 2);
            // 获取窗口最右边的位置，当前关键词起始位置 + 剩余的窗口大小
            int windowRight = Math.min(bodyLen, windowLeft + windowSize);
            i++;
            // 找出在当前窗口的其他关键词位置
            while (i < keyWordIndexList.size()) {
                keywordIndex = keyWordIndexList.get(i);
                // 判断当前关键词的开始是否在窗口内
                if (keywordIndex >= windowRight) {
                    break;
                }

                // 如果关键词起始位置在窗口内，但是结束位置超出窗口，就移动当前窗口的右边
                if (keywordIndex + keyword.length() > windowRight) {
                    windowRight = Math.min(bodyLen, keywordIndex + keyword.length());
                }
                i++;
            }
            startIndex = windowRight;
            snippetList.add(truncateAtWordBoundary(body.substring(windowLeft, windowRight), windowSize));
        }

        return snippetList;
    }

    /**
     * 截断到目标长度；截断点落在英文单词中间时最多向后扩 {@link #WORD_BOUNDARY_MAX_EXTRA}
     * 字符到最近的单词边界（中文按字符切，不受影响）
     */
    public static String truncateAtWordBoundary(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        if (!isAsciiWordChar(text.charAt(max - 1)) || !isAsciiWordChar(text.charAt(max))) {
            return text.substring(0, max);
        }
        int end = max;
        int limit = Math.min(text.length(), max + WORD_BOUNDARY_MAX_EXTRA);
        while (end < limit && isAsciiWordChar(text.charAt(end))) {
            end++;
        }
        return text.substring(0, end);
    }

    /** ASCII 字母/数字视为英文单词字符（中文按字符切，不参与单词边界扩展） */
    private static boolean isAsciiWordChar(char c) {
        return c < 128 && Character.isLetterOrDigit(c);
    }

    /**
     * 删除正文中出现的 yaml-front-format 块
     * 
     * @param body 正文
     * @return 删除掉 yaml-front-format 块的正文内容
     */
    public static String removeYamlFrontFormat(String body) {
        return FRONT_MATTER_PATTERN.matcher(body).replaceFirst("");
    }

    /**
     * 删除正文中第一行出现的标题，防止页面摘要中的标题和卡片上的标题重复
     * 
     * @param body  正文
     * @param title 标题
     * @return 删除掉第一行出现的标题后的正文内容
     */
    public static String removeDuplicatedTitle(String body, @NotNull String title) {
        if (StringUtils.isEmpty(body) || StringUtils.isEmpty(title)) {
            return body;
        }
        Matcher m = FIRST_LINE_TITLE.matcher(body);
        if (m.find() && title.trim().equals(m.group(1).trim())) {
            return body.substring(m.end()); // 直接从匹配结束处截断
        }
        return body;
    }

    /**
     * 向 yaml front-matter 中写入 / 更新 key=value。
     * 如果文本没有 front-matter，则在开头新建一个。
     * 仅支持无嵌套字段，不支持 list、map等
     * 
     * @param body  文本内容
     * @param key   yaml 的属性名称
     * @param value yaml 的属性值
     * @return 新增或更新 yaml front-matter 后的文本内容
     */
    public static String writeYaml(String body, String key, Object value) {
        if (body == null)
            body = "";
        if (key == null || key.isEmpty())
            return body;

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(body);

        String yamlValue = yamlString(value.toString());

        String newLine = key + ": " + yamlValue;

        // 1. 没有 front-matter：新建
        if (!matcher.find()) {
            return "---\n" + newLine + "\n---\n\n" + body;
        }

        String block = matcher.group(); // 整个 front-matter 块
        String rest = body.substring(matcher.end()); // 正文（可能以 \n 开头或为空）

        // 2. 去掉 BOM
        if (block.startsWith("\uFEFF")) {
            block = block.substring(1);
        }

        // 3. 去掉结尾换行，方便按行切分
        int end = block.length();
        while (end > 0 && (block.charAt(end - 1) == '\n' || block.charAt(end - 1) == '\r')) {
            end--;
        }
        block = block.substring(0, end);

        // 4. 按行切分
        // lines[0] = "---"
        // lines[length-1] = "---" 或 "..."
        // lines[1 .. length-2] = YAML 内容
        String[] lines = block.split("\\r?\\n", -1);

        Pattern keyPattern = Pattern.compile(
                "^[ \\t]*" + Pattern.quote(key) + "[ \\t]*:.*$");

        List<String> yamlLines = new ArrayList<>();
        boolean replaced = false;
        for (int i = 1; i < lines.length - 1; i++) {
            String l = lines[i];
            if (!replaced && keyPattern.matcher(l).matches()) {
                yamlLines.add(newLine); // 覆盖旧值
                replaced = true;
            } else {
                yamlLines.add(l);
            }
        }
        if (!replaced) {
            yamlLines.add(newLine); // 追加新 key
        }

        // 5. 重新拼装
        StringBuilder out = new StringBuilder(
                body.length() + key.length() + yamlValue.length() + 16);
        out.append("---\n");
        for (String l : yamlLines) {
            out.append(l).append('\n');
        }
        out.append("---\n");
        if (!rest.isEmpty()) {
            out.append(rest);
        }
        return out.toString();
    }

    /**
     * 将数据转位可写入 yaml 中的文本
     * 
     * @param value 源文本
     * @return 可写如 yaml 中的文本
     */
    private static String yamlString(String value) {
        String text = value == null ? "" : value;
        StringBuilder sb = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * 剥离 Markdown 为纯文本：围栏代码块整块移除、图片移除、链接保留文字、行内代码去反引号、
     * 加粗/斜体/删除线去标记、HTML 标签去标签、标题/引用/列表/任务列表去前缀、表格分隔行移除、
     * 连续空白折叠为单个空格
     *
     * @param text Markdown 文本，可为 null
     * @return 单行纯文本；无内容时为空串
     */
    public static String stripMarkdown(String text) {
        return collapseWhitespace(stripMarkdownLines(text));
    }

    /** 连续空白折叠为单个空格并去首尾 */
    private static String collapseWhitespace(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 剥离 Markdown，保留换行（供摘要按行做标题去重后再折叠）
     */
    public static String stripMarkdownLines(String text) {
        if (text == null || text.isEmpty()) {
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
        for (String line : s.split("\n", -1)) {
            // 表格分隔行（形如 |---|---:|）整行移除
            if (isTableSeparator(line)) {
                continue;
            }
            String t = line;
            t = t.replaceFirst("^\\s{0,3}#{1,6}\\s+", ""); // 标题
            t = t.replaceFirst("^\\s{0,3}>\\s?", ""); // 引用
            t = t.replaceFirst("^\\s{0,3}[-*+]\\s+\\[[ xX]\\]\\s+", ""); // 任务列表
            t = t.replaceFirst("^\\s{0,3}[-*+]\\s+", ""); // 无序列表
            t = t.replaceFirst("^\\s{0,3}\\d{1,9}\\.\\s+", ""); // 有序列表
            sb.append(t).append('\n');
        }
        return sb.toString();
    }

    /** 表格分隔行：整行仅由空白、|、:、- 组成且至少含一个 | 与一个 - */
    private static boolean isTableSeparator(String line) {
        String trimmed = line.trim();
        return trimmed.indexOf('|') >= 0 && trimmed.indexOf('-') >= 0 && trimmed.matches("[\\s:|-]+");
    }

}
