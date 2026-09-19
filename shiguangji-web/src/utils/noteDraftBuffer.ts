/**
 * 笔记编辑页的本地草稿缓冲（issue #32）
 *
 * 打字时先写这里（同步、瞬时成功，断网 / 页面被强杀都不丢），服务端那份才是跨设备的那一份。
 * 缓冲按「登录用户 + 编辑器身份」分 key：不区分用户的话，同一台电脑换个人登录就会看到上一个人没保存的内容。
 *
 * 冲突规则只用服务端时间（设计结论 10）：`baseUpdateTime` = 最近一次推送成功时服务端返回的 updateTime；
 * `dirty` = 之后又改过。**不存客户端 savedAt**——本地钟与服务端钟比不出「谁的内容新」，
 * 只能比出「谁的钟快」。`seq` 是内存里的本地递增计数器（不是时钟），只用来判断推送响应回来时内容有没有再变过。
 */

/** 本地缓冲的存储结构 */
export interface NoteDraftBuffer {
  /** 服务端草稿ID（首次同步成功后回填，key 不需要迁移） */
  draftId?: number
  /** 编辑来源笔记ID；新笔记草稿为空 */
  noteId?: number
  itemId?: number
  title?: string
  content?: string
  tags?: string
  isPublic?: string
  /** 最近一次推送成功时服务端返回的 updateTime（epoch 毫秒，经 parseServerTime 解析） */
  baseUpdateTime: number
  /** baseUpdateTime 之后又改过：有服务端从没见过的内容 */
  dirty: boolean
}

/** 缓冲 key 前缀（登出时按前缀整体清理） */
const KEY_PREFIX = 'sgj:note:buf:'

/** 编辑器身份：同一个编辑器 = 同一个身份（新笔记固定是 new，不区分用户） */
export function editorKeyOf(noteId?: number, draftId?: number): string {
  if (noteId) return `note:${noteId}`
  if (draftId) return `draft:${draftId}`
  return 'new'
}

export function bufferKey(username: string, editorKey: string): string {
  return `${KEY_PREFIX}${username}:${editorKey}`
}

export function readNoteBuffer(username: string, editorKey: string): NoteDraftBuffer | null {
  try {
    const raw = localStorage.getItem(bufferKey(username, editorKey))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? (parsed as NoteDraftBuffer) : null
  } catch {
    // 缓冲损坏（手改、旧版本结构）当作没有，不能让它阻断编辑页
    return null
  }
}

export function writeNoteBuffer(username: string, editorKey: string, buffer: NoteDraftBuffer): void {
  try {
    localStorage.setItem(bufferKey(username, editorKey), JSON.stringify(buffer))
  } catch {
    // 隐私模式 / 配额满：本地缓冲是尽力而为的兜底，写不进去不影响继续编辑
  }
}

export function clearNoteBuffer(username: string, editorKey: string): void {
  try {
    localStorage.removeItem(bufferKey(username, editorKey))
  } catch {
    // 同上：清理失败无需打断流程
  }
}

/** 登出时清掉本机全部笔记缓冲：换个人登录不该看到上一个人没保存的内容 */
export function clearAllNoteBuffers(): void {
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key && key.startsWith(KEY_PREFIX)) keys.push(key)
    }
    keys.forEach(key => localStorage.removeItem(key))
  } catch {
    // 同上
  }
}

/**
 * 本地缓冲是否属于当前这份草稿：身份里有的维度必须一致，
 * 否则那是别的草稿的缓冲，套用会把 A 草稿的内容带进 B（设计结论 26）。
 * `draftId` 分两种「没有」：身份还没 draftId（首次同步前）或缓冲还没有（新建时代替不了）——
 * 都算匹配，避免刚同步出 draftId 就把自己的缓冲判成别人的
 */
export function bufferMatchesIdentity(
  buffer: NoteDraftBuffer | null,
  identity: { noteId?: number; draftId?: number }
): boolean {
  if (!buffer) return false
  if (identity.noteId !== undefined && buffer.noteId !== identity.noteId) return false
  // 服务端首次同步后才回填 draftId，此时编辑器身份还没有它，回填前后都算匹配
  if (identity.draftId !== undefined && buffer.draftId !== undefined && buffer.draftId !== identity.draftId) {
    return false
  }
  return true
}

/** 进编辑器时该用哪一份内容 */
export type RestoreSource = 'local' | 'server' | 'none'

/**
 * 恢复来源判断（设计结论 10），三条分支只用服务端时间：
 * - `dirty` 为真 → 这些改动服务端从没见过 → 用本地（并立即补推一次）；
 * - `dirty` 为假且 baseUpdateTime 与服务端 updateTime 相等 → 两份等价，用本地即可；
 * - `dirty` 为假且两者不等 → 本地是旧快照且没有未推送的改动 → 用服务端。
 * 服务端没有草稿时：本地有未推送改动仍用本地，否则没什么可恢复的
 *
 * @param local            本地缓冲（已通过身份校验）
 * @param serverUpdateTime 服务端草稿的 updateTime（epoch 毫秒；没有草稿传 0）
 */
export function decideRestore(local: NoteDraftBuffer | null, serverUpdateTime: number): RestoreSource {
  if (!local) return serverUpdateTime > 0 ? 'server' : 'none'
  if (local.dirty) return 'local'
  if (serverUpdateTime <= 0) return 'none'
  if (local.baseUpdateTime === serverUpdateTime) return 'local'
  return 'server'
}

/**
 * 推送响应回来后更新缓冲。
 * 推送在飞时用户又打了字（seq 变了）**不能**清 dirty，否则那段新内容会被当作已同步，
 * 下次进编辑器就会拿服务端旧版本盖掉它。只有 seq 未变才把响应的 updateTime 写进 baseUpdateTime 并清 dirty
 *
 * @param buffer      当前缓冲
 * @param pushedSeq   发起这次推送时记录的 seq
 * @param currentSeq  响应回来时的 seq
 * @param draftId     服务端首次同步返回的 draftId（可为空）
 * @param updateTime  服务端返回的 updateTime（epoch 毫秒）
 */
export function applyPushResult(
  buffer: NoteDraftBuffer,
  pushedSeq: number,
  currentSeq: number,
  draftId: number | undefined,
  updateTime: number
): NoteDraftBuffer {
  const next: NoteDraftBuffer = { ...buffer, draftId: draftId ?? buffer.draftId }
  if (pushedSeq !== currentSeq) return next
  return { ...next, baseUpdateTime: updateTime, dirty: false }
}
