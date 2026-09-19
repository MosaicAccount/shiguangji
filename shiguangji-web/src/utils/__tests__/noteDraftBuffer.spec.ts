import { describe, it, expect, beforeEach } from 'vitest'
import {
  decideRestore,
  applyPushResult,
  bufferMatchesIdentity,
  editorKeyOf,
  bufferKey,
  readNoteBuffer,
  writeNoteBuffer,
  clearNoteBuffer,
  clearAllNoteBuffers,
  type NoteDraftBuffer
} from '../noteDraftBuffer'

function buffer(overrides: Partial<NoteDraftBuffer> = {}): NoteDraftBuffer {
  return {
    draftId: 7,
    noteId: undefined,
    title: '标题',
    content: '正文',
    baseUpdateTime: 1000,
    dirty: false,
    ...overrides
  }
}

/** issue #32：恢复来源判断只用服务端时间（本地钟与服务端钟比不出谁新，只比得出谁的钟快） */
describe('decideRestore', () => {
  it('dirty 为真时用本地：这些改动服务端从没见过', () => {
    expect(decideRestore(buffer({ dirty: true, baseUpdateTime: 1000 }), 2000)).toBe('local')
    expect(decideRestore(buffer({ dirty: true }), 0)).toBe('local')
  })

  it('dirty 为假且 baseUpdateTime 与服务端相等时两份等价', () => {
    expect(decideRestore(buffer({ baseUpdateTime: 1000 }), 1000)).toBe('local')
  })

  it('dirty 为假且时间不等时用服务端（本地是旧快照且没有未推送的改动）', () => {
    expect(decideRestore(buffer({ baseUpdateTime: 1000 }), 2000)).toBe('server')
  })

  it('没有本地缓冲时用服务端；两边都没有则没有可恢复的内容', () => {
    expect(decideRestore(null, 2000)).toBe('server')
    expect(decideRestore(null, 0)).toBe('none')
  })

  it('服务端没有草稿且本地没有未推送改动时不恢复（避免复活别处已删除的草稿）', () => {
    expect(decideRestore(buffer({ dirty: false }), 0)).toBe('none')
  })
})

/** 身份隔离：别的用户 / 别的草稿的缓冲不能套用（结论 26） */
describe('bufferMatchesIdentity', () => {
  it('noteId 不一致时不采用', () => {
    expect(bufferMatchesIdentity(buffer({ noteId: 1 }), { noteId: 2 })).toBe(false)
    expect(bufferMatchesIdentity(buffer({ noteId: 1 }), { noteId: 1 })).toBe(true)
  })

  it('draftId 不一致时不采用', () => {
    expect(bufferMatchesIdentity(buffer({ draftId: 3 }), { draftId: 4 })).toBe(false)
  })

  it('服务端首次同步前缓冲还没有 draftId，仍算匹配', () => {
    expect(bufferMatchesIdentity(buffer({ draftId: undefined }), { draftId: 9 })).toBe(true)
  })

  it('没有缓冲时不采用', () => {
    expect(bufferMatchesIdentity(null, {})).toBe(false)
  })
})

/** 推送竞态：推送在飞时用户又打了字，响应回来不能当作「当前内容已同步」 */
describe('applyPushResult', () => {
  it('seq 未变：清 dirty 并把服务端 updateTime 写进 baseUpdateTime', () => {
    const next = applyPushResult(buffer({ dirty: true, baseUpdateTime: 0 }), 5, 5, 12, 2000)
    expect(next.dirty).toBe(false)
    expect(next.baseUpdateTime).toBe(2000)
    expect(next.draftId).toBe(12)
  })

  it('seq 变了：保留 dirty 与旧 baseUpdateTime，只回填 draftId', () => {
    const next = applyPushResult(buffer({ dirty: true, baseUpdateTime: 1000 }), 5, 6, 12, 2000)
    expect(next.dirty).toBe(true)
    expect(next.baseUpdateTime).toBe(1000)
    expect(next.draftId).toBe(12)
  })

  it('服务端没回 draftId 时保留原有的', () => {
    const next = applyPushResult(buffer({ draftId: 7 }), 1, 1, undefined, 500)
    expect(next.draftId).toBe(7)
  })
})

/** 缓冲的存取与按用户清理 */
describe('noteDraftBuffer 存取', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('编辑器身份：编辑态按 noteId、继续写按 draftId、全新固定是 new', () => {
    expect(editorKeyOf(3, undefined)).toBe('note:3')
    expect(editorKeyOf(undefined, 5)).toBe('draft:5')
    expect(editorKeyOf(undefined, undefined)).toBe('new')
  })

  it('读写清：key 里带上用户名，换个人登录读不到上一个人的内容', () => {
    writeNoteBuffer('alice', 'new', buffer())
    expect(readNoteBuffer('alice', 'new')?.content).toBe('正文')
    expect(readNoteBuffer('bob', 'new')).toBeNull()
    expect(localStorage.getItem(bufferKey('alice', 'new'))).toBeTruthy()
    clearNoteBuffer('alice', 'new')
    expect(readNoteBuffer('alice', 'new')).toBeNull()
  })

  it('登出清理：清掉本机全部笔记缓冲，不碰其它 key', () => {
    writeNoteBuffer('alice', 'new', buffer())
    writeNoteBuffer('bob', 'note:1', buffer())
    localStorage.setItem('unrelated', 'x')
    clearAllNoteBuffers()
    expect(readNoteBuffer('alice', 'new')).toBeNull()
    expect(readNoteBuffer('bob', 'note:1')).toBeNull()
    expect(localStorage.getItem('unrelated')).toBe('x')
  })

  it('缓冲损坏时当作没有，不抛错', () => {
    localStorage.setItem(bufferKey('alice', 'new'), '{oops')
    expect(readNoteBuffer('alice', 'new')).toBeNull()
  })
})
