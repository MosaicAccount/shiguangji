package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * 后端鉴权、数据隔离、公开范围、分页上限和软删除回收站接口冒烟测试。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>anonymousBrowseEndpointsReturnOk         —— 匿名 GET /app/item/list、/app/note/list 返回 200</li>
 *   <li>anonymousWriteEndpointsRejectedAs401     —— 匿名 POST/PUT/DELETE 写接口被拒，业务码 401</li>
 *   <li>nonOwnerWriteRejected                    —— 非本人写他人条目/笔记被拒（业务码 500，即后端实际的"403 拒绝表现"，见下方契约说明）</li>
 *   <li>loggedInUserSeesOnlyOwnData              —— 登录用户仅见自己数据，越权查看不可见</li>
 *   <li>anonymousGuestSeesOnlyPublicOwnerData    —— 匿名访客仅见 publicOwner（admin）的数据</li>
 *   <li>notePublicityVisibility                  —— 匿名仅能取 is_public='1' 笔记；私密笔记对访客不可见；本人可见自己私密笔记</li>
 *   <li>guestResponseMasking                     —— 匿名访客响应脱敏：comment/remark 不下发；登录态不受影响</li>
 *   <li>pageSizeCappedAt100                      —— 列表 pageSize 超上限（100）被截断</li>
 *   <li>recycleSoftDeleteRestoreFlow             —— 删除 → recycle/list 可见 → restore 后回到正常列表</li>
 * </ol>
 *
 * <p><b>响应契约说明（重要）</b>：本系统沿用 RuoYi 的 {@code ServletUtils.renderString}，统一以
 * <b>HTTP 200 + JSON 业务码</b> 表达结果，而非 RESTful HTTP 状态码：
 * <ul>
 *   <li>未登录访问受保护接口：HTTP 200，业务码 {@code code=401}（AuthenticationEntryPointImpl）；
 *       <b>不是</b> HTTP 401；</li>
 *   <li>登录但越权操作他人数据：HTTP 200，业务码 {@code code=500}、msg=“无权操作…”；
 *       <b>不是</b> HTTP 403。</li>
 * </ul>
 * 因此本测试断言的是“HTTP 200 + 业务码”这一实际对外契约。若后续后端改为标准 HTTP 状态码
 * （401/403），请同步调整对应断言。</p>
 *
 * <p><b>运行前提</b>：完整 Spring 上下文 + 真实 MySQL/Redis（对应 application-dev.yml：
 * MySQL localhost:13306、Redis localhost:16379，仓库根目录 sql/ 有建库脚本）。
 * 详见 docs/testing.md。本地开发容器未启动时，{@code mvn test} 会因无法连接数据源而失败——
 * 这是有意的失败（鉴权/隔离语义离开真实库无法验证），而不是需要 @Disabled 跳过的用例。</p>
 *
 * <p><b>数据策略</b>：直连开发库，所有测试夹具均带 {@code qat10} 前缀 tag；@BeforeAll 先清理
 * 历史残留（硬删除），@AfterAll 清理本次产生的全部行，不污染真实数据。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppApiAuthIsolationSmokeTest
{
    /** 测试夹具统一 tag 前缀（用于隔离与清理，见类注释"数据策略"） */
    private static final String TAG_MAIN = "qat10-main";
    /** 分页上限用例的夹具 tag（120 行，用于验证 pageSize 上限截断） */
    private static final String TAG_PAGE = "qat10-page";
    /** 回收站用例的夹具 tag / 标题（回收站列表仅支持 title 过滤，故 title 也用唯一值） */
    private static final String TAG_RECYCLE = "qat10-recycle";
    private static final String TITLE_RECYCLE = "qat10-recycle-title-唯一标识";

    /** 公开博主账号（application.yml shiguangji.public-owner） */
    private static final String PUBLIC_OWNER = "admin";
    /** 非管理员普通用户（sys_user 中已存在，user_id=2） */
    private static final String OTHER_USER = "shiguangji";

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
    private String otherToken;

    /** 夹具 ID（@BeforeAll 中经接口创建后记录） */
    private long adminItemId;
    private long publicNoteId;
    private long privateNoteId;
    private long otherUserItemId;

    @BeforeAll
    void setUp() throws Exception
    {
        cleanupFixtureRows();
        adminToken = createTokenFor(PUBLIC_OWNER);
        otherToken = createTokenFor(OTHER_USER);

        // 公开博主（admin）的条目：带私人字段 comment/remark，用于隔离与脱敏断言
        createItem(adminToken, "qat10 admin条目", TAG_MAIN, "admin私人短评", "admin私人备注");
        adminItemId = findSingleItemId(adminToken, TAG_MAIN, "qat10 admin条目");

        // 公开笔记（is_public=1）与私密笔记（is_public=0，库表默认值）
        createNote(adminToken, "qat10 公开笔记", "1", "公开笔记私人备注");
        publicNoteId = findSingleNoteId(adminToken, "qat10 公开笔记");
        createNote(adminToken, "qat10 私密笔记", "0", "私密笔记私人备注");
        privateNoteId = findSingleNoteId(adminToken, "qat10 私密笔记");

        // 非公开博主的普通用户条目：验证匿名访客不可见、登录用户互不可见
        createItem(otherToken, "qat10 other用户条目", TAG_MAIN, null, null);
        otherUserItemId = findSingleItemId(otherToken, TAG_MAIN, "qat10 other用户条目");

        // 分页上限夹具：120 行，验证 pageSize 超过上限 100 时被截断（直接 SQL 批量插入更高效，
        // 被测对象是 HTTP 列表接口的分页行为，而非插入接口）
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 120; i++)
        {
            rows.add(new Object[] { "MOVIE", "qat10 分页夹具 " + i, TAG_PAGE, PUBLIC_OWNER });
        }
        jdbcTemplate.batchUpdate(
                "insert into sgj_item (item_type, title, tags, create_by, create_time, del_flag) "
                        + "values (?, ?, ?, ?, sysdate(), '0')", rows);
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    /** 硬删除本测试类的全部夹具（含软删除产生的 del_flag='2' 行），恢复开发库原状 */
    private void cleanupFixtureRows()
    {
        jdbcTemplate.update("delete from sgj_note where tags like 'qat10%'");
        jdbcTemplate.update("delete from sgj_item where tags like 'qat10%'");
    }

    // ------------------------------------------------------------------
    // 1. 匿名浏览接口（只读放行）
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void anonymousBrowseEndpointsReturnOk() throws Exception
    {
        mockMvc.perform(get("/app/item/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/app/note/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ------------------------------------------------------------------
    // 2. 匿名写接口：统一 HTTP 200 + 业务码 401（见类注释"响应契约说明"）
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void anonymousWriteEndpointsRejectedAs401() throws Exception
    {
        String itemBody = "{\"title\":\"匿名写入不应成功\",\"itemType\":\"MOVIE\"}";

        mockMvc.perform(post("/app/item").contentType(MediaType.APPLICATION_JSON).content(itemBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(put("/app/item/" + adminItemId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名改写不应成功\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/app/item/" + adminItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        mockMvc.perform(post("/app/note").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"匿名笔记不应成功\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/app/note/" + publicNoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // 回收站接口未标 @Anonymous，匿名同样要求登录
        mockMvc.perform(get("/app/item/recycle/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/app/note/recycle/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // 校验：条目未被匿名改动
        mockMvc.perform(get("/app/item/" + adminItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("qat10 admin条目"));
    }

    // ------------------------------------------------------------------
    // 3. 非本人写操作：业务层拒绝（实际表现为业务码 500 + “无权操作”）
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void nonOwnerWriteRejected() throws Exception
    {
        // 非本人（shiguangji）修改 admin 的条目 → 拒绝，且数据不被修改
        mockMvc.perform(put("/app/item/" + adminItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权修改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("无权操作该条目"));

        // 非本人删除 admin 的笔记 → 拒绝
        mockMvc.perform(delete("/app/note/" + publicNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("无权操作该笔记"));

        // 校验：条目标题未被越权修改
        mockMvc.perform(get("/app/item/" + adminItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("qat10 admin条目"));
    }

    // ------------------------------------------------------------------
    // 4. 登录用户数据隔离：普通用户仅见自己的数据
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void loggedInUserSeesOnlyOwnData() throws Exception
    {
        JsonNode data = listItemsAsData(otherToken, TAG_MAIN, null);

        List<Long> ids = extractIds(data, "itemId");
        assertThat(ids).containsExactly(otherUserItemId); // 仅有自己的条目
        assertThat(ids).doesNotContain(adminItemId);      // 不可见公开博主的条目
    }

    // ------------------------------------------------------------------
    // 5. 匿名访客仅见 publicOwner（admin）的数据
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void anonymousGuestSeesOnlyPublicOwnerData() throws Exception
    {
        JsonNode data = listItemsAsData(null, TAG_MAIN, null);

        List<Long> ids = extractIds(data, "itemId");
        assertThat(ids).containsExactly(adminItemId);     // 仅公开博主的数据
        assertThat(ids).doesNotContain(otherUserItemId);  // 其他用户数据不可见
        // 且返回行的 create_by 均为 publicOwner
        for (JsonNode row : data)
        {
            assertThat(row.path("createBy").asText()).isEqualTo(PUBLIC_OWNER);
        }
    }

    // ------------------------------------------------------------------
    // 6. 笔记公开性：匿名仅见 is_public='1'；本人可见自己私密笔记
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void notePublicityVisibility() throws Exception
    {
        // 匿名列表：仅公开笔记
        JsonNode anonNotes = listNotesAsData(null);
        List<Long> anonIds = extractIds(anonNotes, "noteId");
        assertThat(anonIds).contains(publicNoteId);
        assertThat(anonIds).doesNotContain(privateNoteId);

        // 匿名详情：公开可见 / 私密不可见
        mockMvc.perform(get("/app/note/" + publicNoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/app/note/" + privateNoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("笔记不存在或无权访问"));

        // 匿名强制 isPublic='1'：请求显式携带 isPublic='0' 也无法取到私密笔记（参数被服务端覆盖）
        JsonNode forcedPublic = listNotesAsData(null);
        assertThat(extractIds(forcedPublic, "noteId")).doesNotContain(privateNoteId);

        // 本人（admin）：列表与详情均可访问自己的私密笔记
        JsonNode adminNotes = listNotesAsData(adminToken);
        List<Long> adminIds = extractIds(adminNotes, "noteId");
        assertThat(adminIds).contains(publicNoteId, privateNoteId);

        mockMvc.perform(get("/app/note/" + privateNoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.noteId").value((int) privateNoteId));
    }

    // ------------------------------------------------------------------
    // 7. 访客响应脱敏：匿名不下发 comment/remark 私人字段
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void guestResponseMasking() throws Exception
    {
        // 匿名：条目 comment/remark、笔记 remark 均被清空（hasNonNull 同时兼容
        // “字段值为 null”与“字段被剔除”两种序列化配置）
        JsonNode anonItems = listItemsAsData(null, TAG_MAIN, null);
        for (JsonNode row : anonItems)
        {
            assertThat(row.hasNonNull("comment")).as("匿名条目列表不应下发 comment").isFalse();
            assertThat(row.hasNonNull("remark")).as("匿名条目列表不应下发 remark").isFalse();
        }
        JsonNode anonItemDetail = bodyOf(mockMvc.perform(get("/app/item/" + adminItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        assertThat(anonItemDetail.path("data").hasNonNull("comment"))
                .as("匿名条目详情不应下发 comment").isFalse();
        assertThat(anonItemDetail.path("data").hasNonNull("remark"))
                .as("匿名条目详情不应下发 remark").isFalse();

        JsonNode anonNotes = listNotesAsData(null);
        for (JsonNode row : anonNotes)
        {
            assertThat(row.hasNonNull("remark")).as("匿名笔记列表不应下发 remark").isFalse();
        }

        // 登录态：本人可见自己的私人字段
        mockMvc.perform(get("/app/item/" + adminItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comment").value("admin私人短评"))
                .andExpect(jsonPath("$.data.remark").value("admin私人备注"));

        JsonNode adminNotes = listNotesAsData(adminToken);
        boolean sawPublicWithRemark = false;
        for (JsonNode row : adminNotes)
        {
            if (row.path("noteId").asLong() == publicNoteId)
            {
                assertThat(row.hasNonNull("remark")).as("登录态公开笔记应保留 remark").isTrue();
                sawPublicWithRemark = true;
            }
        }
        assertThat(sawPublicWithRemark).as("登录态列表应包含公开笔记夹具").isTrue();
    }

    // ------------------------------------------------------------------
    // 8. 分页上限（修复项）：pageSize 超过 100 被截断为 100
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void pageSizeCappedAt100() throws Exception
    {
        // 夹具共 120 行：pageSize 超过上限时应被截断为 100 行，total 仍为全量 120
        for (String pageSize : new String[] { "1000", "500", "101" })
        {
            MvcResult result = mockMvc.perform(get("/app/item/list")
                            .param("tags", TAG_PAGE)
                            .param("pageNum", "1")
                            .param("pageSize", pageSize)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andReturn();

            JsonNode body = bodyOf(result);
            assertThat(body.path("data").size())
                    .as("pageSize=%s 超上限时应仅返回 100 行", pageSize)
                    .isEqualTo(100);
            assertThat(body.path("total").asLong())
                    .as("total 不受每页上限影响，仍为全量 120")
                    .isEqualTo(120);
        }

        // 对照组：未传 pageSize 时默认每页 10 条
        mockMvc.perform(get("/app/item/list")
                        .param("tags", TAG_PAGE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));
    }

    // ------------------------------------------------------------------
    // 9. 回收站：删除 → recycle/list 可见 → restore 回到正常列表
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void recycleSoftDeleteRestoreFlow() throws Exception
    {
        // 准备一条待删除条目
        createItem(adminToken, TITLE_RECYCLE, TAG_RECYCLE, null, null);
        long recycleItemId = findSingleItemId(adminToken, TAG_RECYCLE, TITLE_RECYCLE);

        // 删除（软删除）
        mockMvc.perform(delete("/app/item/" + recycleItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 正常列表不可见，详情按“条目不存在”返回
        JsonNode normalList = listItemsAsData(adminToken, TAG_RECYCLE, null);
        assertThat(extractIds(normalList, "itemId")).doesNotContain(recycleItemId);
        mockMvc.perform(get("/app/item/" + recycleItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("条目不存在或无权访问"));

        // 回收站列表可见（回收站列表仅支持 title 过滤定位夹具）
        JsonNode recycleList = listAsData("/app/item/recycle/list", adminToken, null, TITLE_RECYCLE);
        assertThat(extractIds(recycleList, "itemId")).contains(recycleItemId);

        // 恢复后回到正常列表，且回收站不再可见
        mockMvc.perform(post("/app/item/recycle/restore/" + recycleItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        JsonNode restored = listItemsAsData(adminToken, TAG_RECYCLE, null);
        assertThat(extractIds(restored, "itemId")).contains(recycleItemId);

        JsonNode recycleAfter = listAsData("/app/item/recycle/list", adminToken, null, TITLE_RECYCLE);
        assertThat(extractIds(recycleAfter, "itemId")).doesNotContain(recycleItemId);
    }

    // ------------------------------------------------------------------
    // 10. 首页公开统计：匿名 recentNotes/noteTotal 仅统计公开笔记
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void anonymousHomeStatsAndRecentNotesPublicOnly() throws Exception
    {
        // 匿名首页：recentNotes 仅含公开笔记，且不下发 remark
        JsonNode anonHome = bodyOf(mockMvc.perform(get("/app/home/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());

        List<Long> anonRecentIds = extractIds(anonHome.path("data").path("recentNotes"), "noteId");
        assertThat(anonRecentIds).contains(publicNoteId);
        assertThat(anonRecentIds).doesNotContain(privateNoteId);
        for (JsonNode note : anonHome.path("data").path("recentNotes"))
        {
            assertThat(note.hasNonNull("remark")).as("匿名首页最近笔记不应下发 remark").isFalse();
        }
        // 时间线/心愿单同样脱敏
        for (String key : new String[] { "timeline", "wishlist" })
        {
            for (JsonNode item : anonHome.path("data").path(key))
            {
                assertThat(item.hasNonNull("comment")).as("匿名首页 %s 不应下发 comment", key).isFalse();
                assertThat(item.hasNonNull("remark")).as("匿名首页 %s 不应下发 remark", key).isFalse();
            }
        }

        // 匿名 noteTotal 与匿名笔记列表 total 一致（都只统计公开博主的公开笔记；
        // 列表不传 tags 以保持与首页统计同一口径）
        long anonPublicTotal = bodyOf(mockMvc.perform(get("/app/note/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()).path("total").asLong();
        assertThat(anonHome.path("data").path("summary").path("noteTotal").asLong())
                .as("匿名首页 noteTotal 应等于公开博主公开笔记总数")
                .isEqualTo(anonPublicTotal);

        // 登录态（博主）：noteTotal 含私密笔记，多于匿名公开口径，且与登录列表 total 同口径
        JsonNode adminHome = bodyOf(mockMvc.perform(get("/app/home/index")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn());
        long adminListTotal = bodyOf(mockMvc.perform(get("/app/note/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()).path("total").asLong();
        long adminTotal = adminHome.path("data").path("summary").path("noteTotal").asLong();
        assertThat(adminTotal).as("登录态 noteTotal 应含私密笔记（大于匿名公开口径）").isGreaterThan(anonPublicTotal);
        assertThat(adminTotal).as("登录态 noteTotal 应等于登录列表 total").isEqualTo(adminListTotal);
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

    /** 经接口创建条目（create_by 由后端强制取当前登录人） */
    private void createItem(String token, String title, String tags, String comment, String remark) throws Exception
    {
        mockMvc.perform(post("/app/item")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "itemType", "MOVIE",
                                "tags", tags,
                                "comment", comment == null ? "" : comment,
                                "remark", remark == null ? "" : remark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 经接口创建笔记（isPublic："1"公开 / "0"私密） */
    private void createNote(String token, String title, String isPublic, String remark) throws Exception
    {
        mockMvc.perform(post("/app/note")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "tags", TAG_MAIN,
                                "content", "qat10 内容",
                                "isPublic", isPublic,
                                "remark", remark))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** POST 不回传主键，通过唯一 tags(+title) 从列表接口定位条目夹具 ID */
    private long findSingleItemId(String token, String tags, String title) throws Exception
    {
        JsonNode data = listItemsAsData(token, tags, title);
        assertThat(data.size()).as("按唯一 tags=%s 应恰好定位到 1 条夹具", tags).isEqualTo(1);
        return data.get(0).path("itemId").asLong();
    }

    /** 通过唯一 title 从列表接口定位笔记夹具 ID */
    private long findSingleNoteId(String token, String title) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/note/list")
                        .param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = bodyOf(result).path("data");
        assertThat(data.size()).as("按唯一 title=%s 应恰好定位到 1 条笔记", title).isEqualTo(1);
        return data.get(0).path("noteId").asLong();
    }

    /** 以指定登录态（null=匿名）查询条目列表并返回 data 数组 */
    private JsonNode listItemsAsData(String token, String tags, String title) throws Exception
    {
        return listAsData("/app/item/list", token, tags, title);
    }

    /** 以指定登录态（null=匿名）查询笔记列表并返回 data 数组（tags 固定夹具 tag） */
    private JsonNode listNotesAsData(String token) throws Exception
    {
        return listAsData("/app/note/list", token, TAG_MAIN, null);
    }

    private JsonNode listAsData(String url, String token, String tags, String title) throws Exception
    {
        MockHttpServletRequestBuilder builder = get(url).param("tags", tags);
        if (title != null)
        {
            builder.param("title", title);
        }
        if (token != null)
        {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data");
    }

    private JsonNode bodyOf(MvcResult result) throws Exception
    {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<Long> extractIds(JsonNode data, String idField)
    {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : data)
        {
            ids.add(row.path(idField).asLong());
        }
        return ids;
    }
}
