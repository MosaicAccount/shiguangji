package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.shiguangji.ShiGuangJiApplication;
import com.shiguangji.common.core.domain.entity.SysUser;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.common.utils.file.FileUtils;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;

/**
 * 笔记导出 Markdown（issue #41）接口冒烟测试。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>anonymousExportRejected      —— 匿名导出返回业务码 401（本接口没有 @Anonymous）</li>
 *   <li>otherUserExportRejected      —— 他人笔记导出被拒（归属校验，管理员之外没有旁路）</li>
 *   <li>nonexistentNoteRejected      —— 已删除 / 不存在的笔记被拒</li>
 *   <li>ownerGetsMarkdownFile        —— 本人导出拿到 text/markdown 附件，正文逐字节等于约定的形状</li>
 *   <li>frontMatterIsYamlEscaped     —— 标题里的引号 / 冒号 / # 被转义成合法 YAML（这是往返能不能成立的关键）</li>
 *   <li>illegalCharactersInFileName  —— 标题里的路径分隔符等在文件名里被替换，且响应头只出现百分号编码名</li>
 *   <li>existingFrontMatterStripped  —— 正文自带前言时先剥掉，导出文件里只有一个 front-matter 块</li>
 *   <li>emptyTagsStillEmitKey        —— 没有标签时 tags 键仍在（导出只写能被导入侧读回来的字段）</li>
 *   <li>exportIsReadOnly             —— 导出不改动笔记（update_time 不变）</li>
 * </ol>
 *
 * <p><b>与前端的分工</b>：这里断言导出文本的<i>精确形状</i>；反过来「这段文本能被 js-yaml 解析回
 * 同样的 noteId / title / tags」由前端 {@code src/utils/__tests__/noteImport.spec.ts} 用同一段字面量断言。
 * 两边改一处必须同步改另一处，否则往返会在没人注意的地方断掉。</p>
 *
 * <p><b>数据策略</b>：直连开发库。夹具标题统一带 {@code qat41} 标记，@BeforeAll / @AfterAll 按标记清理。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppNoteExportSmokeTest
{
    /** 夹具标记：标题前缀，清理按它匹配 */
    private static final String MARK = "qat41";

    /** 公开博主账号（application.yml shiguangji.public-owner），同时是管理员 */
    private static final String PUBLIC_OWNER = "admin";

    /** 非管理员普通用户（sys_user 中已存在） */
    private static final String OTHER_USER = "shiguangji";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String otherToken;

    @BeforeAll
    void setUp()
    {
        cleanupFixtureRows();
        adminToken = createTokenFor(PUBLIC_OWNER);
        otherToken = createTokenFor(OTHER_USER);
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_note where title like '" + MARK + "%'");
    }

    // ------------------------------------------------------------------
    // 1. 权限：匿名 / 他人 / 不存在
    // ------------------------------------------------------------------

    @Test
    void anonymousExportRejected() throws Exception
    {
        long noteId = insertNote(MARK + " 匿名", "正文", "标签", PUBLIC_OWNER);
        mockMvc.perform(get("/app/note/" + noteId + "/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void otherUserExportRejected() throws Exception
    {
        long noteId = insertNote(MARK + " 他人", "正文", "标签", PUBLIC_OWNER);
        mockMvc.perform(get("/app/note/" + noteId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void nonexistentNoteRejected() throws Exception
    {
        mockMvc.perform(get("/app/note/999999999/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ------------------------------------------------------------------
    // 2. 本人导出：形状、文件名、YAML 转义
    // ------------------------------------------------------------------

    @Test
    void ownerGetsMarkdownFile() throws Exception
    {
        String title = MARK + " 普通笔记";
        String content = MARK + " 正文第一行\n\n第二段";
        String tags = "技术,笔记";
        long noteId = insertNote(title, content, tags, PUBLIC_OWNER);

        MvcResult result = exportAs(adminToken, noteId);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(result.getResponse().getContentType()).startsWith("text/markdown");
        assertThat(body).isEqualTo("---\n"
                + "noteId: " + noteId + "\n"
                + "title: \"qat41 普通笔记\"\n"
                + "tags: \"技术,笔记\"\n"
                + "---\n\n"
                + content + "\n");
        // 文件名走统一的百分号编码（中文可用），前端 decodeURIComponent 后即下面的名字
        assertAttachmentFileName(result, "qat41 普通笔记.md");
    }

    @Test
    void frontMatterIsYamlEscaped() throws Exception
    {
        // 冒号 + 空格、双引号、井号：裸标量这三个都会让 YAML 解析出别的东西，甚至整块解析失败
        String title = MARK + " 报价 \"含引号\": 关键#标签";
        long noteId = insertNote(title, "正文", "a,b", PUBLIC_OWNER);

        String body = exportBody(adminToken, noteId);

        assertThat(body).contains("title: \"qat41 报价 \\\"含引号\\\": 关键#标签\"\n");
        // 转义后的标量必须仍是「一条 title 行」，不能因为引号把后面的键吃掉
        assertThat(body).contains("tags: \"a,b\"\n---\n");
    }

    @Test
    void illegalCharactersInFileNameReplaced() throws Exception
    {
        // 标题里的 / \ : * ? " < > | 在保存对话框与响应头里都非法
        long noteId = insertNote(MARK + " 标题/带:非法*字符?\"<>|", "正文", "t", PUBLIC_OWNER);

        String name = decodedFileName(exportAs(adminToken, noteId));

        // 不数下划线个数（改一个字符就挂），只断言真正的不变量：非法字符一个不剩、仍以 .md 结尾
        assertThat(name).matches("[^\\\\/:*?\"<>|]+\\.md");
        assertThat(name).startsWith("qat41 标题");
    }

    @Test
    void existingFrontMatterStripped() throws Exception
    {
        // 用户在编辑页手贴了一段 front-matter：导出前必须先剥掉，否则文件里会有两个前言块，
        // 而重新导入只剥掉第一个，第二个就留在正文里了
        String content = "---\ntitle: 手贴的前言\n---\n真正的正文";
        long noteId = insertNote(MARK + " 带手贴前言", content, "", PUBLIC_OWNER);

        String body = exportBody(adminToken, noteId);

        assertThat(body).doesNotContain("手贴的前言");
        assertThat(body).endsWith("---\n\n真正的正文\n");
        // 只应有一个前言块：剥掉开头那块之后剩不下第二个 '---' 行
        assertThat(body.substring(1)).doesNotContain("---\n---");
    }

    @Test
    void emptyTagsStillEmitKey() throws Exception
    {
        long noteId = insertNote(MARK + " 无标签", "正文", "", PUBLIC_OWNER);
        assertThat(exportBody(adminToken, noteId)).contains("tags: \"\"\n");
    }

    // ------------------------------------------------------------------
    // 3. 回归：导出是只读的
    // ------------------------------------------------------------------

    @Test
    void exportIsReadOnly() throws Exception
    {
        long noteId = insertNote(MARK + " 只读", "正文", "t", PUBLIC_OWNER);
        String before = noteUpdateTime(noteId);

        exportBody(adminToken, noteId);
        exportBody(adminToken, noteId);

        assertThat(noteUpdateTime(noteId)).as("导出不得改动笔记").isEqualTo(before);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private MvcResult exportAs(String token, long noteId) throws Exception
    {
        return mockMvc.perform(get("/app/note/" + noteId + "/export")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String exportBody(String token, long noteId) throws Exception
    {
        return exportAs(token, noteId).getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 断言附件名：响应头里必须是百分号编码名（原始名不能裸着出现），解出来等于期望值 */
    private void assertAttachmentFileName(MvcResult result, String expectedName) throws Exception
    {
        String encoded = FileUtils.percentEncode(expectedName);
        assertThat(result.getResponse().getHeader("Content-disposition"))
                .isEqualTo("attachment; filename=" + encoded + ";filename*=utf-8''" + encoded);
        assertThat(decodedFileName(result)).isEqualTo(expectedName);
    }

    /** 前端拿到的是响应头里的 download-filename（百分号编码），decodeURIComponent 后就是保存对话框里的名字 */
    private String decodedFileName(MvcResult result) throws Exception
    {
        return URLDecoder.decode(result.getResponse().getHeader("download-filename"), StandardCharsets.UTF_8);
    }

    private long insertNote(String title, String content, String tags, String createBy)
    {
        jdbcTemplate.update("insert into sgj_note (title, content, tags, is_public, del_flag, create_by, create_time, update_time) "
                        + "values (?, ?, ?, '0', '0', ?, now(), now())",
                title, content, tags, createBy);
        Long noteId = jdbcTemplate.queryForObject(
                "select note_id from sgj_note where title = ? order by note_id desc limit 1", Long.class, title);
        assertThat(noteId).as("夹具笔记应插入成功").isNotNull();
        return noteId;
    }

    private String noteUpdateTime(long noteId)
    {
        return jdbcTemplate.queryForObject(
                "select date_format(update_time, '%Y-%m-%d %H:%i:%s') from sgj_note where note_id = ?",
                String.class, noteId);
    }

    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-export-test) Chrome/120.0");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            SysUser user = userService.selectUserByUserName(username);
            assertThat(user).as("sys_user 中需存在用户 %s", username).isNotNull();
            return tokenService.createToken(new LoginUser(user.getUserId(), user, new HashSet<String>()));
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static String bearer(String token)
    {
        return "Bearer " + token;
    }
}
