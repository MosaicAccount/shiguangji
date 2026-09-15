package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
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
 * 条目照片链路冒烟测试（issue #6）：新增带照片 → 详情回显 → 修改整体替换 → 空串清空 → 落库顺序。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>photosRoundTripOnDetail       —— POST 创建 PLACE 带照片串，GET 详情按序回显</li>
 *   <li>photoUpdateReplacesInOrder    —— PUT 换一组照片，详情与 sgj_item_photo 行整体替换且排序正确</li>
 *   <li>photoClearWithEmptyString     —— PUT 空串清空照片（详情 photos 为空串，表内无残留行）</li>
 * </ol>
 *
 * <p><b>运行前提</b>：同 {@link AppApiAuthIsolationSmokeTest}，完整 Spring 上下文 + 真实
 * MySQL/Redis（application-dev.yml：localhost:13306 / 16379）。</p>
 *
 * <p><b>数据策略</b>：夹具标题统一 {@code qat11} 前缀；@BeforeAll/@AfterAll 按 title 前缀硬删除，
 * sgj_item_photo 由外键级联清理，不污染真实数据。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SgjItemPhotoApiSmokeTest
{
    /** 夹具标题前缀（创建/清理/反查唯一标识） */
    private static final String TITLE = "qat11-照片冒烟-地点";

    /** 公开博主账号（application.yml shiguangji.public-owner） */
    private static final String PUBLIC_OWNER = "admin";

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

    /** 硬删除本测试类的全部夹具（照片行由外键级联删除） */
    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_item where title like '" + TITLE + "%'");
    }

    // ------------------------------------------------------------------
    // 1. 创建带照片 + 详情回显
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void photosRoundTripOnDetail() throws Exception
    {
        createPlace("/profile/upload/a.png, /profile/upload/b.png");

        long itemId = findSingleItemId();
        mockMvc.perform(get("/app/item/{itemId}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.photos").value("/profile/upload/a.png,/profile/upload/b.png"));
    }

    // ------------------------------------------------------------------
    // 2. 修改照片：整体替换 + 排序落库
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void photoUpdateReplacesInOrder() throws Exception
    {
        long itemId = findSingleItemId();
        mockMvc.perform(put("/app/item/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemType", "PLACE",
                                "photos", "/profile/upload/c.png,/profile/upload/a.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 详情按提交顺序回显
        mockMvc.perform(get("/app/item/{itemId}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photos").value("/profile/upload/c.png,/profile/upload/a.png"));

        // 库内行整体替换且 sort_order 与提交顺序一致
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select url, sort_order from sgj_item_photo where item_id = ? order by sort_order", itemId);
        assertThat(rows).extracting(r -> r.get("url")).containsExactly("/profile/upload/c.png", "/profile/upload/a.png");
        assertThat(rows).extracting(r -> r.get("sort_order")).containsExactly(0, 1);
    }

    // ------------------------------------------------------------------
    // 3. 空串清空
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void photoClearWithEmptyString() throws Exception
    {
        long itemId = findSingleItemId();
        mockMvc.perform(put("/app/item/{itemId}", itemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemType", "PLACE",
                                "photos", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/app/item/{itemId}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photos").value(""));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sgj_item_photo where item_id = ?", Integer.class, itemId)).isZero();
    }

    // ------------------------------------------------------------------
    // 4. 轨迹与列表接口的照片统计（photoCount / cover / photoCover）
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void trajectoryAndListExposePhotoStats() throws Exception
    {
        // 带坐标的地点才会进入轨迹
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", TITLE + "-轨迹",
                                "itemType", "PLACE",
                                "latitude", 30.274085,
                                "longitude", 120.15507,
                                "photos", "/profile/upload/tj-a.png,/profile/upload/tj-b.png"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        long itemId = findSingleItemId(TITLE + "-轨迹");

        // 轨迹接口：photoCount 与 cover（排序首张）
        JsonNode trajectory = objectMapper.readTree(mockMvc
                .perform(get("/app/travel/trajectory")).andReturn().getResponse().getContentAsString());
        JsonPoint point = findPoint(trajectory, itemId);
        assertThat(point).as("轨迹中应包含夹具地点，trajectory=" + trajectory).isNotNull();
        assertThat(point.photoCount).isEqualTo(2);
        assertThat(point.cover).isEqualTo("/profile/upload/tj-a.png");

        // 列表接口：photoCount / photoCover（徽标与封面兜底用）；前台列表以 data 数组返回
        mockMvc.perform(get("/app/item/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "PLACE")
                        .param("title", TITLE + "-轨迹"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].photoCount").value(2))
                .andExpect(jsonPath("$.data[0].photoCover").value("/profile/upload/tj-a.png"));
    }

    /** 从轨迹响应的 visited/wish 中按 itemId 找点（响应体为 { code, data: { visited, wish } }） */
    private JsonPoint findPoint(JsonNode trajectory, long itemId)
    {
        JsonNode data = trajectory.path("data");
        for (String key : new String[] { "visited", "wish" })
        {
            for (JsonNode p : data.path(key))
            {
                if (p.path("itemId").asLong() == itemId)
                {
                    return new JsonPoint(p.path("photoCount").asInt(), p.path("cover").asText(null));
                }
            }
        }
        return null;
    }

    private record JsonPoint(int photoCount, String cover) {}

    /** 反查指定标题的唯一夹具条目 ID */
    private long findSingleItemId(String title) throws Exception
    {
        JsonNode data = objectMapper.readTree(mockMvc.perform(get("/app/item/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "PLACE")
                        .param("title", title))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.isArray() && data.size() == 1).as("应恰好存在一条夹具地点：" + title).isTrue();
        return data.get(0).path("itemId").asLong();
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 经接口创建地点条目（带照片串），create_by 由后端强制取当前登录人 */
    private void createPlace(String photos) throws Exception
    {
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", TITLE,
                                "itemType", "PLACE",
                                "photos", photos))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 反查唯一夹具条目 ID */
    private long findSingleItemId() throws Exception
    {
        JsonNode data = objectMapper.readTree(mockMvc.perform(get("/app/item/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "PLACE")
                        .param("title", TITLE))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(data.isArray() && data.size() == 1).as("应恰好存在一条夹具地点").isTrue();
        return data.get(0).path("itemId").asLong();
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
