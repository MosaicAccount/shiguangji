import { describe, it, expect } from 'vitest'
import { itemStatusLabel } from '@/utils/itemStatus'

/**
 * T-91 状态文案三层取值（0.0.3 回归：地点被置成 DOING 后界面显示裸枚举 DOING）。
 */

const dicts = {
  sgj_movie_status: [
    { value: 'WANT', label: '想看' },
    { value: 'DOING', label: '在看' },
    { value: 'DONE', label: '看过' }
  ],
  sgj_book_status: [
    { value: 'WANT', label: '想读' },
    { value: 'DOING', label: '在读' },
    { value: 'DONE', label: '已读' }
  ],
  sgj_place_status: [
    { value: 'WANT', label: '想去' },
    { value: 'DONE', label: '去过' }
  ]
}

describe('T-91 itemStatusLabel', () => {
  it('字典命中时以字典为准（含新增的 DOING）', () => {
    expect(itemStatusLabel({ itemType: 'MOVIE', status: 'DOING' }, dicts)).toBe('在看')
    expect(itemStatusLabel({ itemType: 'BOOK', status: 'DOING' }, dicts)).toBe('在读')
    expect(itemStatusLabel({ itemType: 'PLACE', status: 'WANT' }, dicts)).toBe('想去')
  })

  it('字典缺失（缓存过期 / 增量脚本没跑）时用中文兜底，不露裸枚举', () => {
    expect(itemStatusLabel({ itemType: 'MOVIE', status: 'DOING' }, {})).toBe('在看')
    expect(itemStatusLabel({ itemType: 'BOOK', status: 'DONE' }, {})).toBe('已读')
  })

  it('地点出现 DOING（值域有、地点语义无）时给通用中文，不显示 DOING', () => {
    const label = itemStatusLabel({ itemType: 'PLACE', status: 'DOING' }, dicts)
    expect(label).not.toBe('DOING')
    expect(label).toBe('进行中')
  })

  it('未知状态也给出可读文案；空状态返回空串', () => {
    expect(itemStatusLabel({ itemType: 'MOVIE', status: 'PAUSED' }, dicts)).toBe('PAUSED')
    expect(itemStatusLabel({ itemType: 'MOVIE', status: '' }, dicts)).toBe('')
  })
})
