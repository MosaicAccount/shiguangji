/**
 * 评分文案：后台统一 0-10 分，前台按 5 星显示（四舍五入到整星）。
 * 列表、时间线、详情共用，避免各页各写一套 repeat。
 */
export function starText(rating?: number | null, max = 5): string {
  if (!rating || rating <= 0) return ''
  const n = Math.min(max, Math.max(0, Math.round(rating / 2)))
  return '★'.repeat(n) + '☆'.repeat(max - n)
}
