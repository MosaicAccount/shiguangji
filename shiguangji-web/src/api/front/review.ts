import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { ReviewYearData } from '@/types/api/front/review'

// 查询某一年的回顾数据（年份列表 / 汇总 / 地点 / 评分榜 / 标签）
export function getReviewYear(year: number | string): Promise<AjaxResult<ReviewYearData>> {
  return request({
    url: '/app/review/year',
    method: 'get',
    params: { year }
  })
}
