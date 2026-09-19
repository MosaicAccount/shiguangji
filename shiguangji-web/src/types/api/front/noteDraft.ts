/** 笔记草稿（服务端草稿箱，issue #35 / #37） */
export interface SgjNoteDraft {
  draftId?: number
  /** 编辑来源笔记ID；空=新笔记草稿（只有这类进草稿箱） */
  noteId?: number
  itemId?: number
  /** 关联条目名称（后端联表带出；条目已删时为空，前端回退 #id） */
  itemName?: string
  title?: string
  /** 正文（草稿箱列表接口不下发，按 id / noteId 取单条时才有） */
  content?: string
  tags?: string
  isPublic?: string
  /** 草稿箱展示用纯文本摘要（标题为空时展示它；后端复用笔记列表那套剥离逻辑） */
  excerpt?: string
  createTime?: string
  updateTime?: string
}

/** 保存草稿的入参 */
export interface NoteDraftPayload {
  draftId?: number
  noteId?: number
  itemId?: number
  title?: string
  content?: string
  tags?: string
  isPublic?: string
}

/** 保存草稿的返回：draftId 与数据库里的 updateTime（供客户端作 baseUpdateTime） */
export interface NoteDraftSaved {
  draftId: number
  updateTime: string
}
