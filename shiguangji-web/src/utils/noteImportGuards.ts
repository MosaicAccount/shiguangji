/**
 * 导入前的两道检查（前台笔记列表页与后台笔记管理页**共用同一份**）
 *
 * 为什么单独一个模块：这两件事的答案必须两边一致，否则同一份文件在两条入口上的行为就不一样了——
 * 前台会问你一句，后台静默顶掉；前台认出是同一篇，后台多存一篇。所以判据只有这一处。
 *
 * 与 `utils/noteImport.ts` 的分工：那边是**纯解析**（不碰网络，所以能单测），这边要查草稿与笔记（有网络）。
 */
import { getFrontNote } from '@/api/front/note'
import { getNoteDraftByNoteId, listNoteDrafts } from '@/api/front/noteDraft'
import { editorKeyOf, readNoteBuffer } from '@/utils/noteDraftBuffer'
import type { SgjNote } from '@/types/api/business/note'

/** 目标槽位里未保存内容的规模（字） */
export interface DraftConflict {
  chars: number
}

/**
 * 这次导入要落地的那个槽位里，是不是已经存着还没保存的内容。
 *
 * 两个槽位共用一套判据：
 * - `entryNoteId` 缺省 → 新增态 = 空白草稿槽位（`note_id = 0`，与前台「写笔记」是同一份）；
 * - 给了 → 那篇笔记自己的编辑态草稿槽位。
 *
 * 两个来源都要看，缺一个就会漏：本地缓冲（停手 1s 就写，比服务端 15s 推得早）与服务端草稿
 * （换设备 / 清过缓存时本地没有，只有它）。判据取宽——**只要有内容就当冲突**：多问一次只是多一次点击，
 * 漏问一次就是用户半篇笔记无声消失。
 *
 * @returns 冲突摘要；没有冲突返回 null。查不到（网络 / 无权限）也按「没有」处理——这一步只是多问一句，
 *          不该因为它的失败阻断导入
 */
export async function findDraftConflict(username: string, entryNoteId?: number): Promise<DraftConflict | null> {
  const buffer = readNoteBuffer(username, editorKeyOf(entryNoteId))
  const local = `${buffer?.title || ''}${buffer?.content || ''}`.trim()
  if (local) return { chars: local.length }

  try {
    // 草稿箱列表不下发正文，用 title / excerpt 判断就够了（纯代码块的正文在摘要里也会回退成原文开头）；
    // 按 noteId 取单条时是带正文的
    if (entryNoteId) {
      const single = await getNoteDraftByNoteId(entryNoteId)
      const server = `${single.data?.title || ''}${single.data?.content || ''}`.trim()
      if (server) return { chars: server.length }
    } else {
      const box = await listNoteDrafts(true)
      const blank = (box.data || []).find(item => !item.noteId)
      const server = `${blank?.title || ''}${blank?.excerpt || ''}`.trim()
      if (server) return { chars: server.length }
    }
  } catch {
    return null
  }
  return null
}

/**
 * 文件 front-matter 里的 `noteId` 指向的笔记，且它确实是**当前用户自己**的那一篇。
 *
 * 严格按 `create_by` 比对，而不是「能不能看到」：否则管理员导入一份别人的导出文件也会被问「更新」，
 * 而按需求那种情况必须静默新建（不写他人数据）。
 *
 * @returns 命中且是自己的那一篇；拿不到 / 不是自己的 / 已删除 / 没给 noteId 都返回 null
 */
export async function findRoundTripTarget(noteId: number | undefined, username: string): Promise<SgjNote | null> {
  if (!noteId) return null
  try {
    const response = await getFrontNote(noteId)
    const note = response.data
    return note && note.createBy === username ? note : null
  } catch {
    // 查不到（已删除）或无权访问：交给调用方按新建处理
    return null
  }
}
