package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.shiguangji.ShiGuangJiApplication;
import com.shiguangji.common.config.ShiGuangJiConfig;
import com.shiguangji.common.constant.Constants;
import com.shiguangji.common.core.domain.entity.SysUser;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.common.core.redis.RedisCache;
import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.file.storage.FileStorageService;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;
import com.shiguangji.web.service.image.ExternalImageFetcher;

/**
 * 外链图片转存端点冒烟测试（0.0.6，issue #68，测试用例 T-49 ~ T-53、T-55）。
 *
 * <p>抓取器用桩替换（真实现的 SSRF 矩阵见设计 §4，落地时由
 * {@code ExternalImageFetcherTest} 覆盖）；本类钉的是端点契约：
 * 鉴权、成功路径的形状 / 落盘 / 台账（fileName 记外链原始地址）、
 * 失败可读且不落盘不登记、IP 限流。</p>
 *
 * <p>限流用例放最后（@Order(99)）：30 次/分钟的桶在类内共享，
 * 先跑会把其它用例的额度吃光。</p>
 */
@SpringBootTest(classes = { ShiGuangJiApplication.class, ExternalImageApiSmokeTest.StubFetchConfig.class })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExternalImageApiSmokeTest
{    /** 夹具标记：外链地址与台账按它匹配清理 */
    private static final String STUB_BASE = "https://example.com/qat68";

    private static final Pattern SAFE_PATH = Pattern.compile(
            "^/profile/upload/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.(png|jpg|jpeg|gif|bmp|webp)$");

    private static final byte[] PNG_BYTES = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4 };

    private static final byte[] WEBP_BYTES = { 'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P' };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private RedisCache redisCache;

    private String token;

    /** 本次测试产生的 storage_key，用于删文件 */
    private final Set<String> createdKeys = new HashSet<>();

    @BeforeAll
    void setUp() throws Exception
    {
        // 限流桶在 Redis 里跨 JVM 存活（60s 窗口）：上一次运行吃掉的额度会把本类全部用例顶到限流，
        // 先清掉本端点的桶再开始（模式只匹配 fetchImage 这一个方法，不影响其它接口的限流状态）
        redisCache.deleteObject(redisCache.keys("rate_limit:*CommonController-fetchImage"));
        cleanupFixtureRows();
        token = createTokenFor("admin");
    }

    @AfterAll
    void tearDown() throws Exception
    {
        cleanupFixtureRows();
    }

    // ------------------------------------------------------------------
    // 1. 鉴权（T-49）
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void anonymousTransferRejected() throws Exception
    {
        MvcResult result = fetch(null, STUB_BASE + "/good.png");
        assertThat(code(result)).as(body(result)).isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // 2. 成功路径：形状 / 落盘 / 台账（T-50、T-55）
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void pngTransferReturnsSafePathAndLedger() throws Exception
    {
        String url = STUB_BASE + "-good.png";
        MvcResult result = fetch(token, url);

        assertThat(code(result)).as(body(result)).isEqualTo(200);
        String path = path(result);
        assertThat(path).matches(SAFE_PATH);

        Path onDisk = toDiskPath(path);
        assertThat(Files.exists(onDisk)).as("文件应落在存储里：" + onDisk).isTrue();
        assertThat(Files.readAllBytes(onDisk)).isEqualTo(PNG_BYTES);

        Integer ledger = jdbcTemplate.queryForObject(
                "select count(1) from sys_file where storage_key = ? and file_name = ?", Integer.class, path, url);
        assertThat(ledger).as("台账恰好多一行，fileName 记外链原始地址").isEqualTo(1);
    }

    @Test
    @Order(3)
    void webpTransferKeepsWebpExtension() throws Exception
    {
        MvcResult result = fetch(token, STUB_BASE + "-good.webp");
        assertThat(code(result)).as(body(result)).isEqualTo(200);
        assertThat(path(result)).endsWith(".webp");
    }

    // ------------------------------------------------------------------
    // 3. 拒收：可读错误 + 不落盘 + 不登记台账（T-51、T-52）
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void fetchFailureReadableAndNoLedger() throws Exception
    {
        int before = countLedgerRows();
        MvcResult result = fetch(token, STUB_BASE + "-internal.png");

        assertThat(code(result)).as(body(result)).isEqualTo(500);
        assertThat(body(result)).contains("内网地址不允许抓取");
        assertThat(countLedgerRows()).as("抓取失败不得登记台账").isEqualTo(before);
    }

    @Test
    @Order(5)
    void nonImageContentTypeRejected() throws Exception
    {
        int before = countLedgerRows();
        MvcResult result = fetch(token, STUB_BASE + "-page.html");

        assertThat(code(result)).as(body(result)).isEqualTo(500);
        assertThat(body(result)).contains("不是图片");
        assertThat(countLedgerRows()).isEqualTo(before);
    }

    @Test
    @Order(6)
    void magicMismatchRejected() throws Exception
    {
        int before = countLedgerRows();
        MvcResult result = fetch(token, STUB_BASE + "-fake.png");

        assertThat(code(result)).as(body(result)).isEqualTo(500);
        assertThat(body(result)).contains("图片内容校验失败");
        assertThat(countLedgerRows()).isEqualTo(before);
    }

    // ------------------------------------------------------------------
    // 4. 限流（T-53）：30 次/分钟/IP，最后跑
    // ------------------------------------------------------------------

    @Test
    @Order(99)
    void rateLimitedAfterThirtyPerMinute() throws Exception
    {
        // 用「抓取失败」的地址消耗额度：不产生磁盘与台账产物，限流计数照常累加
        boolean limited = false;
        for (int i = 0; i < 40; i++)
        {
            MvcResult result = fetch(token, STUB_BASE + "-internal.png");
            if (body(result).contains("访问过于频繁"))
            {
                limited = true;
                break;
            }
        }
        assertThat(limited).as("同一 IP 一分钟内超出 30 次应被限流").isTrue();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private MvcResult fetch(String token, String url) throws Exception
    {
        MvcResult result = mockMvc.perform(post("/common/upload/fetch-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}")
                .header(HttpHeaders.AUTHORIZATION, token == null ? "" : bearer(token))).andReturn();
        Object uploaded = readQuietly(result, "$.path");
        if (uploaded != null && !String.valueOf(uploaded).isEmpty())
        {
            createdKeys.add(String.valueOf(uploaded));
        }
        return result;
    }

    private int countLedgerRows()
    {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from sys_file where file_name like ?", Integer.class, STUB_BASE + "%");
        return count == null ? 0 : count;
    }

    private static Path toDiskPath(String profilePath)
    {
        String objectKey = profilePath.substring(Constants.RESOURCE_PREFIX.length() + 1);
        return Paths.get(ShiGuangJiConfig.getProfile(), objectKey);
    }

    private static String body(MvcResult result) throws Exception
    {
        return result.getResponse().getContentAsString();
    }

    private static int code(MvcResult result) throws Exception
    {
        Object code = readQuietly(result, "$.code");
        return code == null ? -1 : ((Number) code).intValue();
    }

    private static String path(MvcResult result) throws Exception
    {
        Object path = readQuietly(result, "$.path");
        return path == null ? "" : String.valueOf(path);
    }

    private static Object readQuietly(MvcResult result, String jsonPath) throws Exception
    {
        try
        {
            return JsonPath.read(body(result), jsonPath);
        }
        catch (PathNotFoundException e)
        {
            return null;
        }
    }

    /** 清理夹具：先按台账删磁盘产物，再删台账行 */
    private void cleanupFixtureRows() throws Exception
    {
        List<String> keys = new ArrayList<>(createdKeys);
        keys.addAll(jdbcTemplate.queryForList(
                "select storage_key from sys_file where file_name like ?", String.class, STUB_BASE + "%"));
        for (String key : keys)
        {
            try
            {
                fileStorageService.delete(key.substring(Constants.RESOURCE_PREFIX.length() + 1));
            }
            catch (Exception ignored)
            {
                // 文件已不在也继续
            }
        }
        jdbcTemplate.update("delete from sys_file where file_name like ?", STUB_BASE + "%");
        createdKeys.clear();
    }

    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-external-image) Chrome/120.0");
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

    /**
     * 抓取器桩：按 URL 尾缀给出不同响应（不发起任何真实网络请求）
     */
    @TestConfiguration
    static class StubFetchConfig
    {
        @Bean
        @Primary
        ExternalImageFetcher stubExternalImageFetcher()
        {
            return url -> {
                if (url.endsWith("-good.png"))
                {
                    return new ExternalImageFetcher.FetchedImage(PNG_BYTES, "image/png");
                }
                if (url.endsWith("-good.webp"))
                {
                    return new ExternalImageFetcher.FetchedImage(WEBP_BYTES, "image/webp");
                }
                if (url.endsWith("-page.html"))
                {
                    return new ExternalImageFetcher.FetchedImage("<html/>".getBytes(), "text/html");
                }
                if (url.endsWith("-fake.png"))
                {
                    return new ExternalImageFetcher.FetchedImage(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 }, "image/png");
                }
                throw new ServiceException("内网地址不允许抓取");
            };
        }
    }
}
