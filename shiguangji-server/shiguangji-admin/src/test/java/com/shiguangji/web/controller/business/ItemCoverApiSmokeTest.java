package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
import com.shiguangji.web.service.cover.CoverFetchClient;

/**
 * 条目封面链路冒烟测试（issue #5）：候选搜索 → 图片代理 → 转存 → 批量补全。
 *
 * <p>豆瓣外呼以桩实现替换（仅对夹具标题返回候选），其余走真实 Spring 上下文 +
 * 真实 MySQL/Redis（application-dev.yml：localhost:13306 / 16379）与本地磁盘存储。</p>
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>searchReturnsProxiedCandidates   —— 搜索候选返回代理路径，源地址经白名单校验</li>
 *   <li>proxyServesWhitelistedImage      —— 匿名代理白名单图片；非白名单域名拒绝</li>
 *   <li>importStoresCoverToOwnStorage    —— 转存写入 /profile/cover 并可回读原字节</li>
 *   <li>importRejectsNonWhitelistedUrl   —— 非白名单来源拒绝且不触发外呼</li>
 *   <li>backfillFillsMissingCover        —— 批量补全为无封面条目落封面与豆瓣编号</li>
 * </ol>
 *
 * <p><b>数据策略</b>：夹具标题统一 {@code qat5} 前缀，@BeforeAll/@AfterAll 按 title 前缀硬删除。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = { ShiGuangJiApplication.class, ItemCoverApiSmokeTest.StubCoverConfig.class })
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemCoverApiSmokeTest
{
    /** 夹具标题前缀（创建/清理/桩匹配唯一标识） */
    private static final String TITLE = "qat5-封面冒烟";

    /** 桩返回的图片内容（JPEG 魔数开头的占位字节） */
    private static final byte[] FAKE_JPEG = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01 };

    /** 桩候选的豆瓣编号 */
    private static final String STUB_DOUBAN_ID = "111277";

    /** 桩候选的原始封面地址（s_ratio_poster 小图，服务端应升级 l_ratio_poster 大图） */
    private static final String STUB_COVER_URL =
            "https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1910924212.jpg";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StubCoverConfig stubConfig;

    private String adminToken;

    @BeforeAll
    void setUp() throws Exception
    {
        cleanupFixtureRows();
        adminToken = createTokenFor("admin");
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    /** 硬删除本测试类的全部夹具（扩展表由外键级联清理） */
    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_item where title like '" + TITLE + "%'");
    }

    // ------------------------------------------------------------------
    // 1. 候选搜索：返回代理路径候选
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    void searchReturnsProxiedCandidates() throws Exception
    {
        mockMvc.perform(get("/app/item/cover/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "MOVIE")
                        .param("title", TITLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].sourceId").value(STUB_DOUBAN_ID))
                .andExpect(jsonPath("$.data[0].title").value(TITLE))
                .andExpect(jsonPath("$.data[0].year").value("1994"))
                .andExpect(jsonPath("$.data[0].imageUrl").value(
                        org.hamcrest.Matchers.startsWith("/app/item/cover/proxy?u=")));

        // 不支持的类型直接拒绝（地点无豆瓣数据源）
        mockMvc.perform(get("/app/item/cover/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "PLACE")
                        .param("title", TITLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ------------------------------------------------------------------
    // 2. 图片代理：白名单内匿名可访问，白名单外拒绝
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    void proxyServesWhitelistedImageAndBlocksOthers() throws Exception
    {
        byte[] body = mockMvc.perform(get("/app/item/cover/proxy").param("u", STUB_COVER_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body).isEqualTo(FAKE_JPEG);

        // 非白名单域名：回 500 业务码（SSRF 防护生效，且不触发外呼）
        int fetchedBefore = stubClient().fetchedUrls.size();
        mockMvc.perform(get("/app/item/cover/proxy").param("u", "http://127.0.0.1:16379/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        assertThat(stubClient().fetchedUrls).hasSize(fetchedBefore);
    }

    // ------------------------------------------------------------------
    // 3. 转存：写入自有存储并回读
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    void importStoresCoverToOwnStorage() throws Exception
    {
        int fetchedBefore = stubClient().fetchedUrls.size();
        String request = objectMapper.writeValueAsString(Map.of(
                "itemType", "MOVIE",
                "sourceUrl", STUB_COVER_URL,
                "sourceId", STUB_DOUBAN_ID));
        String coverUrl = objectMapper.readTree(mockMvc.perform(post("/app/item/cover/import")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("url").asText();

        // 大图升级：本次转存实际抓取的是 l_ratio_poster 大图地址
        assertThat(stubClient().fetchedUrls.stream().skip(fetchedBefore))
                .anyMatch(u -> u.contains("/l_ratio_poster/"));
        // 落库路径为自有存储 /profile/cover 前缀，且可经资源句柄回读原字节
        assertThat(coverUrl).startsWith("/profile/cover/");
        mockMvc.perform(get(coverUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FAKE_JPEG));
    }

    // ------------------------------------------------------------------
    // 4. 转存：非白名单来源拒绝
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    void importRejectsNonWhitelistedUrl() throws Exception
    {
        int fetchedBefore = stubClient().fetchedUrls.size();
        String request = objectMapper.writeValueAsString(Map.of(
                "itemType", "MOVIE",
                "sourceUrl", "http://127.0.0.1:16399/evil.jpg"));
        mockMvc.perform(post("/app/item/cover/import")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        assertThat(stubClient().fetchedUrls).hasSize(fetchedBefore);
    }

    // ------------------------------------------------------------------
    // 5. 批量补全：为无封面夹具条目落封面与豆瓣编号
    // ------------------------------------------------------------------

    @org.junit.jupiter.api.Test
    void backfillFillsMissingCover() throws Exception
    {
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", TITLE + "-电影",
                                "itemType", "MOVIE",
                                "releaseYear", 1994))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        long itemId = findSingleItemId(TITLE + "-电影");

        mockMvc.perform(post("/business/item/cover/backfill")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updated").value(1));

        JsonNode item = objectMapper.readTree(mockMvc
                .perform(get("/app/item/{itemId}", itemId)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(item.path("coverUrl").asText()).startsWith("/profile/cover/");
        assertThat(item.path("doubanId").asText()).isEqualTo(STUB_DOUBAN_ID);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private StubCoverClient stubClient()
    {
        return (StubCoverClient) stubConfig.stubClient;
    }

    /** 反查指定标题的唯一夹具条目 ID */
    private long findSingleItemId(String title) throws Exception
    {
        JsonNode data = objectMapper.readTree(mockMvc.perform(get("/app/item/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("itemType", "MOVIE")
                        .param("title", title))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(data.isArray() && data.size() == 1).as("应恰好存在一条夹具条目：" + title).isTrue();
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
            // 含超管通配权限，使 @PreAuthorize("@ss.hasPermi(...)") 校验通过
            Set<String> permissions = new HashSet<>(List.of("*:*:*"));
            LoginUser loginUser = new LoginUser(user.getUserId(), user, permissions);
            return tokenService.createToken(loginUser);
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private String bearer(String token)
    {
        return "Bearer " + token;
    }

    /**
     * 豆瓣桩配置：搜索仅对夹具标题返回候选（其余条目按无候选跳过，避免污染库内真实数据），
     * 图片抓取返回固定字节并记录请求地址供断言
     */
    @TestConfiguration
    static class StubCoverConfig
    {
        final StubCoverClient stubClient = new StubCoverClient();

        @Bean
        @Primary
        CoverFetchClient stubCoverClient()
        {
            return stubClient;
        }
    }

    static class StubCoverClient implements CoverFetchClient
    {
        /** 记录 fetch 实际请求的地址（大图升级/白名单断言用） */
        final ConcurrentLinkedQueue<String> fetchedUrls = new ConcurrentLinkedQueue<>();

        @Override
        public List<Candidate> search(String itemType, String keyword)
        {
            if (keyword != null && keyword.startsWith(TITLE))
            {
                return List.of(new Candidate(STUB_DOUBAN_ID, keyword, "1994", STUB_COVER_URL));
            }
            return List.of();
        }

        @Override
        public Image fetch(String imageUrl)
        {
            fetchedUrls.add(imageUrl);
            return new Image(FAKE_JPEG, "image/jpeg");
        }
    }
}
