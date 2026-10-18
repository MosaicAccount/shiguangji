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
 * 全站聚合检索（0.0.5 FR-005-5）接口冒烟测试。
 *
 * <p>覆盖：分组顺序与空组不下发、每组 total 与 rows、匿名只出公开数据、
 * 条目结果不下发私人字段、笔记组命中正文、关键词门槛兜底、无结果空态。</p>
 *
 * <p><b>数据策略</b>：直连开发库，夹具标题带 qat05搜 前缀；条目主表按前缀清理
 * （扩展表由外键级联删除）。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppSearchApiSmokeTest {

    /** 夹具标记兼检索关键词（≥2 字，避免误伤其他数据） */
    private static final String KEYWORD = "qat05搜";

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
        // 夹具统一建一份，两个可见性用例共享同一组数据（避免按用例插入带来的顺序耦合）
        insertMovie(KEYWORD + "影片");
        insertNote(KEYWORD + "笔记", "标题命中即可，正文再写一次 " + KEYWORD, "1");
        insertNote(KEYWORD + "私密笔记", "私密也命中 " + KEYWORD, "0");
        adminToken = createTokenFor(PUBLIC_OWNER);
    }

    @AfterAll
    void tearDown() {
        cleanupFixtureRows();
    }

    private void cleanupFixtureRows() {
        jdbcTemplate.update("delete from sgj_item where title like '" + KEYWORD + "%'");
        jdbcTemplate.update("delete from sgj_note where title like '" + KEYWORD + "%'");
    }

    @Test
    void adminSearchGroupsAndTotals() throws Exception {
        mockMvc.perform(get("/app/search").param("keyword", KEYWORD)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                // 空组（书单/足迹）不下发，只剩 影单 → 笔记，顺序固定
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[0].key").value("movie"))
                .andExpect(jsonPath("$.data.groups[0].total").value(1))
                .andExpect(jsonPath("$.data.groups[0].rows[0].title").value(KEYWORD + "影片"))
                .andExpect(jsonPath("$.data.groups[1].key").value("note"))
                .andExpect(jsonPath("$.data.groups[1].total").value(2))
                // 笔记组复用前台列表：带摘要与命中数、正文不下发
                .andExpect(jsonPath("$.data.groups[1].rows[0].excerpt").exists())
                .andExpect(jsonPath("$.data.groups[1].rows[0].hitTotal").exists())
                .andExpect(jsonPath("$.data.groups[1].rows[0].content").doesNotExist())
                // 搜索结果不下发私人字段（对任何查看者一致）
                .andExpect(jsonPath("$.data.groups[0].rows[0].comment").doesNotExist())
                .andExpect(jsonPath("$.data.groups[0].rows[0].remark").doesNotExist());
    }

    @Test
    void anonymousSearchSeesPublicOnly() throws Exception {
        mockMvc.perform(get("/app/search").param("keyword", KEYWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(2))
                .andExpect(jsonPath("$.data.groups[1].key").value("note"))
                .andExpect(jsonPath("$.data.groups[1].total").value(1))
                .andExpect(jsonPath("$.data.groups[0].key").value("movie"))
                .andExpect(jsonPath("$.data.groups[0].total").value(1));
    }

    @Test
    void keywordShorterThanTwoRejected() throws Exception {
        mockMvc.perform(get("/app/search").param("keyword", "q")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("2")));
    }

    @Test
    void noMatchGivesEmptyGroups() throws Exception {
        mockMvc.perform(get("/app/search").param("keyword", KEYWORD + "无命中zz")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(0));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void insertMovie(String title) {
        jdbcTemplate.update("insert into sgj_item (item_type, title, status, del_flag, create_by, create_time, update_time) "
                        + "values ('MOVIE', ?, 'DONE', '0', ?, now(), now())", title, PUBLIC_OWNER);
        Long itemId = jdbcTemplate.queryForObject(
                "select item_id from sgj_item where title = ? order by item_id desc limit 1", Long.class, title);
        assertThat(itemId).as("夹具条目应插入成功").isNotNull();
        // 扩展行走左联，不插也能查；插一行保持与真实条目同形
        jdbcTemplate.update("insert into sgj_item_movie (item_id) values (?)", itemId);
    }

    private void insertNote(String title, String content, String isPublic) {
        jdbcTemplate.update("insert into sgj_note (title, content, tags, is_public, del_flag, create_by, create_time, update_time) "
                        + "values (?, ?, '', ?, '0', ?, now(), now())", title, content, isPublic, PUBLIC_OWNER);
    }

    private String createTokenFor(String username) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-search-test) Chrome/120.0");
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
