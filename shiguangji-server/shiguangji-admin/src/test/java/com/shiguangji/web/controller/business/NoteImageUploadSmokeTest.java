package com.shiguangji.web.controller.business;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
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
import com.shiguangji.file.storage.FileStorageService;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 笔记正文插图上传（0.0.2 FR-002-9，issue #56）的接口契约
 *
 * <ol>
 *   <li>anonymousUploadRejected    —— 匿名返回业务码 401（本端点没有 @Anonymous，与 /common/upload 一致）</li>
 *   <li>pngUploadReturnsSafePath   —— uuid 命名的站内相对路径；磁盘能按字节读回；台账恰好登记一行</li>
 *   <li>chineseAndSpacedNameIsSafe —— 原文件名含中文与空格时，path 里都不出现（正文引用的字符集保证）</li>
 *   <li>webpAccepted               —— 放行 webp（IMAGE_EXTENSION 里没有它，这是本端点与封面 / 头像的差别）</li>
 *   <li>txtRejected / svgRejected  —— 非白名单格式被拒，且不登记台账</li>
 * </ol>
 *
 * <p>为什么断言 path 的形状而不是只断言 200：正文里保存的就是这条路径，孤儿清理的引用抽取
 * （ProfileRefExtractor）与前端改写都依赖它落在安全字符集内（见设计 结论 52 / 结论 56）。</p>
 *
 * <p>数据策略：直连开发库。夹具文件名统一带 {@code qat56} 前缀，@BeforeAll / @AfterAll 按它清理
 * 台账与磁盘产物。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NoteImageUploadSmokeTest
{
    /** 夹具标记：原文件名前缀，台账与磁盘产物按它匹配清理 */
    private static final String MARK = "qat56";

    /** 公开博主账号（application.yml shiguangji.public-owner），同时是管理员 */
    private static final String UPLOADER = "admin";

    /** uuid 命名的站内相对路径：/profile/upload/yyyy/MM/dd/<32位hex>.<ext> */
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

    private String token;

    /** 本次测试产生的 storage_key，用于删文件 */
    private final Set<String> createdKeys = new HashSet<>();

    @BeforeAll
    void setUp() throws Exception
    {
        cleanupFixtureRows();
        token = createTokenFor(UPLOADER);
    }

    @AfterAll
    void tearDown() throws Exception
    {
        cleanupFixtureRows();
    }

    // ------------------------------------------------------------------
    // 1. 鉴权
    // ------------------------------------------------------------------

    @Test
    void anonymousUploadRejected() throws Exception
    {
        MvcResult result = mockMvc.perform(multipart("/common/upload/note-image")
                .file(png(MARK + "-anon.png"))).andReturn();

        assertThat(code(result)).as("匿名不得上传").isEqualTo(401);
    }

    // ------------------------------------------------------------------
    // 2. 成功路径：形状 / 落盘 / 台账
    // ------------------------------------------------------------------

    @Test
    void pngUploadReturnsSafePath() throws Exception
    {
        MvcResult result = upload(png(MARK + "-shot.png"));

        assertThat(code(result)).as(body(result)).isEqualTo(200);

        String path = path(result);
        assertThat(path).as("path 必须是 uuid 命名的站内相对路径").matches(SAFE_PATH);

        Path onDisk = toDiskPath(path);
        assertThat(Files.exists(onDisk)).as("文件应落在存储里：" + onDisk).isTrue();
        assertThat(Files.readAllBytes(onDisk)).as("内容按字节读回").isEqualTo(PNG_BYTES);

        assertThat(countLedgerRows(path)).as("台账恰好多一行").isEqualTo(1);
    }

    @Test
    void chineseAndSpacedNameIsSafe() throws Exception
    {
        MvcResult result = upload(png(MARK + "-截图 2026.png"));

        assertThat(code(result)).as(body(result)).isEqualTo(200);

        String path = path(result);
        assertThat(path).matches(SAFE_PATH);
        assertThat(path).as("中文与空格不得进路径").doesNotContain("截图").doesNotContain(" ");
    }

    @Test
    void webpAccepted() throws Exception
    {
        MockMultipartFile file = new MockMultipartFile("file", MARK + "-a.webp", "image/webp", WEBP_BYTES);
        MvcResult result = upload(file);

        assertThat(code(result)).as(body(result)).isEqualTo(200);
        assertThat(path(result)).endsWith(".webp");
    }

    // ------------------------------------------------------------------
    // 3. 拒收：不登记台账
    // ------------------------------------------------------------------

    @Test
    void nonMultipartRequestRejectedWithReadableError() throws Exception
    {
        // 前端若把 FormData 序列化成 JSON（axios 默认 Content-Type=application/json 时的坑，
        // 见 utils/request.ts 的拦截器），后端 MultipartFile 参数会是 null——
        // 必须回一句能看懂的错，而不是一行 NPE 文案
        MvcResult result = mockMvc.perform(post("/common/upload/note-image")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();

        assertThat(code(result)).as(body(result)).isNotEqualTo(200);
        assertThat(body(result)).as("错误信息必须可读").contains("上传文件为空").doesNotContain("NullPointerException");
    }

    @Test
    void txtRejected() throws Exception
    {
        assertRejected(new MockMultipartFile("file", MARK + "-note.txt", "text/plain", "hello".getBytes()));
    }

    @Test
    void svgRejected() throws Exception
    {
        // svg 能带脚本，与既有白名单口径一致：不收（要收的话是安全决定，不是顺手加一项）
        assertRejected(new MockMultipartFile("file", MARK + "-icon.svg", "image/svg+xml", "<svg/>".getBytes()));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void assertRejected(MockMultipartFile file) throws Exception
    {
        MvcResult result = upload(file);

        assertThat(code(result)).as(body(result)).isNotEqualTo(200);
        assertThat(readQuietly(result, "$.path")).as("被拒时不应回路径").isNull();

        Integer ledger = jdbcTemplate.queryForObject(
                "select count(1) from sys_file where file_name = ?", Integer.class, file.getOriginalFilename());
        assertThat(ledger).as("被拒的上传不得登记台账").isZero();
    }

    private MvcResult upload(MockMultipartFile file) throws Exception
    {
        MvcResult result = mockMvc.perform(multipart("/common/upload/note-image")
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();

        // 成功的上传记下 key，交给清理
        Object uploaded = readQuietly(result, "$.path");
        if (uploaded != null && !String.valueOf(uploaded).isEmpty())
        {
            createdKeys.add(String.valueOf(uploaded));
        }
        return result;
    }

    private static MockMultipartFile png(String originalFilename)
    {
        return new MockMultipartFile("file", originalFilename, "image/png", PNG_BYTES);
    }

    private static Path toDiskPath(String profilePath)
    {
        String objectKey = profilePath.substring(Constants.RESOURCE_PREFIX.length() + 1);
        return Paths.get(ShiGuangJiConfig.getProfile(), objectKey);
    }

    private int countLedgerRows(String storageKey)
    {
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from sys_file where storage_key = ?", Integer.class, storageKey);
        return count == null ? 0 : count;
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

    /** 读响应 JSON 的一个字段；字段不存在时返回 null，而不是抛异常 */
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

    /**
     * 清理夹具：先按台账把磁盘产物删掉，再删台账行（台账是 storage_key 的唯一线索）
     */
    private void cleanupFixtureRows() throws Exception
    {
        List<String> keys = new ArrayList<>(createdKeys);
        keys.addAll(jdbcTemplate.queryForList(
                "select storage_key from sys_file where file_name like ?", String.class, MARK + "%"));

        for (String key : keys)
        {
            try
            {
                fileStorageService.delete(key.substring(Constants.RESOURCE_PREFIX.length() + 1));
            }
            catch (Exception ignored)
            {
                // 文件已不在也继续，别让清理挡住其它断言
            }
        }
        jdbcTemplate.update("delete from sys_file where file_name like ?", MARK + "%");
        createdKeys.clear();
    }

    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-note-image) Chrome/120.0");
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
