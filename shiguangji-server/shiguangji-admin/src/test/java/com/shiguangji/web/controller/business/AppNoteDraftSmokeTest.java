package com.shiguangji.web.controller.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
 * 笔记草稿箱（issue #35 / #37 / #38）接口冒烟测试。
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>anonymousDraftEndpointsRejected        —— 匿名访问四个草稿端点均为业务码 401（不是 200 空列表）</li>
 *   <li>createUpdateDeleteBoxDraft             —— 新建返回 draftId + 数据库里的 updateTime；
 *       草稿箱列表只回 excerpt 不回 content；按 id 取单条含正文；更新不新建行；删除即时消失</li>
 *   <li>boxExcerptFallsBackToContent           —— 无标题草稿用正文首行兜底（摘要复用笔记列表那套剥离逻辑）</li>
 *   <li>draftsAreVisibleToOwnerOnly            —— 他人（含管理员）既看不到也改不了 / 删不了别人的草稿</li>
 *   <li>homeStatsIgnoreDrafts                  —— 首页笔记总数与最近笔记不受草稿影响（独立表的直接收益）</li>
 *   <li>oneRowPerWritingTarget                 —— 空白草稿每人一份（换条目也只是更新同一行，库里存 0）；编辑态每篇笔记一份</li>
 *   <li>editDraftUpsertByNoteIdAndAliveCheck   —— 不带 draftId 的编辑态写入按「本人 + 笔记」收敛为一行；
 *       笔记软删 / 彻底删除后再写入被拒（存活校验）</li>
 * </ol>
 *
 * <p><b>响应契约</b>：与既有冒烟测试一致——未登录 = HTTP 200 + 业务码 401；越权 / 业务拒绝 = 业务码 500。</p>
 *
 * <p><b>数据策略</b>：直连开发库。草稿表没有 tags 列，故夹具统一在 title / content 里带 {@code qat35} 标记，
 * @BeforeAll / @AfterAll 按标记清理，不触碰真实草稿。</p>
 */
@SpringBootTest(classes = ShiGuangJiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppNoteDraftSmokeTest
{
    /** 夹具标记：草稿表无 tags 列，用 title/content 里的统一标记做隔离与清理 */
    private static final String MARK = "qat35";

    /** 写作对象夹具：两个不存在的条目ID就够了——item_id 上无外键，分行用例不需要真实条目 */
    private static final long ITEM_A = 9999001L;
    private static final long ITEM_B = 9999002L;

    /** 公开博主账号（application.yml shiguangji.public-owner），同时是管理员 */
    private static final String PUBLIC_OWNER = "admin";

    /** 非管理员普通用户（sys_user 中已存在） */
    private static final String OTHER_USER = "shiguangji";

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
    private String otherToken;

    @BeforeAll
    void setUp()
    {
        cleanupFixtureRows();
        adminToken = createTokenFor(PUBLIC_OWNER);
        otherToken = createTokenFor(OTHER_USER);
    }

    @AfterAll
    void tearDown()
    {
        cleanupFixtureRows();
    }

    /** 硬删除本测试类的全部夹具（草稿按标记，笔记按 tags），恢复开发库原状 */
    private void cleanupFixtureRows()
    {
        deleteMarkedDrafts();
        jdbcTemplate.update("delete from sgj_note where tags like '" + MARK + "%'");
        jdbcTemplate.update("delete from sgj_item where tags like '" + MARK + "%'");
    }

    private void deleteMarkedDrafts()
    {
        jdbcTemplate.update("delete from sgj_note_draft where title like '" + MARK + "%' or content like '%" + MARK + "%'");
    }

    // ------------------------------------------------------------------
    // 1. 匿名访问：业务码 401
    // ------------------------------------------------------------------

    @Test
    void anonymousDraftEndpointsRejected() throws Exception
    {
        mockMvc.perform(get("/app/note/draft/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/app/note/draft/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(put("/app/note/draft").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"qat35 匿名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(delete("/app/note/draft/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // ------------------------------------------------------------------
    // 2. 草稿箱的增删改查
    // ------------------------------------------------------------------

    @Test
    void createUpdateDeleteBoxDraft() throws Exception
    {
        long draftId = saveDraft(adminToken, draftBody(null, null, MARK + " 草稿标题", MARK + " 正文第一行\n\nqat35 更多内容"));

        // 返回的 updateTime 必须与库里那一行一致（不能用 JVM 时钟）
        String dbTime = jdbcTemplate.queryForObject(
                "select date_format(update_time, '%Y-%m-%d %H:%i:%s') from sgj_note_draft where draft_id = ?",
                String.class, draftId);
        assertThat(latestDraftUpdateTime(adminToken, draftId)).isEqualTo(dbTime);

        // 草稿箱列表：含该行、有 excerpt、不下发 content
        JsonNode box = boxList(adminToken);
        JsonNode row = findById(box, draftId);
        assertThat(row).as("草稿箱应包含新建的草稿").isNotNull();
        assertThat(row.path("excerpt").asText()).contains(MARK + " 正文第一行");
        assertThat(row.hasNonNull("content")).as("草稿箱列表不应下发正文").isFalse();

        // 按 id 取单条：含完整正文（草稿箱「继续写」用）
        mockMvc.perform(get("/app/note/draft/" + draftId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.draftId").value((int) draftId))
                .andExpect(jsonPath("$.data.content").value(MARK + " 正文第一行\n\nqat35 更多内容"));

        // 带 draftId 更新：不新建行
        long updatedId = saveDraft(adminToken, draftBody(draftId, null, MARK + " 改过的标题", "改过的正文"));
        assertThat(updatedId).isEqualTo(draftId);
        assertThat(countDraftsById(draftId)).as("更新不得产生第二行").isEqualTo(1);
        assertThat(findById(boxList(adminToken), draftId).path("title").asText()).isEqualTo(MARK + " 改过的标题");

        // 删除：硬删除，列表即时消失
        mockMvc.perform(delete("/app/note/draft/" + draftId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(findById(boxList(adminToken), draftId)).as("删掉的草稿不应再出现在草稿箱").isNull();
        assertThat(draftRowExists(draftId)).isFalse();
    }

    // ------------------------------------------------------------------
    // 3. 无标题草稿用正文首行兜底
    // ------------------------------------------------------------------

    @Test
    void boxExcerptFallsBackToContent() throws Exception
    {
        long draftId = saveDraft(adminToken, draftBody(null, null, null, MARK + " 无标题草稿的正文第一行，后面还有很多字"));
        JsonNode row = findById(boxList(adminToken), draftId);
        assertThat(row).isNotNull();
        assertThat(row.path("excerpt").asText()).startsWith(MARK + " 无标题草稿的正文第一行");
        jdbcTemplate.update("delete from sgj_note_draft where draft_id = ?", draftId);
    }

    // ------------------------------------------------------------------
    // 4. 草稿只本人可见（管理员也没有旁路）
    // ------------------------------------------------------------------

    @Test
    void draftsAreVisibleToOwnerOnly() throws Exception
    {
        long adminDraftId = saveDraft(adminToken, draftBody(null, null, MARK + " admin私密草稿", "qat35 admin 的内容"));

        // 普通用户看不到、也改不了 / 删不了
        assertThat(findById(boxList(otherToken), adminDraftId)).as("他人草稿不出现在自己的草稿箱").isNull();
        mockMvc.perform(get("/app/note/draft/" + adminDraftId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        mockMvc.perform(put("/app/note/draft").header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftBody(adminDraftId, null, MARK + " 越权改", "越权"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        mockMvc.perform(delete("/app/note/draft/" + adminDraftId).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        assertThat(jdbcTemplate.queryForObject("select title from sgj_note_draft where draft_id = ?", String.class, adminDraftId))
                .as("越权更新不得生效").isEqualTo(MARK + " admin私密草稿");

        // 管理员读他人草稿同样被拒（草稿接口不沿用 AppScopeHelper 的管理员旁路）
        long otherDraftId = saveDraft(otherToken, draftBody(null, null, MARK + " other的草稿", "qat35 other 的内容"));
        assertThat(findById(boxList(adminToken), otherDraftId)).as("管理员不应看到他人草稿").isNull();
        mockMvc.perform(get("/app/note/draft/" + otherDraftId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        // createBy 是客户端可绑定字段：伪造 createBy 也读不到他人草稿
        mockMvc.perform(get("/app/note/draft/list").param("createBy", OTHER_USER)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(findById(boxList(adminToken), otherDraftId)).isNull();

        jdbcTemplate.update("delete from sgj_note_draft where draft_id in (?, ?)", adminDraftId, otherDraftId);
    }

    // ------------------------------------------------------------------
    // 5. 首页统计不受草稿影响（独立表的直接收益）
    // ------------------------------------------------------------------

    @Test
    void homeStatsIgnoreDrafts() throws Exception
    {
        JsonNode before = home();
        long totalBefore = before.path("data").path("summary").path("noteTotal").asLong();
        int recentBefore = before.path("data").path("recentNotes").size();

        long draftId = saveDraft(adminToken, draftBody(null, null, MARK + "-home 草稿", "qat35 home 内容"));
        JsonNode after = home();
        assertThat(after.path("data").path("summary").path("noteTotal").asLong())
                .as("草稿不得计入首页笔记总数").isEqualTo(totalBefore);
        assertThat(after.path("data").path("recentNotes").size())
                .as("草稿不得出现在首页最近笔记").isEqualTo(recentBefore);

        jdbcTemplate.update("delete from sgj_note_draft where draft_id = ?", draftId);
    }

    // ------------------------------------------------------------------
    // 6. 每个写作对象最多一行（draft_scope 唯一键）
    // ------------------------------------------------------------------

    @Test
    void oneRowPerWritingTarget() throws Exception
    {
        deleteMarkedDrafts();

        // 空白草稿每人一份：连推两次（第二次还换了关联条目）仍只有一行——item_id 只是数据，不参与身份
        long blank = saveDraft(adminToken, draftBodyWithItem(ITEM_A, MARK + "-blank 第一篇", "qat35 blank 1"));
        long blankAgain = saveDraft(adminToken, draftBodyWithItem(ITEM_B, MARK + "-blank 第二篇", "qat35 blank 2"));
        assertThat(blankAgain).as("空白草稿只有一份：第二次首推应更新同一行").isEqualTo(blank);
        assertThat(countMarkedDrafts()).as("空白草稿只有一行").isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select note_id from sgj_note_draft where draft_id = ?", Long.class, blank))
                .as("空白草稿在库里存哨兵 0（对外仍是 null）").isEqualTo(0L);

        // 进「写笔记」时按身份把那唯一一份空白草稿取回来（否则写第二篇会静默覆盖上一篇）
        mockMvc.perform(get("/app/note/draft/blank").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.draftId").value((int) blankAgain))
                .andExpect(jsonPath("$.data.content").value("qat35 blank 2"));

        // 编辑态：同一篇笔记一份，不同笔记各一份
        long noteA = createNote(MARK + "-blank 笔记A");
        long noteB = createNote(MARK + "-blank 笔记B");
        long editA = saveDraft(adminToken, draftBody(null, noteA, MARK + "-blank 编辑A", "qat35 编辑 A"));
        long editAAgain = saveDraft(adminToken, draftBody(null, noteA, MARK + "-blank 编辑A 再推", "qat35 编辑 A2"));
        long editB = saveDraft(adminToken, draftBody(null, noteB, MARK + "-blank 编辑B", "qat35 编辑 B"));
        assertThat(editAAgain).as("同一篇笔记第二次首推应更新同一行").isEqualTo(editA);
        assertThat(editB).as("不同笔记各一份").isNotEqualTo(editA);
        assertThat(countMarkedDrafts()).as("空白 1 行 + 两篇笔记各一行").isEqualTo(3);

        deleteMarkedDrafts();
    }

    // ------------------------------------------------------------------
    // 7. 编辑态草稿：按「本人 + 笔记」收敛 + 笔记存活校验
    // ------------------------------------------------------------------

    @Test
    void editDraftUpsertByNoteIdAndAliveCheck() throws Exception
    {
        long noteId = createNote(MARK + " 存活校验笔记");

        // 不带 draftId 连续两次：同一篇只保留一行（两台设备都离线时的收敛）
        long first = saveDraft(adminToken, draftBody(null, noteId, MARK + " 编辑态一", "第一版"));
        long second = saveDraft(adminToken, draftBody(null, noteId, MARK + " 编辑态二", "第二版"));
        assertThat(second).isEqualTo(first);
        assertThat(countDraftsOfNote(noteId)).isEqualTo(1);

        // 编辑页按 noteId 取回未保存改动（含正文）
        mockMvc.perform(get("/app/note/draft/list").param("noteId", String.valueOf(noteId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.draftId").value((int) first))
                .andExpect(jsonPath("$.data.content").value("第二版"));
        // 编辑态草稿也在草稿箱里（每个写作对象一行），并带上 noteId 供「继续写」跳回那篇
        JsonNode boxRow = findById(boxList(adminToken), first);
        assertThat(boxRow).as("编辑态草稿也要进草稿箱").isNotNull();
        assertThat(boxRow.path("noteId").asLong()).as("带上笔记ID，才能跳回那篇").isEqualTo(noteId);

        // 笔记软删后：草稿行被清理，继续写入被存活校验拒绝
        mockMvc.perform(delete("/app/note/" + noteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        rejectDraftWriteForDeadNote(noteId);

        // 彻底删除后同样被拒
        mockMvc.perform(post("/app/note/recycle/purge/" + noteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        rejectDraftWriteForDeadNote(noteId);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private Map<String, Object> draftBody(Long draftId, Long noteId, String title, String content)
    {
        Map<String, Object> body = new HashMap<>();
        if (draftId != null)
        {
            body.put("draftId", draftId);
        }
        if (noteId != null)
        {
            body.put("noteId", noteId);
        }
        body.put("title", title);
        body.put("content", content);
        body.put("tags", MARK);
        body.put("isPublic", "0");
        return body;
    }

    /** 带关联条目的新建态草稿（draft_scope 按条目区分，不带 draftId 首次写入） */
    private Map<String, Object> draftBodyWithItem(long itemId, String title, String content)
    {
        Map<String, Object> body = draftBody(null, null, title, content);
        body.put("itemId", itemId);
        return body;
    }

    private int countMarkedDrafts()
    {
        return jdbcTemplate.queryForObject(
                "select count(*) from sgj_note_draft where title like ? or content like ?",
                Integer.class, MARK + "%", "%" + MARK + "%");
    }

    /** PUT 一份草稿，断言成功并返回 draftId */
    private long saveDraft(String token, Map<String, Object> body) throws Exception
    {
        MvcResult result = mockMvc.perform(put("/app/note/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data").path("draftId").asLong();
    }

    /** 往已删除的笔记写草稿 → 存活校验拒绝（草稿行是否同时被清掉属 #38 的发布语义，见 next 分支） */
    private void rejectDraftWriteForDeadNote(long noteId) throws Exception
    {
        mockMvc.perform(put("/app/note/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftBody(null, noteId, MARK + " 已删笔记", "qat35 已删正文"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    private JsonNode boxList(String token) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/note/draft/list").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data");
    }

    /** 首页公开统计（含 noteTotal 与最近笔记） */
    private JsonNode home() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/home/index").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result);
    }

    /** 取单条草稿的 updateTime 文本（经接口） */
    private String latestDraftUpdateTime(String token, long draftId) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/note/draft/" + draftId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return bodyOf(result).path("data").path("updateTime").asText();
    }

    private Map<String, Object> noteBody(Long noteId, String title, Long draftId)
    {
        Map<String, Object> body = new HashMap<>();
        if (noteId != null)
        {
            body.put("noteId", noteId);
        }
        body.put("title", title);
        body.put("content", "qat35 发布正文");
        body.put("tags", MARK);
        body.put("isPublic", "0");
        if (draftId != null)
        {
            body.put("draftId", draftId);
        }
        return body;
    }


    private long createNote(String title) throws Exception
    {
        mockMvc.perform(post("/app/note").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteBody(null, title, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return findNoteId(title);
    }



    private long findNoteId(String title) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/note/list").param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return bodyOf(result).path("data").get(0).path("noteId").asLong();
    }

    private static JsonNode findById(JsonNode array, long draftId)
    {
        for (JsonNode row : array)
        {
            if (row.path("draftId").asLong() == draftId)
            {
                return row;
            }
        }
        return null;
    }

    private boolean draftRowExists(long draftId)
    {
        return jdbcTemplate.queryForObject(
                "select count(*) from sgj_note_draft where draft_id = ?", Integer.class, draftId) > 0;
    }

    private int countDraftsById(long draftId)
    {
        return jdbcTemplate.queryForObject(
                "select count(*) from sgj_note_draft where draft_id = ?", Integer.class, draftId);
    }

    private int countDraftsOfNote(long noteId)
    {
        return jdbcTemplate.queryForObject(
                "select count(*) from sgj_note_draft where note_id = ?", Integer.class, noteId);
    }


    private String createTokenFor(String username)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (qa-draft-test) Chrome/120.0");
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

    private JsonNode bodyOf(MvcResult result) throws Exception
    {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
