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
 *   <li>boxDraftLimitIsTwenty                  —— 第 21 份新笔记草稿被拒并提示先清理；编辑态草稿不计入</li>
 *   <li>editDraftUpsertByNoteIdAndAliveCheck   —— 不带 draftId 的编辑态写入按「本人 + 笔记」收敛为一行；
 *       笔记软删 / 彻底删除后再写入被拒（存活校验）</li>
 *   <li>publishConsumesDraft                   —— 保存笔记（新增 / 编辑）同事务删掉草稿；不带 draftId 行为不变；
 *       带他人 draftId 整个请求失败且不写入任何数据</li>
 *   <li>noteAndItemDeletionCleanDrafts         —— 笔记软删、笔记彻底删除、条目彻底删除三条路径都清掉草稿行</li>
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
    // 6. 新笔记草稿上限 20 份
    // ------------------------------------------------------------------

    @Test
    void boxDraftLimitIsTwenty() throws Exception
    {
        deleteMarkedDrafts();
        for (int i = 1; i <= 20; i++)
        {
            saveDraft(adminToken, draftBody(null, null, MARK + "-limit " + i, "qat35 limit " + i));
        }
        // 第 21 份被拒并提示先清理
        mockMvc.perform(put("/app/note/draft").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftBody(null, null, MARK + "-limit 21", "qat35 limit 21"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value(containsString("上限")));
        assertThat(countBoxDrafts(PUBLIC_OWNER)).isEqualTo(20);

        // 编辑态草稿不受上限约束：有 noteId 时即使草稿箱满了也能写
        long editDraftId = saveDraft(adminToken, draftBody(null, adminNoteId(), MARK + "-limit 编辑态", "qat35 limit 编辑态"));
        assertThat(editDraftId).isPositive();
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
        // 编辑态草稿不进草稿箱
        assertThat(findById(boxList(adminToken), first)).isNull();

        // 笔记软删后：草稿行被清理，继续写入被存活校验拒绝
        mockMvc.perform(delete("/app/note/" + noteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(countDraftsOfNote(noteId)).as("软删笔记应一并清掉它的草稿").isZero();
        rejectDraftWriteForDeadNote(noteId);

        // 彻底删除后同样被拒
        mockMvc.perform(post("/app/note/recycle/purge/" + noteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        rejectDraftWriteForDeadNote(noteId);
    }

    // ------------------------------------------------------------------
    // 8. 发布语义：保存成功同事务删草稿；他人的 draftId 整个请求失败
    // ------------------------------------------------------------------

    @Test
    void publishConsumesDraft() throws Exception
    {
        // 新增：带自己的 draftId → 草稿随保存消失
        long draftId = saveDraft(adminToken, draftBody(null, null, MARK + "-publish 新笔记", "qat35 publish 正文"));
        JsonNode added = publish(adminToken, noteBody(null, MARK + "-publish 新笔记", draftId));
        assertThat(added.path("code").asInt()).isEqualTo(200);
        assertThat(draftRowExists(draftId)).as("保存成功后草稿应消失").isFalse();

        // 回归：不带 draftId 的保存行为完全一样
        JsonNode plain = publish(adminToken, noteBody(null, MARK + "-publish 无草稿", null));
        assertThat(plain.path("code").asInt()).isEqualTo(200);

        // 编辑：带自己的 draftId → 同样消失
        long noteId = findNoteId(MARK + "-publish 无草稿");
        long editDraftId = saveDraft(adminToken, draftBody(null, noteId, MARK + "-publish 编辑态", "qat35 publish 编辑正文"));
        JsonNode edited = publish(adminToken, noteBody(noteId, MARK + "-publish 已改", editDraftId));
        assertThat(edited.path("code").asInt()).isEqualTo(200);
        assertThat(draftRowExists(editDraftId)).isFalse();

        // 带他人的 draftId → 整个请求失败，且没有写入任何数据
        long otherDraftId = saveDraft(otherToken, draftBody(null, null, MARK + "-publish 他人草稿", "qat35 他人的正文"));
        int before = countNotesByTitle(MARK + "-publish 越权");
        JsonNode rejected = publish(adminToken, noteBody(null, MARK + "-publish 越权", otherDraftId));
        assertThat(rejected.path("code").asInt()).isEqualTo(500);
        assertThat(countNotesByTitle(MARK + "-publish 越权")).as("被拒的保存不得写入任何数据").isEqualTo(before);
        assertThat(draftRowExists(otherDraftId)).as("他人的草稿不得被删掉").isTrue();

        jdbcTemplate.update("delete from sgj_note_draft where draft_id = ?", otherDraftId);
    }

    // ------------------------------------------------------------------
    // 9. 三条清理路径：笔记软删 / 笔记彻底删除 / 条目彻底删除
    // ------------------------------------------------------------------

    @Test
    void noteAndItemDeletionCleanDrafts() throws Exception
    {
        // 条目彻底删除：先删草稿再删条目（反序会因级联删 sgj_note 而反查不到 note_id）
        long itemId = createItem(MARK + "-purge 条目");
        long noteId = createNoteWithItem(MARK + "-purge 条目笔记", itemId);
        long draftId = saveDraft(adminToken, draftBody(null, noteId, MARK + "-purge 草稿", "qat35 purge 正文"));
        assertThat(draftRowExists(draftId)).isTrue();
        mockMvc.perform(delete("/app/item/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/app/item/recycle/purge/" + itemId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertThat(draftRowExists(draftId)).as("条目彻底删除后草稿行不应残留").isFalse();
        assertThat(jdbcTemplate.queryForObject("select count(*) from sgj_note where note_id = ?", Integer.class, noteId))
                .as("条目删除经外键级联清掉笔记").isZero();

        // 笔记彻底删除（独立笔记，软删后清掉草稿）
        long boxNoteId = createNote(MARK + "-purge 独立笔记");
        long boxDraftId = saveDraft(adminToken, draftBody(null, boxNoteId, MARK + "-purge 独立草稿", "qat35 purge 独立正文"));
        mockMvc.perform(delete("/app/note/" + boxNoteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(draftRowExists(boxDraftId)).isFalse();
        mockMvc.perform(post("/app/note/recycle/purge/" + boxNoteId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(draftRowExists(boxDraftId)).isFalse();
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

    /** 往已删除的笔记写草稿 → 存活校验拒绝 */
    private void rejectDraftWriteForDeadNote(long noteId) throws Exception
    {
        mockMvc.perform(put("/app/note/draft")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftBody(null, noteId, MARK + " 已删笔记", "qat35 已删正文"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
        assertThat(countDraftsOfNote(noteId)).as("已删笔记不得再写入草稿").isZero();
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

    private JsonNode publish(String token, Map<String, Object> body) throws Exception
    {
        MockHttpServletRequestBuilder builder = body.containsKey("noteId")
                ? put("/app/note") : post("/app/note");
        MvcResult result = mockMvc.perform(builder
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return bodyOf(result);
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

    private long createNoteWithItem(String title, long itemId) throws Exception
    {
        Map<String, Object> body = noteBody(null, title, null);
        body.put("itemId", itemId);
        mockMvc.perform(post("/app/note").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return findNoteId(title);
    }

    private long createItem(String title) throws Exception
    {
        mockMvc.perform(post("/app/item").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", title, "itemType", "MOVIE", "tags", MARK))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        MvcResult result = mockMvc.perform(get("/app/item/list").param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return bodyOf(result).path("data").get(0).path("itemId").asLong();
    }

    private long findNoteId(String title) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/app/note/list").param("title", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return bodyOf(result).path("data").get(0).path("noteId").asLong();
    }

    /** 夹具笔记（草稿上限用例里作为「编辑态草稿不受限」的载体），用完即删 */
    private long adminNoteId() throws Exception
    {
        return createNote(MARK + "-limit 载体笔记");
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

    private int countBoxDrafts(String createBy)
    {
        return jdbcTemplate.queryForObject(
                "select count(*) from sgj_note_draft where create_by = ? and note_id is null", Integer.class, createBy);
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

    private int countNotesByTitle(String title)
    {
        return jdbcTemplate.queryForObject("select count(*) from sgj_note where title = ?", Integer.class, title);
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
