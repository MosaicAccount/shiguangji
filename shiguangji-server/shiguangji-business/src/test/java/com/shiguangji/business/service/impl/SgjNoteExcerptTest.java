package com.shiguangji.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.shiguangji.business.utils.SgjNoteUtils;

/**
 * 前台摘要与详情正文生成单元测试（不依赖 Spring 容器与数据库）。
 *
 * 覆盖 issue #31 验收：语法剥离、命中窗口居中、无命中取开头、空正文、很短的正文、
 * 命中在开头或结尾、整篇代码块、标题去重、词边界截断。
 * 前言（YAML front-matter）跳过：列表侧由 SQL 负责（SgjNoteMapper.xml fmEnd 片段），
 * 详情侧由 {@link SgjNoteServiceImpl#buildBody} 负责——两者规则必须一致，故此处覆盖 Java 侧
 */
class SgjNoteExcerptTest
{
    // ===== stripMarkdown：各种语法剥离 =====

    @Test
    void stripRemovesFencedCodeBlocksEntirely()
    {
        String out = SgjNoteUtils.stripMarkdown("前文\n```java\nint a = 1;\n```\n后文");
        assertEquals("前文 后文", out);
    }

    @Test
    void stripKeepsInlineCodeContentWithoutBackticks()
    {
        assertEquals("用 npm run dev 启动", SgjNoteUtils.stripMarkdown("用 `npm run dev` 启动"));
    }

    @Test
    void stripRemovesImagesAndKeepsLinkText()
    {
        assertEquals("看 这个 截图", SgjNoteUtils.stripMarkdown("看 [这个](https://example.com) 截图 ![logo](/a.png)"));
    }

    @Test
    void stripRemovesHeadingQuoteListPrefixes()
    {
        String out = SgjNoteUtils.stripMarkdown("## 标题\n> 引用一句\n- 无序项\n1. 有序项\n- [ ] 待办\n- [x] 完成");
        assertEquals("标题 引用一句 无序项 有序项 待办 完成", out);
    }

    @Test
    void stripRemovesTableSeparatorRowsAndMarkers()
    {
        // 仅分隔线整行移除；数据行的管道符不在剥离范围（issue 只要求去掉分隔线）
        String out = SgjNoteUtils.stripMarkdown("| 列A | 列B |\n|---|---|\n| a | b |");
        assertEquals("| 列A | 列B | | a | b |", out);
    }

    @Test
    void stripRemovesEmphasisMarkersButKeepsSnakeCase()
    {
        assertEquals("加粗 斜体 删除 bold user_name 字段", SgjNoteUtils.stripMarkdown("**加粗** *斜体* ~~删除~~ __bold__ user_name 字段"));
    }

    @Test
    void stripRemovesHtmlTagsAndCollapsesWhitespace()
    {
        // 标签以空格代替，避免相邻词粘连
        assertEquals("一段 带标签 文字", SgjNoteUtils.stripMarkdown("<p>一段</p>\n\n  带标签<br/>文字"));
    }

    // ===== buildBody：详情正文的前言剥离与标题去重（须与 SQL fmEnd 口径一致） =====

    @Test
    void bodyWithoutFrontMatterStaysUntouched()
    {
        assertEquals("正文内容", SgjNoteUtils.buildBody("正文内容", "别的标题"));
    }

    @Test
    void bodyStripsFrontMatterBlock()
    {
        String content = "---\ntitle: x\ntags: a,b\n---\n正文内容";
        assertEquals("正文内容", SgjNoteUtils.buildBody(content, "别的标题"));
    }

    @Test
    void bodyStripsFrontMatterThenDeduplicatesTitleH1()
    {
        String content = "---\ntags: a\n---\n# 标题\n正文";
        assertEquals("正文", SgjNoteUtils.buildBody(content, "标题"));
    }

    @Test
    void bodyKeepsCrlfFrontMatterResidualNewline()
    {
        // 镜像 SQL 的 locate(...) + 5 盲跳：CRLF 前言剥完后仍残留一个 \n，不在此处修（修它要同时改 SQL）
        String content = "---\r\ntitle: x\r\n---\r\n正文";
        assertEquals("\n正文", SgjNoteUtils.buildBody(content, "别的标题"));
    }

    @Test
    void bodyKeepsWholeContentWhenFrontMatterUnclosed()
    {
        String content = "---\ntitle: x\n正文";
        assertEquals(content, SgjNoteUtils.buildBody(content, "别的标题"));
    }

    @Test
    void bodyDeduplicatesLeadingTitleH1()
    {
        assertEquals("正文", SgjNoteUtils.buildBody("# 标题\n正文", "标题"));
    }

    @Test
    void bodyKeepsLeadingH1WithDifferentText()
    {
        assertEquals("# 别的\n正文", SgjNoteUtils.buildBody("# 别的\n正文", "标题"));
    }

    @Test
    void bodyTitleDedupIgnoresCase()
    {
        // SQL 侧的等值比较走 _ci 排序规则（大小写不敏感），Java 侧用 regionMatches 对齐
        assertEquals("正文", SgjNoteUtils.buildBody("# hello\n正文", "Hello"));
    }

    @Test
    void bodyToleratesNullAndEmptyInput()
    {
        assertNull(SgjNoteUtils.buildBody(null, "标题"));
        assertEquals("", SgjNoteUtils.buildBody("", "标题"));
        assertEquals("正文", SgjNoteUtils.buildBody("正文", null));
        assertEquals("正文", SgjNoteUtils.buildBody("正文", ""));
    }
}
