import { saveAs } from 'file-saver'
import { parseTime } from '@/utils/sgj'

/**
 * 前端数据导出工具（备份导出最小版）
 *
 * 后端列表接口分页上限为 100（PageUtils.MAX_PAGE_SIZE），
 * 导出时按上限循环分页拉取全量数据，前端生成 JSON / CSV 文件下载。
 */

/** 单页行数：取后端 PageUtils.MAX_PAGE_SIZE 上限 */
const EXPORT_PAGE_SIZE = 100

/** 分页循环安全上限，防止 total 异常导致死循环 */
const MAX_PAGE_COUNT = 1000

/** 分页列表响应的最小结构 */
interface PageResult<T> {
  rows: T[]
  total: number
}

/**
 * 循环分页拉取全量数据
 *
 * @param fetchPage 分页请求函数（调用方负责带上与列表页一致的筛选参数）
 * @returns 全量行数据
 */
export async function fetchAllRows<T>(
  fetchPage: (pageNum: number, pageSize: number) => Promise<PageResult<T>>
): Promise<T[]> {
  const all: T[] = []
  let pageNum = 1
  let total = 0
  do {
    const res = await fetchPage(pageNum, EXPORT_PAGE_SIZE)
    total = Number(res.total) || 0
    const rows = res.rows || []
    all.push(...rows)
    // 防御：返回空页说明没有更多数据，避免 total 异常时死循环
    if (rows.length === 0) {
      break
    }
    pageNum++
  } while (all.length < total && pageNum <= MAX_PAGE_COUNT)
  return all
}

/** 导出文件名日期段：YYYYMMDD */
export function exportDateTag(): string {
  return parseTime(new Date(), '{y}{m}{d}') || ''
}

/** 导出 JSON 文件（对象数组，可直接解析） */
export function downloadJson(filename: string, rows: any[]): void {
  saveAs(new Blob([JSON.stringify(rows, null, 2)], { type: 'application/json;charset=utf-8' }), filename)
}

/** CSV 单元格转义：含逗号/引号/换行时加引号包裹，内部引号翻倍 */
function csvCell(value: any): string {
  if (value === null || value === undefined) {
    return ''
  }
  let text = String(value)
  // 日期字段统一格式，避免 Excel/文本阅读歧义
  if (text.includes(',') || text.includes('"') || text.includes('\n') || text.includes('\r')) {
    text = '"' + text.replace(/"/g, '""') + '"'
  }
  return text
}

/**
 * 导出 CSV 文件（含表头；前置 BOM 保证 Excel 中文不乱码）
 *
 * @param columns 列定义（label 为表头，key 为行数据字段名）
 */
export function downloadCsv(
  filename: string,
  columns: { label: string; key: string }[],
  rows: Record<string, any>[]
): void {
  const head = columns.map(col => csvCell(col.label)).join(',')
  const body = rows
    .map(row => columns.map(col => csvCell(row[col.key])).join(','))
    .join('\r\n')
  const csv = '\ufeff' + head + '\r\n' + (body ? body + '\r\n' : '')
  saveAs(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename)
}
