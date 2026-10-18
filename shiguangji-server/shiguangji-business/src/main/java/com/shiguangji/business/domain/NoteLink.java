package com.shiguangji.business.domain;

/**
 * 笔记互链解析结果（0.0.5 FR-005-1/3，前台详情接口出参，非表字段）。
 *
 * <p>raw 是标记在正文里的原文片段（含括号内空白），前端据此做字面量替换；
 * title 是去空白后的目标标题；noteId 为空时 reason 说明降级原因。</p>
 *
 * @author shiguangji
 */
public class NoteLink {

    /** 降级原因：可见范围内不存在目标（对匿名，私密与不存在同为此值，不泄露存在性） */
    public static final String REASON_NOT_FOUND = "NOT_FOUND";

    /** 降级原因：可见范围内同名笔记 ≥2 篇，无法确定目标（D-14：不猜） */
    public static final String REASON_AMBIGUOUS = "AMBIGUOUS";

    private final String raw;

    private final String title;

    private final Long noteId;

    private final String reason;

    private NoteLink(String raw, String title, Long noteId, String reason) {
        this.raw = raw;
        this.title = title;
        this.noteId = noteId;
        this.reason = reason;
    }

    public static NoteLink resolved(String raw, String title, Long noteId) {
        return new NoteLink(raw, title, noteId, null);
    }

    public static NoteLink broken(String raw, String title, String reason) {
        return new NoteLink(raw, title, null, reason);
    }

    public String getRaw() {
        return raw;
    }

    public String getTitle() {
        return title;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getReason() {
        return reason;
    }
}
