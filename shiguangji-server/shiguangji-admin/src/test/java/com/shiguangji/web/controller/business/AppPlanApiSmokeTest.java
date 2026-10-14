package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 0.0.3 计划冒烟测试（issue #74 / #77 / #78 / #79）：状态闸门放开 DOING、计划读接口、
 * 首页统计三态与心愿单纳入 DOING。
 *
 * <p>用例编号对应 docs/design/plan-testcases.md（T-72 起），随提交逐步补齐：</p>
 * <ol>
 *   <li>statusWhitelistAndDefault（T-72）—— PUT status=DOING 放行、非法值仍拒、新增缺省 WANT、计划日期可写</li>
 * </ol>
 *
 * <p><b>运行前提</b>：同 {@link AppApiAuthIsolationSmokeTest}，完整 Spring 上下文 + 真实
 * MySQL/Redis（application-dev.yml：localhost:13306 / 16379），并已执行
 * sql/update/20261014_plan_status.sql（状态字典含 DOING）。</p>
 *
 * <p><b>数据策略</b>：夹具标题统一 {@code qat12} 前缀；@BeforeAll/@AfterAll 按 title 前缀硬删除。
 * 断言口径为「夹具之间」的相对关系（顺序、包含/排除），不依赖库里其他真实数据。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppPlanApiSmokeTest
{
    /** 夹具标题前缀（创建/清理/反查唯一标识） */
    private static final String TITLE = "qat12-计划冒烟";

    /** 公开博主账号（application.yml shiguangji.public-owner；resolveCreateBy 对其返回 null=不限制） */
    private static final String PUBLIC_OWNER = "admin";

    /** 普通登录账号（test_data.sql 的第二用户；resolveCreateBy 对其返回本名=只见自己） */
    private static final String OTHER_USER = "shiguangji";

    /** 影视夹具 A：WANT + 计划日期 2026-10-13（相对今天 10-14 属「逾期」） */
    private static final String TITLE_A = TITLE + "-影视A";

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
    void setUp() throws Exception
    {
        cleanupFixtureRows();
        adminToken = createTokenFor(PUBLIC_OWNER);
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    /** 硬删除本测试类的全部夹具（sgj_item_photo 等由外键级联清理） */
    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_item where title like '" + TITLE + "%'");
    }

    // ------------------------------------------------------------------
    // 1. T-72：状态闸门放开 DOING（提交 fix(business) ITEM_STATUS）
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void statusWhitelistAndDefault() throws Exception
    {
        // 新增不传 status → 缺省 WANT（新增即心愿，不默认开始）
        createItem(TITLE_A, "MOVIE", null);
        long idA = findItemId(TITLE_A);
        assertThat(detail(idA).path("status").asText()).isEqualTo("WANT");

        // 计划日期可经既有 PUT 写入（0.0.3 不新增流转端点的根据）
        mockMvc.perform(put("/app/item/{itemId}", idA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("startDate", "2026-10-13"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(detail(idA).path("startDate").asText()).startsWith("2026-10-13");

        // PUT status=DOING 放行（此前的闸门是 ITEM_STATUS 白名单）
        mockMvc.perform(put("/app/item/{itemId}", idA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DOING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(detail(idA).path("status").asText()).isEqualTo("DOING");

        // 非法状态仍被拒，且文案含三态
        mockMvc.perform(put("/app/item/{itemId}", idA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "PAUSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", containsString("仅支持WANT/DOING/DONE")));
        // 被拒的请求不落库，状态停留在 DOING
        assertThat(detail(idA).path("status").asText()).isEqualTo("DOING");
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 经接口创建条目（create_by 由后端强制取当前登录人）；status 传 null 表示不带上该字段 */
    private void createItem(String title, String itemType, String status) throws Exception
    {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("itemType", itemType);
        if (status != null)
        {
            body.put("status", status);
        }
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 反查指定标题的唯一夹具条目 ID */
    private long findItemId(String title) throws Exception
    {
        JsonNode data = objectMapper.readTree(mockMvc.perform(get("/app/item/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("title", title))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.isArray() && data.size() == 1).as("应恰好存在一条夹具：" + title).isTrue();
        return data.get(0).path("itemId").asLong();
    }

    /** 取条目详情的 data 节点 */
    private JsonNode detail(long itemId) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(get("/app/item/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    /** 为指定用户直造登录态：TokenService.createToken 与登录接口同源，绕过验证码依赖 */
    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-smoke-test) Chrome/120.0");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            SysUser user = userService.selectUserByUserName(username);
            assertThat(user).as("sys_user 中需存在用户 %s", username).isNotNull();
            Set<String> permissions = new HashSet<>();
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
}
