package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import com.shiguangji.file.storage.FileStorageService;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;
import com.shiguangji.web.service.image.ExternalImageFetcher;
import com.shiguangji.web.service.image.HttpExternalImageFetcher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 外链转存的装配冒烟（issue #68）：不用桩——上下文必须装配出**真实现**。
 *
 * <p>钉三件事：①真实现 @Component 注册后 {@code NoopExternalImageFetcher} 自动退位
 * （ConditionalOnMissingBean 生效）；②{@code sgj.image-fetch.*} 配置真实绑定到 @Value 字段
 * （纯单测注入字段覆盖不到这层）；③端到端走通——本地桩图经 fetch-image 落盘、登记台账、
 * 可回读。{@code allow-private-ranges=true} 测试开关放行本机地址（生产默认 false）。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class,
        properties = "sgj.image-fetch.allow-private-ranges=true")
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalImageRealFetchSmokeTest
{
    private static final String MARK = "qat68e2e";

    private static final Pattern SAFE_PATH = Pattern.compile(
            "^/profile/upload/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.(png|jpg|jpeg|gif|bmp|webp)$");

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4 };

    private static HttpServer server;

    private static String imageUrl;

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

    @Autowired
    private ExternalImageFetcher fetcher;

    private String token;

    private final Set<String> createdKeys = new HashSet<>();

    @BeforeAll
    void setUp() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/" + MARK + ".png", ExternalImageRealFetchSmokeTest::servePng);
        server.start();
        imageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/" + MARK + ".png";

        // 与 ExternalImageApiSmokeTest 同一把限流桶（key 按方法聚合，跨类共享 Redis）
        redisCache.deleteObject(redisCache.keys("rate_limit:*CommonController-fetchImage"));
        token = createTokenFor("admin");
    }

    @AfterAll
    void tearDown() throws Exception
    {
        server.stop(0);
        cleanupFixtureRows();
    }

    @Test
    void realFetcherIsWiredAndNoopBackedOff()
    {
        assertThat(fetcher).as("真实现注册后 Noop 占位必须退位").isInstanceOf(HttpExternalImageFetcher.class);
    }

    @Test
    void endpointTransfersFromLocalStubEndToEnd() throws Exception
    {
        MvcResult result = mockMvc.perform(post("/common/upload/fetch-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + imageUrl + "\"}")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();

        assertThat(readQuietly(result, "$.code")).as(body(result)).isEqualTo(200);
        String path = String.valueOf(readQuietly(result, "$.path"));
        assertThat(path).matches(SAFE_PATH);
        createdKeys.add(path);

        Path onDisk = Paths.get(ShiGuangJiConfig.getProfile(),
                path.substring(Constants.RESOURCE_PREFIX.length() + 1));
        assertThat(Files.readAllBytes(onDisk)).as("桩图应原字节落盘").isEqualTo(PNG);

        Integer ledger = jdbcTemplate.queryForObject(
                "select count(1) from sys_file where storage_key = ? and file_name = ?",
                Integer.class, path, imageUrl);
        assertThat(ledger).as("台账记外链原始地址").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static void servePng(HttpExchange exchange) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, PNG.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(PNG);
        }
    }

    private void cleanupFixtureRows() throws Exception
    {
        List<String> keys = new ArrayList<>(createdKeys);
        keys.addAll(jdbcTemplate.queryForList(
                "select storage_key from sys_file where file_name like ?", String.class, "http://127.0.0.1:" + "%/" + MARK + "%"));
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
        jdbcTemplate.update("delete from sys_file where file_name like ?", "%/" + MARK + ".png");
        createdKeys.clear();
    }

    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-external-image-real) Chrome/120.0");
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

    private static String body(MvcResult result) throws Exception
    {
        return result.getResponse().getContentAsString();
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
}
