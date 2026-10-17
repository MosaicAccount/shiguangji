package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    /** 影视夹具 A：WANT + 计划日期 2026-10-13（相对今天 10-14 属「逾期」）；T-72 末尾被流转为 DOING */
    private static final String TITLE_A = TITLE + "-影视A";

    /** 书籍夹具 B：DOING + 计划日期 2026-10-15（「本周」），带短评供脱敏断言 */
    private static final String TITLE_B = TITLE + "-书籍B";

    /** 影视夹具 C：WANT、无计划日期（「未排期」） */
    private static final String TITLE_C = TITLE + "-影视C";

    /** 影视夹具 D：DONE（经 /complete 流转，用于排除断言与时间线回归） */
    private static final String TITLE_D = TITLE + "-影视D";

    /** 地点夹具 E：用于 T-91（地点不得被置成 DOING） */
    private static final String TITLE_E = TITLE + "-地点E";

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
    // 2. T-75：计划接口的集合 / 排序 / 分页（提交 feat(business) plan/list）
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void planListCollectionOrderAndPagination() throws Exception
    {
        // 夹具补齐：B=DOING+日期 10-15+短评；C=WANT 无日期；D=WANT→complete→DONE
        createItem(TITLE_B, "BOOK", null);
        long idB = findItemId(TITLE_B);
        mockMvc.perform(put("/app/item/{itemId}", idB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "DOING", "startDate", "2026-10-15", "comment", "在读中，第三章"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        createItem(TITLE_C, "MOVIE", null);

        createItem(TITLE_D, "MOVIE", null);
        long idD = findItemId(TITLE_D);
        mockMvc.perform(post("/app/item/{itemId}/complete", idD)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("finishDate", "2026-10-14"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 集合与排序：只含 WANT/DOING（D 被排除），有日期的按日期升序，无日期的置后
        JsonNode rows = planList(null, null);
        assertThat(rows.size()).as("计划页应恰含 A/B/C 三条夹具").isEqualTo(3);
        assertThat(rows.get(0).path("title").asText()).isEqualTo(TITLE_A);
        assertThat(rows.get(0).path("status").asText()).isEqualTo("DOING");
        assertThat(rows.get(1).path("title").asText()).isEqualTo(TITLE_B);
        assertThat(rows.get(2).path("title").asText()).isEqualTo(TITLE_C);
        assertThat(rows.get(2).path("startDate").isMissingNode() || rows.get(2).path("startDate").isNull())
                .as("未排期条目不应带计划日期").isTrue();

        // 分页：total 是符合条件总数而非当前页长度；翻页无重复无遗漏
        JsonNode page1 = planList(1, 2);
        assertThat(page1.size()).isEqualTo(2);
        JsonNode page2 = planList(2, 2);
        assertThat(page2.size()).isEqualTo(1);
        assertThat(page2.get(0).path("title").asText()).isEqualTo(TITLE_C);
        assertThat(planListTotal(1, 2)).isEqualTo(3);
    }

    // ------------------------------------------------------------------
    // 3. T-76：计划接口的可见范围
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void planListVisibilityByScope() throws Exception
    {
        // 匿名访客：心愿是私人模块（结论 108），不开放匿名访问，返回业务码 401 且不下发任何行
        String guestBody = mockMvc.perform(get("/app/item/plan/list").param("title", TITLE))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(guestBody).path("code").asInt())
                .as("匿名访问计划接口应为 401，实际响应：%s", guestBody).isEqualTo(401);
        assertThat(objectMapper.readTree(guestBody).path("data").isMissingNode()
                || objectMapper.readTree(guestBody).path("data").isNull())
                .as("匿名响应不得带任何条目").isTrue();

        // 其他登录用户：只见自己（夹具都建在 admin 名下，shiguangji 应看到 0 条）
        String otherToken = createTokenFor(OTHER_USER);
        JsonNode other = objectMapper.readTree(mockMvc.perform(get("/app/item/plan/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .param("title", TITLE))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(other.size()).as("其他登录用户不应看到 admin 的计划夹具").isZero();
    }

    // ------------------------------------------------------------------
    // 4. T-77：心愿为私人模块 —— 首页汇总不向访客下发心愿数字
    //    （原「访客脱敏」用例随接口私有化作废：访客现在拿不到该接口，见 T-76）
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void homeSummaryHidesPlanForGuest() throws Exception
    {
        // 户主视角：在想 / 在做确有数字，否则下面的清零断言没有意义
        JsonNode owner = homeIndexSummary();
        assertThat(owner.path("wishTotal").asLong() + owner.path("doingTotal").asLong())
                .as("户主应能看到心愿数字").isGreaterThan(0);

        // 访客视角：心愿数字清零、分态映射为空；公开目录口径不受影响
        JsonNode guest = objectMapper.readTree(mockMvc.perform(get("/app/home/index"))
                .andReturn().getResponse().getContentAsString()).path("data").path("summary");
        assertThat(guest.path("wishTotal").asLong()).as("访客不应看到在想数字").isZero();
        assertThat(guest.path("doingTotal").asLong()).as("访客不应看到在做数字").isZero();
        assertThat(guest.path("wish").size()).as("访客的「在想」分态映射应为空").isZero();
        assertThat(guest.path("doing").size()).as("访客的「在做」分态映射应为空").isZero();
        assertThat(guest.path("itemTotal").asLong()).as("公开目录口径（条目总数）对访客不变").isGreaterThan(0);
    }

    // ------------------------------------------------------------------
    // 5. T-79：时间线仍只含 DONE（回归：DOING/WANT 不进时间线）
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void timelineContainsOnlyDone() throws Exception
    {
        // pageSize=50 覆盖全库 DONE（当前约 24 条），避免“D 恰好在第二页”的偶发
        // 响应形状与前端 listFrontHomeTimeline 的读法一致：行在 data、总数在 total
        JsonNode timeline = objectMapper.readTree(mockMvc.perform(get("/app/home/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("pageNum", "1")
                        .param("pageSize", "50"))
                .andReturn().getResponse().getContentAsString());
        JsonNode rows = timeline.path("data");
        assertThat(rows.isArray() && rows.size() > 0).as("时间线应有数据").isTrue();
        Set<String> statuses = new HashSet<>();
        rows.forEach(r -> statuses.add(r.path("status").asText()));
        assertThat(statuses).as("时间线只允许出现 DONE").containsExactly("DONE");

        Set<String> titles = new HashSet<>();
        rows.forEach(r -> titles.add(r.path("title").asText()));
        assertThat(titles).as("完成的夹具 D 应出现在时间线").contains(TITLE_D);
        assertThat(titles).as("未完成夹具不得进入时间线").doesNotContain(TITLE_A, TITLE_B, TITLE_C);
    }

    // ------------------------------------------------------------------
    // 6. T-73：buildSummary 显式三态（提交 fix(business) 统计与心愿单）
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void summaryThreeWayInvariant() throws Exception
    {
        JsonNode summary = homeIndexSummary();
        long itemTotal = summary.path("itemTotal").asLong();
        long doneTotal = summary.path("doneTotal").asLong();
        long wishTotal = summary.path("wishTotal").asLong();
        long doingTotal = summary.path("doingTotal").asLong();

        // 自洽：总数 == 三态之和（原先的 else 兜底会破坏这条）
        assertThat(itemTotal).as("itemTotal 应等于三态之和").isEqualTo(doneTotal + wishTotal + doingTotal);

        // 与库内实际分布逐态对账（admin 视角不限制 create_by，统计的是全库未删条目）
        assertThat(doneTotal).isEqualTo(countByStatus("DONE"));
        assertThat(wishTotal).isEqualTo(countByStatus("WANT"));
        assertThat(doingTotal).as("DOING 必须独立成列，不得混入 wish").isEqualTo(countByStatus("DOING"));
    }

    // ------------------------------------------------------------------
    // 7. T-74：心愿单纳入 DOING 且按计划日期排
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void wishlistSingleSourceAndPaging() throws Exception
    {
        // 首页不再下发 wishlist：心愿单与计划页是同一个集合 + 同一个排序，
        // 两份来源各带一页会在分页边界错位（0.0.3 修订），统一走 /app/item/plan/list
        JsonNode home = objectMapper.readTree(mockMvc.perform(get("/app/home/index")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(home.path("wishlist").isMissingNode()).as("首页响应不应再含 wishlist").isTrue();
        assertThat(home.path("summary").path("doingTotal").asLong()).isGreaterThanOrEqualTo(0);

        // 分页边界：page1 ∪ page2 覆盖全部夹具且无重复（心愿单靠它翻页）
        JsonNode page1 = planList(1, 2);
        JsonNode page2 = planList(2, 2);
        Set<String> seen = new HashSet<>();
        for (JsonNode row : page1)
        {
            assertThat(seen.add(row.path("title").asText())).as("第一页内不得重复").isTrue();
        }
        int overlap = 0;
        for (JsonNode row : page2)
        {
            if (!seen.add(row.path("title").asText()))
            {
                overlap++;
            }
        }
        assertThat(overlap).as("两页之间不得重叠").isZero();
        assertThat(seen).as("两页并集应覆盖 A/B/C，且不含 DONE 的 D")
                .contains(TITLE_A, TITLE_B, TITLE_C).doesNotContain(TITLE_D);

        // 排序与状态（心愿单展示口径）：有日期的按日期升序在前、无日期置后
        JsonNode all = planList(1, 50);
        assertThat(indexOfTitle(all, TITLE_A)).isLessThan(indexOfTitle(all, TITLE_B));
        assertThat(indexOfTitle(all, TITLE_B)).isLessThan(indexOfTitle(all, TITLE_C));
        assertThat(all.get(indexOfTitle(all, TITLE_A)).path("status").asText()).isEqualTo("DOING");
        assertThat(indexOfTitle(all, TITLE_D)).as("DONE 不得进入心愿单").isLessThan(0);
    }

    // ------------------------------------------------------------------
    // 8. T-91：地点不得被置成 DOING（0.0.3 D-5 的单点闸门）
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void placeCannotBeDoing() throws Exception
    {
        createItem(TITLE_E, "PLACE", null);
        long idE = findItemId(TITLE_E);

        // 地点字典没有 DOING：放它落库就会得到一个「显示不出来」的状态，故在闸门处拒绝
        mockMvc.perform(put("/app/item/{itemId}", idE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DOING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", containsString("地点不支持")));
        assertThat(detail(idE).path("status").asText()).as("被拒后状态不变").isEqualTo("WANT");

        // 计划日期对地点仍然可用（地点用日期表达计划）
        mockMvc.perform(put("/app/item/{itemId}", idE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("startDate", "2026-11-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(detail(idE).path("startDate").asText()).startsWith("2026-11-01");
    }

    // ------------------------------------------------------------------
    // 9. T-97（issue #92，结论 109）：计划接口胶囊筛选（status 单档）与分态总数口径
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void planListStatusFilterAndStatusTotals() throws Exception
    {
        // 单档筛选只保留该状态：WANT = C（无日期置后）+ E（地点，2026-11-01）；DOING = A → B
        assertThat(titles(planListRoot("WANT").path("data"))).containsExactly(TITLE_E, TITLE_C);
        assertThat(titles(planListRoot("DOING").path("data"))).containsExactly(TITLE_A, TITLE_B);

        // 非法档（DONE / 乱码）不做单选，仍按两态合集返回，不会把列表筛空
        assertThat(titles(planListRoot("DONE").path("data")))
                .containsExactly(TITLE_A, TITLE_B, TITLE_E, TITLE_C);
        assertThat(titles(planListRoot("PAUSED").path("data"))).hasSize(4);

        // total 跟随筛选（筛选后的条数），不是全集条数
        assertThat(planListRoot("DOING").path("total").asLong()).isEqualTo(2);

        // 分态总数：全库口径（admin 不限 create_by，与 T-76 同源）、不随筛选变化，并与库内分布对账
        long wantTotal = planListRoot("DOING").path("wantTotal").asLong();
        long doingTotal = planListRoot("DOING").path("doingTotal").asLong();
        assertThat(wantTotal).isEqualTo(countByStatus("WANT"));
        assertThat(doingTotal).isEqualTo(countByStatus("DOING"));
        assertThat(planListRoot("WANT").path("wantTotal").asLong()).isEqualTo(wantTotal);
        assertThat(planListRoot("WANT").path("doingTotal").asLong()).isEqualTo(doingTotal);
        assertThat(planListRoot(null).path("wantTotal").asLong()).isEqualTo(wantTotal);
        assertThat(planListRoot(null).path("doingTotal").asLong()).isEqualTo(doingTotal);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 首页 summary 节点（admin 视角 = 全库口径） */
    private JsonNode homeIndexSummary() throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(get("/app/home/index")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getContentAsString()).path("data").path("summary");
    }

    /** 库内未删条目按状态计数（对账用，绕开被测的聚合逻辑本身） */
    private long countByStatus(String status)
    {
        Long cnt = jdbcTemplate.queryForObject(
                "select count(*) from sgj_item where del_flag = '0' and status = ?", Long.class, status);
        return cnt == null ? 0 : cnt;
    }

    /** 在数组里按 title 找下标，找不到返回 -1 */
    private int indexOfTitle(JsonNode array, String title)
    {
        for (int i = 0; i < array.size(); i++)
        {
            if (title.equals(array.get(i).path("title").asText()))
            {
                return i;
            }
        }
        return -1;
    }

    /** 取计划页 data 数组（admin 视角，可带分页参数） */
    private JsonNode planList(Integer pageNum, Integer pageSize) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(planListRequest(pageNum, pageSize))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    /** 计划页响应根节点（admin 视角，可带单档 status；title 夹具前缀与其他用例一致） */
    private JsonNode planListRoot(String status) throws Exception
    {
        MockHttpServletRequestBuilder builder = planListRequest(1, 50);
        if (status != null)
        {
            builder = builder.param("status", status);
        }
        return objectMapper.readTree(mockMvc.perform(builder)
                .andReturn().getResponse().getContentAsString());
    }

    /** 取 data 数组的标题列表（断言集合与排序用） */
    private List<String> titles(JsonNode rows)
    {
        List<String> result = new ArrayList<>();
        for (JsonNode row : rows)
        {
            result.add(row.path("title").asText());
        }
        return result;
    }

    /** 取计划页 total（分页场景下 data 只含当前页，total 必须单独取） */
    private long planListTotal(Integer pageNum, Integer pageSize) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(planListRequest(pageNum, pageSize))
                .andReturn().getResponse().getContentAsString()).path("total").asLong();
    }

    private MockHttpServletRequestBuilder planListRequest(Integer pageNum, Integer pageSize)
    {
        MockHttpServletRequestBuilder builder = get("/app/item/plan/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .param("title", TITLE);
        if (pageNum != null)
        {
            builder = builder.param("pageNum", String.valueOf(pageNum));
        }
        if (pageSize != null)
        {
            builder = builder.param("pageSize", String.valueOf(pageSize));
        }
        return builder;
    }

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
