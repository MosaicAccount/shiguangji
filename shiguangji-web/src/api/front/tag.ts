import request from '@/utils/request'
import type { AjaxResult } from '@/types/api/common'
import type { SgjTag, TagModule } from '@/types/api/business/tag'

// 前台查询指定模块的启用标签列表（匿名可用，供标签下拉选择）
export function listAppTag(module: TagModule): Promise<AjaxResult<SgjTag[]>> {
  return request({
    url: '/app/tag/list',
    method: 'get',
    params: { module }
  })
}
