package com.shiguangji.business.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 前台摘要生成单元测试（不依赖 Spring 容器与数据库）。
 *
 * 覆盖 issue #31 验收：语法剥离、命中窗口居中、无命中取开头、空正文、很短的正文、
 * 命中在开头或结尾、整篇代码块、标题去重、词边界截断。
 * 前言（YAML front-matter）跳过由 SQL 负责（SgjNoteMapper.xml fmEnd 片段），不在本测试范围
 */
class SgjNoteExcerptTest
{
    // ===== stripMarkdown：各种语法剥离 =====

    @Test
    void stripRemovesFencedCodeBlocksEntirely()
    {
        String out = SgjNoteServiceImpl.stripMarkdown("前文\n```java\nint a = 1;\n```\n后文");
        assertEquals("前文 后文", out);
    }

    @Test
    void stripKeepsInlineCodeContentWithoutBackticks()
    {
        assertEquals("用 npm run dev 启动", SgjNoteServiceImpl.stripMarkdown("用 `npm run dev` 启动"));
    }

    @Test
    void stripRemovesImagesAndKeepsLinkText()
    {
        assertEquals("看 这个 截图", SgjNoteServiceImpl.stripMarkdown("看 [这个](https://example.com) 截图 ![logo](/a.png)"));
    }

    @Test
    void stripRemovesHeadingQuoteListPrefixes()
    {
        String out = SgjNoteServiceImpl.stripMarkdown("## 标题\n> 引用一句\n- 无序项\n1. 有序项\n- [ ] 待办\n- [x] 完成");
        assertEquals("标题 引用一句 无序项 有序项 待办 完成", out);
    }

    @Test
    void stripRemovesTableSeparatorRowsAndMarkers()
    {
        // 仅分隔线整行移除；数据行的管道符不在剥离范围（issue 只要求去掉分隔线）
        String out = SgjNoteServiceImpl.stripMarkdown("| 列A | 列B |\n|---|---|\n| a | b |");
        assertEquals("| 列A | 列B | | a | b |", out);
    }

    @Test
    void stripRemovesEmphasisMarkersButKeepsSnakeCase()
    {
        assertEquals("加粗 斜体 删除 bold user_name 字段", SgjNoteServiceImpl.stripMarkdown("**加粗** *斜体* ~~删除~~ __bold__ user_name 字段"));
    }

    @Test
    void stripRemovesHtmlTagsAndCollapsesWhitespace()
    {
        // 标签以空格代替，避免相邻词粘连
        assertEquals("一段 带标签 文字", SgjNoteServiceImpl.stripMarkdown("<p>一段</p>\n\n  带标签<br/>文字"));
    }

    // ===== buildExcerpt：截窗与回退 =====

    @Test
    void windowExcerptKeepsEllipsesAndHitInside()
    {
        String fragment = "…" + "前".repeat(45) + "命中词" + "后".repeat(45) + "…";
        String out = SgjNoteServiceImpl.buildExcerpt(fragment, "标题");
        assertTrue(out.startsWith("…"), "片段外有正文应保留前省略号");
        assertTrue(out.endsWith("…"), "片段外有正文应保留后省略号");
        assertTrue(out.contains("命中词"), "命中词必须保留在摘要里");
    }

    @Test
    void prefixExcerptStartsWithoutEllipsis()
    {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 30; i++)
        {
            body.append("第").append(i).append("句这是一段足够长的中文内容。");
        }
        String out = SgjNoteServiceImpl.buildExcerpt(body.toString(), "标题");
        assertFalse(out.startsWith("…"), "开头摘要不带前省略号");
        assertTrue(out.endsWith("…"), "超长正文截断应有后省略号");
        assertTrue(out.codePointCount(0, out.length() - 1) <= 130, "摘要约 120 字（词边界最多 +10）");
    }

    @Test
    void nullAndEmptyContentYieldEmptyExcerpt()
    {
        assertEquals("", SgjNoteServiceImpl.buildExcerpt(null, "标题"));
        assertEquals("", SgjNoteServiceImpl.buildExcerpt("", "标题"));
    }

    @Test
    void shortContentReturnedWholeWithoutEllipsis()
    {
        assertEquals("很短的一段正文", SgjNoteServiceImpl.buildExcerpt("很短的一段正文", "标题"));
        String shortWithMore = "短正文";
        // SQL 拼了后省略号但内容本身完整展示
        assertEquals("短正文…", SgjNoteServiceImpl.buildExcerpt(shortWithMore + "…", "标题"));
    }

    @Test
    void hitAtVeryStartKeepsNoLeadingEllipsis()
    {
        String fragment = "命中词开头的这段正文被 SQL 截窗且未拼前省略号";
        String out = SgjNoteServiceImpl.buildExcerpt(fragment, "标题");
        assertFalse(out.startsWith("…"));
        assertTrue(out.startsWith("命中词"));
    }

    @Test
    void hitAtVeryEndKeepsTrailingState()
    {
        String fragment = "…" + "前".repeat(60) + "结尾命中";
        String out = SgjNoteServiceImpl.buildExcerpt(fragment, "标题");
        assertTrue(out.startsWith("…"));
        assertTrue(out.endsWith("结尾命中"), "片段到正文结尾时不再补后省略号");
    }

    @Test
    void codeBlockOnlyContentFallsBackToRawText()
    {
        String fragment = "```java\nint a = 1;\nint b = 2;\n```";
        String out = SgjNoteServiceImpl.buildExcerpt(fragment, "标题");
        assertEquals("```java int a = 1; int b = 2; ```", out);
    }

    @Test
    void excerptDeduplicatesLeadingTitleLine()
    {
        String fragment = "# MySQL 全文索引\n正文从全文索引讲起，足够长的一段话继续往下说。";
        assertEquals("正文从全文索引讲起，足够长的一段话继续往下说。",
                SgjNoteServiceImpl.buildExcerpt(fragment, "MySQL 全文索引"));
    }

    @Test
    void titleOnlyLineYieldsEmptyExcerpt()
    {
        assertEquals("", SgjNoteServiceImpl.buildExcerpt("# 同名标题", "同名标题"));
    }

    @Test
    void windowFragmentSkipsTitleDedup()
    {
        String fragment = "…" + "x".repeat(45) + "同名标题" + "y".repeat(45) + "…";
        String out = SgjNoteServiceImpl.buildExcerpt(fragment, "同名标题");
        assertTrue(out.contains("同名标题"), "正文中部的同名文字不是标题行，不得移除");
    }

    @Test
    void truncationExtendsToEnglishWordBoundary()
    {
        // 120 字截断点落在 "introduction"（115-126 位）单词中间时向后扩到词界，而非拦腰截成 introduct
        String head = "a".repeat(114) + " introduction";
        StringBuilder body = new StringBuilder(head);
        while (body.length() < 200)
        {
            body.append("中文内容占位。");
        }
        String out = SgjNoteServiceImpl.buildExcerpt(body.toString(), "标题");
        assertTrue(out.startsWith(head), "单词被完整保留而非拦腰截断");
        assertTrue(out.endsWith("…"));
    }

    @Test
    void emptyFragmentWithOnlyEllipsisYieldsEmpty()
    {
        // 正文为空的行经 SQL concat 后可能只剩省略号占位
        assertEquals("", SgjNoteServiceImpl.buildExcerpt("…", "标题"));
    }
}
