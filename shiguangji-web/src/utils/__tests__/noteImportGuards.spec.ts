import { describe, it, expect, vi, beforeEach } from 'vitest'

/** 两个 api 模块换成 spy：这两道检查的判据全在「查到了什么」，不该真发请求 */
const mocks = vi.hoisted(() => ({
  getFrontNote: vi.fn(),
  getNoteDraftByNoteId: vi.fn(),
  listNoteDrafts: vi.fn()
}))

vi.mock('@/api/front/note', () => ({
  getFrontNote: (...args: any[]) => mocks.getFrontNote(...args)
}))
vi.mock('@/api/front/noteDraft', () => ({
  getNoteDraftByNoteId: (...args: any[]) => mocks.getNoteDraftByNoteId(...args),
  listNoteDrafts: (...args: any[]) => mocks.listNoteDrafts(...args)
}))

import { findDraftConflict, findRoundTripTarget } from '../noteImportGuards'
import { clearNoteBuffer, editorKeyOf, writeNoteBuffer } from '../noteDraftBuffer'

const USER = 'admin'

beforeEach(() => {
  vi.clearAllMocks()
  clearNoteBuffer(USER, editorKeyOf())
  clearNoteBuffer(USER, editorKeyOf(7))
})

describe('findDraftConflict：这次要落地的槽位里有没有未保存内容', () => {
  it('本地缓冲有内容就直接算冲突，不再发请求（停手 1s 就写，比服务端推得早）', async () => {
    writeNoteBuffer(USER, editorKeyOf(), { title: '半篇', content: '', baseUpdateTime: 0, dirty: true })

    expect(await findDraftConflict(USER)).toEqual({ chars: 2 })
    expect(mocks.listNoteDrafts).not.toHaveBeenCalled()
    expect(mocks.getNoteDraftByNoteId).not.toHaveBeenCalled()
  })

  it('新增态：本地没有时查草稿箱列表，空白草稿（noteId 为空）有内容就算冲突', async () => {
    mocks.listNoteDrafts.mockResolvedValue({ data: [{ draftId: 3, noteId: null, title: '', excerpt: 'abc' }] })

    expect(await findDraftConflict(USER)).toEqual({ chars: 3 })
    // 本页免登录 / 后台也可能遇到失效 token：必须走静默（结论 21）
    expect(mocks.listNoteDrafts).toHaveBeenCalledWith(true)
  })

  it('新增态：列表里只有「编辑某篇笔记」的草稿时不算冲突（那不是空白草稿槽位）', async () => {
    mocks.listNoteDrafts.mockResolvedValue({ data: [{ draftId: 3, noteId: 7, title: '别的笔记', excerpt: 'x' }] })

    expect(await findDraftConflict(USER)).toBeNull()
  })

  it('编辑态：按 noteId 取那篇笔记自己的草稿，带正文的内容也算冲突', async () => {
    mocks.getNoteDraftByNoteId.mockResolvedValue({ data: { draftId: 9, noteId: 7, title: 't', content: 'abc' } })

    expect(await findDraftConflict(USER, 7)).toEqual({ chars: 4 })
    expect(mocks.getNoteDraftByNoteId).toHaveBeenCalledWith(7)
    expect(mocks.listNoteDrafts).not.toHaveBeenCalled()
  })

  it('两边都没有内容 → null', async () => {
    mocks.listNoteDrafts.mockResolvedValue({ data: [{ draftId: 3, noteId: null, title: '', excerpt: '' }] })
    mocks.getNoteDraftByNoteId.mockResolvedValue({ data: { draftId: 9, noteId: 7, title: ' ', content: '' } })

    expect(await findDraftConflict(USER)).toBeNull()
    expect(await findDraftConflict(USER, 7)).toBeNull()
  })

  it('查询失败按「没有冲突」处理，不阻断导入（这一步只是多问一句）', async () => {
    mocks.listNoteDrafts.mockRejectedValue(new Error('boom'))
    mocks.getNoteDraftByNoteId.mockRejectedValue(new Error('boom'))

    expect(await findDraftConflict(USER)).toBeNull()
    expect(await findDraftConflict(USER, 7)).toBeNull()
  })
})

describe('findRoundTripTarget：文件里的 noteId 是不是自己的那一篇', () => {
  it('没给 noteId 直接 null，不发请求', async () => {
    expect(await findRoundTripTarget(undefined, USER)).toBeNull()
    expect(mocks.getFrontNote).not.toHaveBeenCalled()
  })

  it('create_by 是自己 → 返回那一篇', async () => {
    mocks.getFrontNote.mockResolvedValue({ data: { noteId: 12, title: '旧版本', createBy: USER } })

    expect(await findRoundTripTarget(12, USER)).toMatchObject({ noteId: 12, title: '旧版本' })
  })

  it('是别人的 → null（管理员也一样，按需求必须静默新建，不写他人数据）', async () => {
    mocks.getFrontNote.mockResolvedValue({ data: { noteId: 12, title: '别人的', createBy: 'someone-else' } })

    expect(await findRoundTripTarget(12, USER)).toBeNull()
  })

  it('查不到（已删除 / 无权访问）→ null，不报错', async () => {
    mocks.getFrontNote.mockRejectedValue(new Error('笔记不存在或无权访问'))

    expect(await findRoundTripTarget(12, USER)).toBeNull()
  })
})
