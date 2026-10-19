package com.shiguangji.business.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shiguangji.business.domain.NoteBacklink;
import com.shiguangji.business.domain.NoteLink;
import com.shiguangji.business.domain.SgjNote;
import com.shiguangji.business.utils.SgjNoteLinkUtils.NoteToken;

/**
 * 互链解析纯函数单测（0.0.5，不依赖数据库；用例对应 link-testcases.md §A）。
 *
 * @author shiguangji
 */
class SgjNoteLinkUtilsTest {

    private static SgjNote ref(long noteId, String title) {
        SgjNote note = new SgjNote();
        note.setNoteId(noteId);
        note.setTitle(title);
        return note;
    }

    private static SgjNote candidate(long noteId, String title, String content) {
        SgjNote note = ref(noteId, title);
        note.setContent(content);
        note.setCreateTime(new Date());
        return note;
    }

    // ------------------------------------------------------------------
    // 掩码：哪些位置的 [[...]] 不解析
    // ------------------------------------------------------------------

    @Test
    void inlineCodeIsMasked() {
        List<NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens("说明 `[[不是链接]]`，但 [[是链接]] 是");
        assertThat(tokens).extracting(NoteToken::getTitle).containsExactly("是链接");
        assertThat(SgjNoteLinkUtils.countTitleReference("说明 `[[不是链接]]` 尾", "不是链接")[0]).isZero();
    }

    @Test
    void fencedCodeIsMasked() {
        String body = "前文\n```\n[[代码里]]\n```\n后文 [[代码外]]";
        assertThat(SgjNoteLinkUtils.extractNoteTokens(body))
                .extracting(NoteToken::getTitle).containsExactly("代码外");

        // 波浪线围栏与未闭合围栏（到文末都算代码）
        assertThat(SgjNoteLinkUtils.extractNoteTokens("~~~\n[[波浪围栏]]\n~~~")).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("```\n[[未闭合]]")).isEmpty();
    }

    @Test
    void linksImagesAndRefLinksAreMasked() {
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[看 [[x]]](http://a)")).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("![alt [[y]]](u)")).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[文 [[z]]][ref]")).isEmpty();
    }

    @Test
    void htmlTagsAreMaskedButTextIsNot() {
        assertThat(SgjNoteLinkUtils.extractNoteTokens("<div>[[a]]</div>"))
                .extracting(NoteToken::getTitle).containsExactly("a");
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[b]]<br>"))
                .extracting(NoteToken::getTitle).containsExactly("b");
    }

    @Test
    void maskKeepsLengthAndNewlines() {
        String body = "段落\n```js\nconst a = 1;\n```\n尾段 `code` [[链]] <br>";
        String masked = SgjNoteLinkUtils.maskNonLinkText(body);
        assertThat(masked).hasSize(body.length());
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                assertThat(masked.charAt(i)).as("换行位置应保持不变，下标才能对齐").isEqualTo('\n');
            }
        }
        // 掩码文本上只剩普通文本里的 [[链]]
        assertThat(masked).contains("[[链]]").doesNotContain("code").doesNotContain("const");
    }

    // ------------------------------------------------------------------
    // token 形态
    // ------------------------------------------------------------------

    @Test
    void innerWhitespaceIsTrimmedButRawKept() {
        List<NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens("[[ 标题 ]]");
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTitle()).isEqualTo("标题");
        assertThat(tokens.get(0).getRaw()).isEqualTo("[[ 标题 ]]");
    }

    @Test
    void illegalFormsStayPlainText() {
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[]]")).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[未闭合")).isEmpty();
        // 嵌套括号：整段按普通文本，不得把内层 [[b]] 单独拎出来成链
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[a [[b]] c]]")).isEmpty();
        // 三连括号同理
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[[x]]]")).isEmpty();
        // 标题内换行不成立
        assertThat(SgjNoteLinkUtils.extractNoteTokens("[[跨\n行]]")).isEmpty();
    }

    @Test
    void tokensAreDedupedByRaw() {
        List<NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens("[[甲]] 中段 [[甲]] 尾 [[ 乙 ]]");
        assertThat(tokens).extracting(NoteToken::getTitle).containsExactly("甲", "乙");
        // 同 raw 多次出现只留首现
        assertThat(tokens.get(0).getStart()).isEqualTo(0);
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertThat(SgjNoteLinkUtils.extractNoteTokens(null)).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("")).isEmpty();
        assertThat(SgjNoteLinkUtils.extractNoteTokens("普通正文")).isEmpty();
    }

    // ------------------------------------------------------------------
    // 目标解析（三态 + 大小写）
    // ------------------------------------------------------------------

    @Test
    void resolveLinksThreeStates() {
        List<NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens("[[唯一]] [[没有]] [[重名]]");
        List<SgjNote> refs = Arrays.asList(ref(1, "唯一"), ref(2, "重名"), ref(3, "重名"));

        List<NoteLink> links = SgjNoteLinkUtils.resolveLinks(tokens, refs);
        assertThat(links).hasSize(3);
        assertThat(links.get(0).getNoteId()).isEqualTo(1L);
        assertThat(links.get(0).getReason()).isNull();
        assertThat(links.get(1).getNoteId()).isNull();
        assertThat(links.get(1).getReason()).isEqualTo(NoteLink.REASON_NOT_FOUND);
        assertThat(links.get(2).getNoteId()).isNull();
        assertThat(links.get(2).getReason()).isEqualTo(NoteLink.REASON_AMBIGUOUS);
    }

    @Test
    void caseSensitiveTitleMatch() {
        // 库排序规则不区分大小写，SQL 候选会把 vue/VUE 一并捞回来；
        // 逐字分组后它们各成一组，token [[Vue]] 仍唯一命中——不会并入、也不会误降级
        List<NoteToken> tokens = SgjNoteLinkUtils.extractNoteTokens("[[Vue]]");
        List<NoteLink> nearCase = SgjNoteLinkUtils.resolveLinks(tokens,
                Arrays.asList(ref(1, "Vue"), ref(2, "vue")));
        assertThat(nearCase.get(0).getNoteId()).isEqualTo(1L);
        assertThat(nearCase.get(0).getReason()).isNull();

        List<NoteLink> upperCase = SgjNoteLinkUtils.resolveLinks(tokens,
                Arrays.asList(ref(1, "Vue"), ref(2, "VUE")));
        assertThat(upperCase.get(0).getNoteId()).isEqualTo(1L);
    }

    @Test
    void resolveLinksHandlesEmptyRefs() {
        List<NoteLink> links = SgjNoteLinkUtils.resolveLinks(
                SgjNoteLinkUtils.extractNoteTokens("[[x]]"), null);
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getReason()).isEqualTo(NoteLink.REASON_NOT_FOUND);
        assertThat(SgjNoteLinkUtils.resolveLinks(Collections.emptyList(), null)).isEmpty();
    }

    @Test
    void countExactTitleMatchesCaseSensitively() {
        List<SgjNote> refs = Arrays.asList(ref(1, "周记"), ref(2, "周记"), ref(3, "周 记"));
        assertThat(SgjNoteLinkUtils.countExactTitle(refs, "周记")).isEqualTo(2);
        assertThat(SgjNoteLinkUtils.countExactTitle(refs, "周 记")).isEqualTo(1);
        assertThat(SgjNoteLinkUtils.countExactTitle(null, "x")).isZero();
    }

    // ------------------------------------------------------------------
    // 反链精确校验与片段
    // ------------------------------------------------------------------

    @Test
    void codeBlockReferenceDoesNotCount() {
        String source = "开头\n```\n[[目标]]\n```\n结尾";
        assertThat(SgjNoteLinkUtils.countTitleReference(source, "目标")[0]).isZero();

        List<NoteBacklink> backlinks = SgjNoteLinkUtils.buildBacklinks(
                Collections.singletonList(candidate(9L, "来源", source)), "目标");
        assertThat(backlinks).isEmpty();
    }

    @Test
    void backlinkCountsHitsAndBuildsSnippet() {
        String source = "第一段 [[目标]] 中间文字 第二处 [[ 目标 ]] 结尾";
        List<NoteBacklink> backlinks = SgjNoteLinkUtils.buildBacklinks(
                Collections.singletonList(candidate(9L, "来源笔记", source)), "目标");
        assertThat(backlinks).hasSize(1);
        NoteBacklink backlink = backlinks.get(0);
        assertThat(backlink.getNoteId()).isEqualTo(9L);
        assertThat(backlink.getHitCount()).isEqualTo(2L);
        // 片段取自原文（含括号内空白原样），首现前后文可见
        assertThat(backlink.getSnippet()).contains("[[目标]]");
        assertThat(backlink.getSnippet()).contains("第一段");
    }

    @Test
    void snippetTruncatesWithEllipsisAndSurvivesEdges() {
        String body = "起".repeat(80) + "[[目标]]" + "终".repeat(80);
        String snippet = SgjNoteLinkUtils.snippetAround(body, 80, 80 + "[[目标]]".length());
        assertThat(snippet).startsWith("…").endsWith("…").contains("[[目标]]");

        // token 在开头：无前省略号；越界下标返回空串
        assertThat(SgjNoteLinkUtils.snippetAround("[[目标]]尾", 0, 6)).startsWith("[[目标]]");
        assertThat(SgjNoteLinkUtils.snippetAround(body, -1, 3)).isEmpty();
    }

    @Test
    void buildBacklinksSkipsNonReferences() {
        List<SgjNote> candidates = Arrays.asList(
                candidate(1L, "无引用", "正文里没有标记"),
                candidate(2L, "别的目标", "引用 [[别的东西]]"));
        assertThat(SgjNoteLinkUtils.buildBacklinks(candidates, "目标")).isEmpty();
        assertThat(SgjNoteLinkUtils.buildBacklinks(null, "目标")).isEmpty();
        assertThat(SgjNoteLinkUtils.buildBacklinks(candidates, "")).isEmpty();
    }

    // ------------------------------------------------------------------
    // LIKE 转义
    // ------------------------------------------------------------------

    @Test
    void likeWildcardsAreEscaped() {
        assertThat(SgjNoteLinkUtils.escapeLike("50%")).isEqualTo("50\\%");
        assertThat(SgjNoteLinkUtils.escapeLike("a_b")).isEqualTo("a\\_b");
        assertThat(SgjNoteLinkUtils.escapeLike("C:\\x")).isEqualTo("C:\\\\x");
        assertThat(SgjNoteLinkUtils.escapeLike(null)).isNull();
        assertThat(SgjNoteLinkUtils.escapeLike("普通")).isEqualTo("普通");
    }
}
