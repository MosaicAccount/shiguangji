package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguangji.ShiGuangJiApplication;
import com.shiguangji.common.core.domain.entity.SysUser;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;

/**
 * 标签管理冒烟测试。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>adminTagCrudAndValidation      —— 管理端新增/校验：同模块重名拒绝、跨模块同名允许、非法模块与含逗号名称拒绝</li>
 *   <li>adminTagListFiltersByModule    —— 管理端列表按模块+名称筛选</li>
 *   <li>anonymousAppTagListEnabledOnly —— 前台 /app/tag/list 匿名可用、仅返回启用标签、module 必填</li>
 *   <li>tagFilterExactMatchOnItems     —— 条目按标签筛选为 FIND_IN_SET 精确匹配（子串不命中）</li>
 *   <li>tagFilterExactMatchOnNotes     —— 笔记按标签筛选为精确匹配</li>
 * </ol>
 *
 * <p><b>运行前提</b>：真实 MySQL/Redis（dev profile，localhost:13306/16379），
 * 且已执行 sql/update/20260906_tag_management.sql（依赖 sgj_tag 表与 sgj:tag:* 菜单无强关联，
 * 权限在测试内以 *:*:* 直造登录态）。</p>
 *
 * <p><b>数据策略</b>：夹具统一带 qatag1 前缀；@BeforeAll/@AfterAll 硬删除清理，不污染真实数据。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SgjTagApiSmokeTest
{
    /** 测试夹具统一前缀（用于隔离与清理） */
    private static final String PREFIX = "qatag1";

    /** 公开博主账号（application.yml shiguangji.public-owner） */
    private static final String ADMIN = "admin";

    @Autowired
    private MockMvc mockMvc;

    /** 应用上下文未注册 Jackson ObjectMapper Bean（MVC 侧为自定义消息转换），测试内自建即可 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;

    @BeforeAll
    void setUp()
    {
        cleanupFixtureRows();
        adminToken = createTokenFor(ADMIN);
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    /** 硬删除本测试类的全部夹具（含软删除产生的 del_flag='2' 行），恢复开发库原状 */
    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_tag where tag_name like '" + PREFIX + "%'");
        jdbcTemplate.update("delete from sgj_note where tags like '" + PREFIX + "%'");
        jdbcTemplate.update("delete from sgj_item where tags like '" + PREFIX + "%'");
    }

    // ------------------------------------------------------------------
    // 1. 管理端新增与校验
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void adminTagCrudAndValidation() throws Exception
    {
        // 正常新增（MOVIE 模块）
        createTag(adminToken, "MOVIE", PREFIX + "-mv", "0");

        // 同模块同名 → 拒绝
        mockMvc.perform(post("/business/tag")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody("MOVIE", PREFIX + "-mv", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("该模块下已存在同名标签：" + PREFIX + "-mv"));

        // 不同模块同名 → 允许（模块间标签隔离）
        createTag(adminToken, "BOOK", PREFIX + "-mv", "0");

        // 非法模块 → 拒绝
        mockMvc.perform(post("/business/tag")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody("FOO", PREFIX + "-foo", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("标签所属模块取值非法（仅允许 MOVIE/TV/BOOK/PLACE/NOTE）"));

        // 名称含逗号 → 拒绝（逗号是记录侧标签拼接分隔符）
        mockMvc.perform(post("/business/tag")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody("MOVIE", PREFIX + "-a,b", "0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("标签名称不能包含逗号或空白字符"));

        // 未登录调用管理端接口 → 业务码 401
        mockMvc.perform(get("/business/tag/list").param("module", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ------------------------------------------------------------------
    // 2. 管理端列表按模块筛选
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void adminTagListFiltersByModule() throws Exception
    {
        createTag(adminToken, "TV", PREFIX + "-tv", "0");

        JsonNode rows = listAdminTags(adminToken, "MOVIE", PREFIX);
        assertThat(rows.size()).as("MOVIE 模块按名称模糊应命中 1 条").isEqualTo(1);
        assertThat(rows.get(0).path("module").asText()).isEqualTo("MOVIE");
        assertThat(rows.get(0).path("tagName").asText()).isEqualTo(PREFIX + "-mv");
    }

    // ------------------------------------------------------------------
    // 3. 前台 /app/tag/list：匿名可用、仅启用标签、module 必填
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void anonymousAppTagListEnabledOnly() throws Exception
    {
        // 停用 BOOK 模块的同名标签
        long bookTagId = listAdminTags(adminToken, "BOOK", PREFIX).get(0).path("tagId").asLong();
        mockMvc.perform(put("/business/tag")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tagId", bookTagId,
                                "module", "BOOK",
                                "tagName", PREFIX + "-mv",
                                "status", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 匿名：MOVIE 返回启用标签；BOOK 停用标签不出现
        JsonNode movieTags = listAppTags("MOVIE");
        assertThat(containsName(movieTags, PREFIX + "-mv")).isTrue();
        JsonNode bookTags = listAppTags("BOOK");
        assertThat(containsName(bookTags, PREFIX + "-mv")).as("停用标签不应出现在前台下拉").isFalse();

        // 缺 module 参数 → 明确报错
        mockMvc.perform(get("/app/tag/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("module 参数不能为空"));
    }

    // ------------------------------------------------------------------
    // 4. 条目按标签精确筛选（FIND_IN_SET）
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void tagFilterExactMatchOnItems() throws Exception
    {
        String title = PREFIX + " 精确筛选条目";
        createItem(adminToken, title, PREFIX + "-f-a," + PREFIX + "-f-b");

        // 完整标签命中
        JsonNode exactRows = listItemsAsData(adminToken, PREFIX + "-f-a", title);
        assertThat(exactRows.size()).as("按完整标签筛选应命中夹具条目").isEqualTo(1);
        long exactItemId = exactRows.get(0).path("itemId").asLong();

        // 子串不命中（旧 LIKE 行为会误命中，此处验证精确语义）
        JsonNode substringRows = listItemsAsData(adminToken, PREFIX + "-f", title);
        assertThat(substringRows.size()).as("按子串筛选不应命中任何条目").isEqualTo(0);

        // 删除夹具条目（软删除），不残留数据
        mockMvc.perform(delete("/app/item/" + exactItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ------------------------------------------------------------------
    // 5. 笔记按标签精确筛选
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void tagFilterExactMatchOnNotes() throws Exception
    {
        String title = PREFIX + " 精确筛选笔记";
        createNote(adminToken, title, PREFIX + "-n-a," + PREFIX + "-n-b");

        JsonNode exactRows = listNotesAsData(adminToken, PREFIX + "-n-a", title);
        assertThat(exactRows.size()).as("按完整标签筛选应命中夹具笔记").isEqualTo(1);

        JsonNode substringRows = listNotesAsData(adminToken, PREFIX + "-n", title);
        assertThat(substringRows.size()).as("按子串筛选不应命中任何笔记").isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 为指定用户直造登录态：TokenService.createToken 与登录接口同源，绕过验证码依赖 */
    private String createTokenFor(String username)
    {
        // TokenService.setUserAgent 需要当前线程绑定请求；测试线程手工绑定 Mock 请求
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-smoke-test) Chrome/120.0");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            SysUser user = userService.selectUserByUserName(username);
            assertThat(user).as("sys_user 中需存在用户 %s", username).isNotNull();
            // 管理端接口带 @PreAuthorize，直造登录态需携带全量权限
            Set<String> permissions = new HashSet<>();
            permissions.add("*:*:*");
            LoginUser loginUser = new LoginUser(user.getUserId(), user, permissions);
            return tokenService.createToken(loginUser);
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

    private String tagBody(String module, String tagName, String status) throws Exception
    {
        return objectMapper.writeValueAsString(Map.of("module", module, "tagName", tagName, "status", status));
    }

    private void createTag(String token, String module, String tagName, String status) throws Exception
    {
        mockMvc.perform(post("/business/tag")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tagBody(module, tagName, status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 管理端列表（返回 rows 数组） */
    private JsonNode listAdminTags(String token, String module, String tagName) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/business/tag/list")
                        .param("module", module)
                        .param("tagName", tagName)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("rows");
    }

    /** 前台匿名标签下拉数据 */
    private JsonNode listAppTags(String module) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/tag/list").param("module", module))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data");
    }

    private boolean containsName(JsonNode tags, String name)
    {
        for (JsonNode row : tags)
        {
            if (name.equals(row.path("tagName").asText()))
            {
                return true;
            }
        }
        return false;
    }

    private void createItem(String token, String title, String tags) throws Exception
    {
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "itemType", "MOVIE",
                                "tags", tags))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void createNote(String token, String title, String tags) throws Exception
    {
        mockMvc.perform(post("/app/note")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "tags", tags,
                                "content", PREFIX + " 内容",
                                "isPublic", "0"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private JsonNode listItemsAsData(String token, String tags, String title) throws Exception
    {
        return listAsData("/app/item/list", token, tags, title);
    }

    private JsonNode listNotesAsData(String token, String tags, String title) throws Exception
    {
        return listAsData("/app/note/list", token, tags, title);
    }

    private JsonNode listAsData(String url, String token, String tags, String title) throws Exception
    {
        MockHttpServletRequestBuilder builder = get(url).param("tags", tags).param("title", title);
        MvcResult result = mockMvc.perform(builder
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data");
    }

    private JsonNode bodyOf(MvcResult result) throws Exception
    {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
