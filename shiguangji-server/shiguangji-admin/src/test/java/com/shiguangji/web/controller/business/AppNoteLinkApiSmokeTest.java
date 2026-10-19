package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.shiguangji.ShiGuangJiApplication;
import com.shiguangji.common.core.domain.entity.SysUser;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;

/**
 * 笔记互链与反链（0.0.5 FR-005-1/2/3）详情接口冒烟测试。
 *
 * <p>覆盖：目标解析三态、反链来源（排除自身/回收站/代码块假引用）、重名降级与同名计数、
 * 匿名可见性（私密目标 NOT_FOUND、私密来源不进反链）、列表接口不下发互链字段。
 * 纯函数口径（掩码/嵌套/大小写）由 SgjNoteLinkUtilsTest 钉住。</p>
 *
 * <p><b>数据策略</b>：直连开发库，夹具标题统一带 qat05 前缀，@BeforeAll / @AfterAll 按前缀清理。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppNoteLinkApiSmokeTest {

    /** 夹具标记：标题前缀，清理按它匹配 */
    private static final String MARK = "qat05";

    /** 公开博主账号（application.yml shiguangji.public-owner），同时是管理员 */
    private static final String PUBLIC_OWNER = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeAll
    void setUp() {
        cleanupFixtureRows();
        adminToken = createTokenFor(PUBLIC_OWNER);
    }

    @AfterAll
    void tearDown() {
        cleanupFixtureRows();
    }

    private void cleanupFixtureRows() {
        jdbcTemplate.update("delete from sgj_note where title like '" + MARK + "%'");
    }

    // ------------------------------------------------------------------
    // 目标解析与反链
    // ------------------------------------------------------------------

    @Test
    void detailResolvesLinkAndBacklinks() throws Exception {
        long targetId = insertNote(MARK + "目标B", "B 正文", "1", PUBLIC_OWNER);
        long sourceId = insertNote(MARK + "来源A", "引用 [[qat05目标B]] 完成", "1", PUBLIC_OWNER);

        // 来源篇：唯一命中成链
        mockMvc.perform(get("/app/note/" + sourceId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links.length()").value(1))
                .andExpect(jsonPath("$.data.links[0].noteId").value(targetId))
                .andExpect(jsonPath("$.data.links[0].raw").value("[[qat05目标B]]"))
                .andExpect(jsonPath("$.data.links[0].reason").doesNotExist())
                .andExpect(jsonPath("$.data.backlinks.length()").value(0));

        // 目标篇：反链指向来源，片段含标记原文、计数为 1；本篇无 token 则 links 为空数组
        mockMvc.perform(get("/app/note/" + targetId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links.length()").value(0))
                .andExpect(jsonPath("$.data.backlinks.length()").value(1))
                .andExpect(jsonPath("$.data.backlinks[0].noteId").value(sourceId))
                .andExpect(jsonPath("$.data.backlinks[0].snippet").value(org.hamcrest.Matchers.containsString("[[qat05目标B]]")))
                .andExpect(jsonPath("$.data.backlinks[0].hitCount").value(1));
    }

    @Test
    void backlinksExcludeSelfAndRecycled() throws Exception {
        long targetId = insertNote(MARK + "目标T2", "正文", "1", PUBLIC_OWNER);
        long sourceId = insertNote(MARK + "来源S2", "看 [[qat05目标T2]]", "1", PUBLIC_OWNER);
        insertNote(MARK + "已删R2", "看 [[qat05目标T2]]", "1", PUBLIC_OWNER);
        jdbcTemplate.update("update sgj_note set del_flag = '2' where title = ?", MARK + "已删R2");

        // 自引：这篇正文引用自己的标题，不得出现在自己的反链里
        long selfId = insertNote(MARK + "自指Z2", "自己 [[qat05自指Z2]]", "1", PUBLIC_OWNER);

        mockMvc.perform(get("/app/note/" + targetId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlinks.length()").value(1))
                .andExpect(jsonPath("$.data.backlinks[0].noteId").value(sourceId));

        mockMvc.perform(get("/app/note/" + selfId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlinks.length()").value(0));
    }

    @Test
    void codeBlockReferenceIsIgnored() throws Exception {
        long targetId = insertNote(MARK + "目标T3", "正文", "1", PUBLIC_OWNER);
        insertNote(MARK + "代码S3", "开头\n```\n[[qat05目标T3]]\n```\n结尾", "1", PUBLIC_OWNER);

        mockMvc.perform(get("/app/note/" + targetId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlinks.length()").value(0));
    }

    @Test
    void duplicateTitleDegradesToAmbiguous() throws Exception {
        insertNote(MARK + "重名T4", "a", "1", PUBLIC_OWNER);
        insertNote(MARK + "重名T4", "b", "1", PUBLIC_OWNER);
        long sourceId = insertNote(MARK + "来源S4", "看 [[qat05重名T4]]", "1", PUBLIC_OWNER);
        long firstId = noteIdByTitle(MARK + "重名T4");

        // 引用侧：重名不猜，降级为 AMBIGUOUS
        mockMvc.perform(get("/app/note/" + sourceId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links[0].reason").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.data.links[0].noteId").doesNotExist());

        // 被引用侧：同名计数含本篇为 2，反链不列来源
        mockMvc.perform(get("/app/note/" + firstId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sameTitleCount").value(2))
                .andExpect(jsonPath("$.data.backlinks.length()").value(0));
    }

    @Test
    void missingTargetIsNotFound() throws Exception {
        long sourceId = insertNote(MARK + "来源S5", "看 [[qat05不存在X]]", "1", PUBLIC_OWNER);

        mockMvc.perform(get("/app/note/" + sourceId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links[0].reason").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.links[0].noteId").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 匿名可见性
    // ------------------------------------------------------------------

    @Test
    void anonymousSeesPublicScopeOnly() throws Exception {
        long publicTargetId = insertNote(MARK + "公开Q", "q", "1", PUBLIC_OWNER);
        long privateTargetId = insertNote(MARK + "私密R", "r", "0", PUBLIC_OWNER);
        long pageId = insertNote(MARK + "公开P", "公 [[qat05公开Q]] 私 [[qat05私密R]]", "1", PUBLIC_OWNER);
        insertNote(MARK + "私密S", "s 看 [[qat05公开P]]", "0", PUBLIC_OWNER);

        // 匿名：公开目标成链，私密目标与不存在同表现（不泄露存在性）；私密来源不进反链
        mockMvc.perform(get("/app/note/" + pageId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links.length()").value(2))
                .andExpect(jsonPath("$.data.links[0].noteId").value(publicTargetId))
                .andExpect(jsonPath("$.data.links[1].reason").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.backlinks.length()").value(0));

        // 管理员：全量可见——私密目标成链、私密来源出现在反链
        mockMvc.perform(get("/app/note/" + pageId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links[1].noteId").value(privateTargetId))
                .andExpect(jsonPath("$.data.backlinks.length()").value(1));
    }

    @Test
    void anonymousPrivateNoteStillRejected() throws Exception {
        long privateId = insertNote(MARK + "私密R2", "正文", "0", PUBLIC_OWNER);

        mockMvc.perform(get("/app/note/" + privateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ------------------------------------------------------------------
    // 回归：列表接口不下发互链字段
    // ------------------------------------------------------------------

    @Test
    void listRowsDoNotCarryLinkFields() throws Exception {
        insertNote(MARK + "列表L", "看 [[qat05公开Q]]", "1", PUBLIC_OWNER);

        mockMvc.perform(get("/app/note/list").param("title", MARK + "列表L")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].links").doesNotExist())
                .andExpect(jsonPath("$.data[0].backlinks").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private long insertNote(String title, String content, String isPublic, String createBy) {
        jdbcTemplate.update("insert into sgj_note (title, content, tags, is_public, del_flag, create_by, create_time, update_time) "
                        + "values (?, ?, '', ?, '0', ?, now(), now())",
                title, content, isPublic, createBy);
        return noteIdByTitle(title);
    }

    private long noteIdByTitle(String title) {
        Long noteId = jdbcTemplate.queryForObject(
                "select note_id from sgj_note where title = ? order by note_id desc limit 1", Long.class, title);
        assertThat(noteId).as("夹具笔记应插入成功：%s", title).isNotNull();
        return noteId;
    }

    private String createTokenFor(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-link-test) Chrome/120.0");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            SysUser user = userService.selectUserByUserName(username);
            assertThat(user).as("sys_user 中需存在用户 %s", username).isNotNull();
            return tokenService.createToken(new LoginUser(user.getUserId(), user, new java.util.HashSet<String>()));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
