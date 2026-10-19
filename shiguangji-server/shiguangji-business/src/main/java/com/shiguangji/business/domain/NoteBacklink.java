package com.shiguangji.business.domain;

import java.util.Date;

/**
 * 反向链接来源（0.0.5 FR-005-2，前台详情接口出参，非表字段）。
 *
 * <p>由来源笔记正文实时派生：snippet 是含 [[本篇标题]] 的正文片段（纯文本、折叠空白），
 * hitCount 为该来源中有效引用的处数（代码块里的不算）。</p>
 *
 * @author shiguangji
 */
public class NoteBacklink {

    private final Long noteId;

    private final String title;

    private final Date updateTime;

    private final String snippet;

    private final Long hitCount;

    public NoteBacklink(Long noteId, String title, Date updateTime, String snippet, Long hitCount) {
        this.noteId = noteId;
        this.title = title;
        this.updateTime = updateTime;
        this.snippet = snippet;
        this.hitCount = hitCount;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public String getSnippet() {
        return snippet;
    }

    public Long getHitCount() {
        return hitCount;
    }
}
