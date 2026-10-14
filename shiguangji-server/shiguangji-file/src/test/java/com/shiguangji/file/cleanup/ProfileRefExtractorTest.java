package com.shiguangji.file.cleanup;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 正文引用抽取的纯函数用例（无 Spring / 无 DB / 无存储）
 *
 * <p>这些用例的形状全部取自真实会出现的正文：Markdown 图片、Obsidian 短引用、引用后紧跟中文说明、
 * 百分号编码、以及「正文里提了一嘴但文件并不存在」的伪引用。最后一类必须落空——
 * 抽取的产物是「候选集的子集」，任何时候都不该凭正文造出候选集里没有的路径。</p>
 */
class ProfileRefExtractorTest
{
    private static final String KEY = "/profile/upload/2026/10/10/9f3c1a2b7e4d5c6f8091a2b3c4d5e6f7.png";

    @Test
    void matchesMarkdownImageEndingWithParenthesis()
    {
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("正文 ![图](" + KEY + ")"),
                Set.of(KEY));

        assertEquals(Set.of(KEY), referenced);
    }

    @Test
    void matchesWhenFollowedByChineseTextOrFullWidthParen()
    {
        // 正则猜边界最容易漏的形状：路径后面直接跟中文说明或全角括号
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("见图 " + KEY + "（说明）\n还有 " + KEY + "这是说明"),
                Set.of(KEY));

        assertEquals(Set.of(KEY), referenced);
    }

    @Test
    void matchesLongestPrefixSoTrailingJunkDoesNotHideTheKey()
    {
        // 宽 token 多吃了尾巴也没关系：从长到短试前缀，长的先命中
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![](/profile/upload/2026/10/10/9f3c1a2b7e4d5c6f8091a2b3c4d5e6f7.pngx)，这是说明"),
                Set.of(KEY));

        assertEquals(Set.of(KEY), referenced);
    }

    @Test
    void matchesObsidianShortReference()
    {
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![[/profile/upload/2026/10/10/9f3c1a2b7e4d5c6f8091a2b3c4d5e6f7.png]]"),
                Set.of(KEY));

        assertEquals(Set.of(KEY), referenced);
    }

    @Test
    void matchesPercentEncodedReferenceAfterDecoding()
    {
        String encodedKey = "/profile/upload/2026/10/10/%E5%9B%BE.png";
        String decodedKey = "/profile/upload/2026/10/10/图.png";

        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![]( " + encodedKey + ")"),
                Set.of(decodedKey));

        assertEquals(Set.of(decodedKey), referenced);
    }

    @Test
    void ignoresReferenceThatIsNotAmongCandidates()
    {
        // 正文里提到的路径不在候选集（文件其实不存在）→ 不得凭空造出引用
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![](/profile/upload/2026/10/10/not-in-storage.png)"),
                Set.of(KEY));

        assertTrue(referenced.isEmpty());
    }

    @Test
    void ignoresPathTraversalAndOverlongTokens()
    {
        Set<String> candidates = Set.of(KEY);
        String traversal = "![](/profile/../../../etc/passwd)";
        String overlong = "![](" + "/profile/upload/" + "a".repeat(300) + ")";

        Set<String> referenced = ProfileRefExtractor.selectReferenced(List.of(traversal, overlong), candidates);

        assertTrue(referenced.isEmpty());
    }

    @Test
    void collectsMultipleReferencesFromOneContent()
    {
        String second = "/profile/cover/2026/10/10/abc_1.jpg";

        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("第一张 " + KEY + "\n第二张 " + second + "\n再提一次 " + KEY),
                Set.of(KEY, second));

        // 同一键出现多次只算一个；不同键都要收
        assertEquals(Set.of(KEY, second), referenced);
    }

    @Test
    void findsSecondReferenceWhenTwoAreAdjacentWithoutSeparator()
    {
        // 两条引用之间没有终止符时，第一条的 token 会把第二条整条吃进去；
        // 扫描位置若跳到 token 末尾就会漏掉第二条（漏 = 误删），必须只前进一个前缀长度
        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![](/profile/a.png/profile/b.png)"),
                Set.of("/profile/a.png", "/profile/b.png"));

        assertEquals(Set.of("/profile/a.png", "/profile/b.png"), referenced);
    }

    @Test
    void returnsEmptyForEmptyInputs()
    {
        assertTrue(ProfileRefExtractor.selectReferenced(null, Set.of(KEY)).isEmpty());
        assertTrue(ProfileRefExtractor.selectReferenced(List.of(KEY), null).isEmpty());
        assertTrue(ProfileRefExtractor.selectReferenced(List.of(KEY), Set.of()).isEmpty());
        assertTrue(ProfileRefExtractor.selectReferenced(List.of("", "没有引用"), Set.of(KEY)).isEmpty());
        assertTrue(ProfileRefExtractor.selectReferenced(java.util.Arrays.asList((String) null), Set.of(KEY)).isEmpty());
    }

    /**
     * 已知残余：键名里含空格的文件抽不出来（token 在空白处截断）
     *
     * 这是刻意的取舍——空白是引用最常见的结束符，若把它当成路径字符，引用后面的整句话都会被吃进
     * token（那才是真的漏）。本功能上传的图片走 uuid 命名，不含空格；历史遗留的「原文件名 + 序号」
     * 键（封面 / 相册）另有业务列兜底，不依赖正文抽取。
     */
    @Test
    void doesNotMatchKeyContainingWhitespace()
    {
        String spacedKey = "/profile/cover/2026/09/28/我的封面 1.jpg";

        Set<String> referenced = ProfileRefExtractor.selectReferenced(
                List.of("![]( " + spacedKey + " )"),
                Set.of(spacedKey));

        assertTrue(referenced.isEmpty());
    }
}
