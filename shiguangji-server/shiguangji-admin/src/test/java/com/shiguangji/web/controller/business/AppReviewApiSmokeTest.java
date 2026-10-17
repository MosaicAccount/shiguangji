package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguangji.ShiGuangJiApplication;
import com.shiguangji.common.core.domain.entity.SysUser;
import com.shiguangji.common.core.domain.model.LoginUser;
import com.shiguangji.framework.web.service.TokenService;
import com.shiguangji.system.service.ISysUserService;

/**
 * 0.0.4 回顾接口冒烟测试（issue #86）：用例编号对应 docs/design/review-testcases.md A 组。
 *
 * <p>数据策略：夹具标题统一 {@code qat13} 前缀，直接经 JDBC 造数（回顾是只读聚合，
 * 无写入接口可复用），@BeforeAll/@AfterAll 按 title 前缀硬删除；断言口径为夹具之间的
 * 相对关系，不依赖库内其他真实数据。</p>
 *
 * <p><b>运行前提</b>：完整 Spring 上下文 + 真实 MySQL/Redis（dev profile：13306 / 16379）。</p>
 *
 * @author shiguangji
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppReviewApiSmokeTest
{
    private static final String TITLE = "qat13-回顾冒烟";

    private static final String PUBLIC_OWNER = "admin";

    @Autowired
    private MockMvc mockMvc;

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
        adminToken = createTokenFor(PUBLIC_OWNER);
        insertFixtureRows();
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    private void cleanupFixtureRows()
    {
        // sgj_item_place / sgj_item_photo 随 sgj_item 外键级联清理
        jdbcTemplate.update("delete from sgj_item where title like '" + TITLE + "%'");
        jdbcTemplate.update("delete from sgj_note where title like '" + TITLE + "%'");
    }

    /**
     * 夹具（create_by = admin，finish_date 全部 2024，覆盖归因/分型/评分/标签/坐标各分支）：
     * A 影视 DONE 9.5（上榜）、B 影视 DONE 无评分（不上榜）、C 书籍 DONE 8.0、
     * D 地点 DONE 有坐标福建、E 地点 DONE 无坐标云南、F 影视 DONE finish_date 2023（不进 2024）
     */
    private void insertFixtureRows()
    {
        insertItem(TITLE + "-影视A", "MOVIE", "10.0", "人文,剧情", "2024-05-01");
        insertItem(TITLE + "-影视B", "MOVIE", null, "剧情", "2024-06-01");
        insertItem(TITLE + "-书籍C", "BOOK", "8.0", "人文", "2024-07-01");
        insertItem(TITLE + "-地点D", "PLACE", "8.6", "海岛,骑行", "2024-03-12");
        insertItem(TITLE + "-地点E", "PLACE", null, null, "2024-11-20");
        insertItem(TITLE + "-影视F2023", "MOVIE", "9.0", null, "2023-12-31");

        // 地点 D：福建有坐标；地点 E：云南无坐标（不上图的分支）
        Long idD = queryItemId(TITLE + "-地点D");
        Long idE = queryItemId(TITLE + "-地点E");
        jdbcTemplate.update("insert into sgj_item_place (item_id, province, country, latitude, longitude) "
                + "values (?, '福建', null, 25.50, 119.79)", idD);
        jdbcTemplate.update("insert into sgj_item_place (item_id, province, country, latitude, longitude) "
                + "values (?, '云南', null, null, null)", idE);

        // 笔记两条：2024 一条、2023 一条（年份并集与归年分支）
        jdbcTemplate.update("insert into sgj_note (title, content, is_public, del_flag, create_by, create_time) "
                + "values ('" + TITLE + "-笔记1', 'c', '1', '0', '" + PUBLIC_OWNER + "', '2024-08-15 10:00:00')");
        jdbcTemplate.update("insert into sgj_note (title, content, is_public, del_flag, create_by, create_time) "
                + "values ('" + TITLE + "-笔记2', 'c', '1', '0', '" + PUBLIC_OWNER + "', '2023-01-05 10:00:00')");
    }

    private void insertItem(String title, String type, String rating, String tags, String finishDate)
    {
        jdbcTemplate.update("insert into sgj_item (title, item_type, status, rating, tags, finish_date, "
                + "del_flag, create_by, create_time) values (?, ?, 'DONE', ?, ?, ?, '0', ?, now())",
                title, type, rating, tags, finishDate, PUBLIC_OWNER);
    }

    private Long queryItemId(String title)
    {
        return jdbcTemplate.queryForObject(
                "select item_id from sgj_item where title = ?", Long.class, title);
    }

    // ------------------------------------------------------------------
    // A2 / A3：认证与参数校验
    // ------------------------------------------------------------------

    @Test
    void anonymousGets401AndInvalidYearRejected() throws Exception
    {
        // 匿名（无 token）：私人内容，未标 @Anonymous——RuoYi 契约为 HTTP 200 + 业务码 401
        mockMvc.perform(get("/app/review/year").param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));

        // 登录态 + 非法 year：非四位 / 越界
        mockMvc.perform(get("/app/review/year").param("year", "abc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.code").value(400));
        mockMvc.perform(get("/app/review/year").param("year", "1799")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ------------------------------------------------------------------
    // A1 / A4 / A5 / A6 / A7 / A8 / A9 / A10：口径与字段
    // ------------------------------------------------------------------

    @Test
    void yearDataAggregationAndFields() throws Exception
    {
        JsonNode data = reviewYear(2024);

        // 年份列表：条目 ∪ 笔记，降序，含夹具年份
        JsonNode years = data.path("years");
        assertThat(years.size()).isGreaterThanOrEqualTo(2);
        for (int i = 1; i < years.size(); i++)
        {
            assertThat(years.get(i - 1).asInt()).isGreaterThanOrEqualTo(years.get(i).asInt());
        }
        assertThat(containsYear(years, 2024)).isTrue();
        assertThat(containsYear(years, 2023)).isTrue();

        // 汇总：与库内直查对账（admin 是公开博主 → resolveCreateBy 不限制，对账不带 create_by）
        // movie 合并 MOVIE+TV（页面统计卡口径「看过 · 电影/剧集」）
        JsonNode summary = data.path("summary");
        assertThat(summary.path("total").asLong()).isEqualTo(dbCount(DONE_ITEM + "2024"));
        assertThat(summary.path("movie").asLong()).isEqualTo(dbCount(DONE_ITEM + "2024 and item_type in ('MOVIE','TV')"));
        assertThat(summary.path("book").asLong()).isEqualTo(dbCount(DONE_ITEM + "2024 and item_type = 'BOOK'"));
        assertThat(summary.path("place").asLong()).isEqualTo(dbCount(DONE_ITEM + "2024 and item_type = 'PLACE'"));
        assertThat(summary.path("total").asLong())
                .isEqualTo(summary.path("movie").asLong() + summary.path("book").asLong()
                        + summary.path("place").asLong());
        assertThat(summary.path("note").asLong())
                .isEqualTo(dbCount("select count(*) from sgj_note where del_flag='0' and year(create_time)=2024"));

        // 地点：总数对账；夹具 D（03-12）在 E（11-20）之前；字段与「无坐标仍在列表」分支
        JsonNode places = data.path("places");
        assertThat(places.size()).isEqualTo((int) dbCount(DONE_ITEM + "2024 and item_type = 'PLACE'"));
        int idxD = indexOfTitle(places, TITLE + "-地点D");
        int idxE = indexOfTitle(places, TITLE + "-地点E");
        assertThat(idxD).isGreaterThanOrEqualTo(0);
        assertThat(idxE).as("地点按 finish_date 升序").isGreaterThan(idxD);
        assertThat(places.get(idxD).path("province").asText()).isEqualTo("福建");
        assertThat(places.get(idxD).path("latitude").asDouble()).isEqualTo(25.50);
        assertThat(places.get(idxD).path("finishDate").asText()).isEqualTo("2024-03-12");
        assertThat(places.get(idxE).path("latitude").isMissingNode()
                || places.get(idxE).path("latitude").isNull()).as("无坐标地点仍在列表里").isTrue();

        // 评分榜：降序、≤5；夹具 A 满分必在影视榜；ratedCount 与库内对账
        JsonNode movieTop = data.path("topRated").path("movie");
        assertThat(movieTop.size()).isLessThanOrEqualTo(5);
        for (int i = 1; i < movieTop.size(); i++)
        {
            assertThat(movieTop.get(i).path("rating").asDouble())
                    .isLessThanOrEqualTo(movieTop.get(i - 1).path("rating").asDouble());
        }
        assertThat(indexOfTitle(movieTop, TITLE + "-影视A")).isGreaterThanOrEqualTo(0);
        assertThat(data.path("topRated").path("ratedCount").path("movie").asLong())
                .isEqualTo(dbCount(RATED_ITEM + "2024 and item_type in ('MOVIE','TV')"));
        assertThat(data.path("topRated").path("ratedCount").path("book").asLong())
                .isEqualTo(dbCount(RATED_ITEM + "2024 and item_type = 'BOOK'"));

        // 标签：夹具贡献人文/剧情各 ≥2、海岛/骑行 ≥1；计数降序
        JsonNode tags = data.path("tags");
        assertThat(tagCount(tags, "人文")).isGreaterThanOrEqualTo(2L);
        assertThat(tagCount(tags, "剧情")).isGreaterThanOrEqualTo(2L);
        assertThat(tagCount(tags, "海岛")).isGreaterThanOrEqualTo(1L);
        assertThat(tagCount(tags, "骑行")).isGreaterThanOrEqualTo(1L);
        for (int i = 1; i < tags.size(); i++)
        {
            assertThat(tags.get(i).path("count").asLong())
                    .isLessThanOrEqualTo(tags.get(i - 1).path("count").asLong());
        }
    }

    // ------------------------------------------------------------------
    // A5 / A11：跨年归因与空年份
    // ------------------------------------------------------------------

    @Test
    void crossYearAttributionAndEmptyYear() throws Exception
    {
        // 夹具 F 创建于今年（create_time=now()）但 finish_date=2023-12-31 → 归因 2023（对账）
        JsonNode y2023 = reviewYear(2023);
        assertThat(y2023.path("summary").path("movie").asLong())
                .isEqualTo(dbCount(DONE_ITEM + "2023 and item_type in ('MOVIE','TV')"));
        assertThat(y2023.path("summary").path("movie").asLong()).isGreaterThanOrEqualTo(1L);
        assertThat(y2023.path("summary").path("note").asLong())
                .isEqualTo(dbCount("select count(*) from sgj_note where del_flag='0' and year(create_time)=2023"));

        // 空年份：200 + 全 0 空数组（不 404）
        JsonNode empty = reviewYear(1999);
        assertThat(empty.path("summary").path("total").asLong()).isZero();
        assertThat(empty.path("places").size()).isZero();
        assertThat(empty.path("tags").size()).isZero();
        assertThat(empty.path("topRated").path("movie").size()).isZero();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** DONE 条目按年（及可选附加条件）计数的 SQL 前缀 */
    private static final String DONE_ITEM = "select count(*) from sgj_item "
            + "where del_flag='0' and status='DONE' and finish_date is not null and year(finish_date)=";

    /** 已评分 DONE 条目按年计数的 SQL 前缀 */
    private static final String RATED_ITEM = "select count(*) from sgj_item "
            + "where del_flag='0' and status='DONE' and finish_date is not null and rating is not null and year(finish_date)=";

    private long dbCount(String sql)
    {
        Long v = jdbcTemplate.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }

    /** 在 JSON 数组里按 title 字段找下标（找不到 -1） */
    private static int indexOfTitle(JsonNode arr, String title)
    {
        for (int i = 0; i < arr.size(); i++)
        {
            if (title.equals(arr.get(i).path("title").asText()))
            {
                return i;
            }
        }
        return -1;
    }

    /** 取某标签的计数（无则 0） */
    private static long tagCount(JsonNode tags, String name)
    {
        for (JsonNode t : tags)
        {
            if (name.equals(t.path("name").asText()))
            {
                return t.path("count").asLong();
            }
        }
        return 0L;
    }

    private JsonNode reviewYear(int year) throws Exception
    {
        return objectMapper.readTree(mockMvc.perform(get("/app/review/year")
                        .param("year", String.valueOf(year))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private static boolean containsYear(JsonNode years, int y)
    {
        for (JsonNode n : years)
        {
            if (n.asInt() == y)
            {
                return true;
            }
        }
        return false;
    }

    private static String tagsToString(JsonNode tags)
    {
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : tags)
        {
            sb.append(t.path("name").asText()).append(',');
        }
        return sb.toString();
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
