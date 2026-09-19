/**
 * 通用js方法封装处理
 * Copyright (c) 2019 ruoyi
 */

// 日期格式化
export function parseTime(time: any, pattern?: string): string | null {
  if (arguments.length === 0 || !time) {
    return null
  }
  const format = pattern || '{y}-{m}-{d} {h}:{i}:{s}'
  let date: Date
  if (typeof time === 'object') {
    date = time
  } else {
    if ((typeof time === 'string') && (/^[0-9]+$/.test(time))) {
      time = parseInt(time)
    } else if (typeof time === 'string') {
      time = time.replace(new RegExp(/-/gm), '/').replace('T', ' ').replace(new RegExp(/\.[\d]{3}/gm), '')
    }
    if ((typeof time === 'number') && (time.toString().length === 10)) {
      time = time * 1000
    }
    date = new Date(time)
  }
  const formatObj: Record<string, any> = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  const time_str = format.replace(/{(y|m|d|h|i|s|a)+}/g, (result, key) => {
    let value = formatObj[key]
    // Note: getDay() returns 0 on Sunday
    if (key === 'a') { return ['日', '一', '二', '三', '四', '五', '六'][value] }
    if (result.length > 0 && value < 10) {
      value = '0' + value
    }
    return value || 0
  })
  return time_str
}

// 表单重置
export function resetForm(refName: string): void {
  // @ts-ignore
  if ((this as any).$refs[refName]) {
    // @ts-ignore
    (this as any).$refs[refName].resetFields()
  }
}

// 添加日期范围
export function addDateRange(params: any, dateRange: string[], propName?: string): any {
  let search = params
  search.params = typeof (search.params) === 'object' && search.params !== null && !Array.isArray(search.params) ? search.params : {}
  dateRange = Array.isArray(dateRange) ? dateRange : []
  if (typeof (propName) === 'undefined') {
    search.params['beginTime'] = dateRange[0]
    search.params['endTime'] = dateRange[1]
  } else {
    search.params['begin' + propName] = dateRange[0]
    search.params['end' + propName] = dateRange[1]
  }
  return search
}

// 回显数据字典
export function selectDictLabel(datas: any, value: any): string {
  if (value === undefined) {
    return ""
  }
  const actions: string[] = []
  Object.keys(datas).some((key) => {
    if (datas[key].value == ('' + value)) {
      actions.push(datas[key].label)
      return true
    }
  })
  if (actions.length === 0) {
    actions.push(value)
  }
  return actions.join('')
}

// 回显数据字典（字符串、数组）
export function selectDictLabels(datas: any, value: any, separator?: string): string {
  if (value === undefined || value.length === 0) {
    return ""
  }
  if (Array.isArray(value)) {
    value = value.join(",")
  }
  const actions: string[] = []
  const currentSeparator = undefined === separator ? "," : separator
  const temp = value.split(currentSeparator)
  Object.keys(value.split(currentSeparator)).some((val) => {
    let match = false
    Object.keys(datas).some((key) => {
      if (datas[key].value == ('' + temp[val])) {
        actions.push(datas[key].label + currentSeparator)
        match = true
      }
    })
    if (!match) {
      actions.push(temp[val] + currentSeparator)
    }
  })
  return actions.join('').substring(0, actions.join('').length - 1)
}

// 字符串格式化(%s )
export function sprintf(str: string, ...args: any[]): string {
  let flag = true, i = 1
  str = str.replace(/%s/g, function () {
    const arg = args[i++]
    if (typeof arg === 'undefined') {
      flag = false
      return ''
    }
    return arg
  })
  return flag ? str : ''
}

// 转换字符串，undefined,null等转化为""
export function parseStrEmpty(str: any): string {
  if (!str || str == "undefined" || str == "null") {
    return ""
  }
  return str
}

// 数据合并
export function mergeRecursive(source: any, target: any): any {
  for (const p in target) {
    try {
      if (target[p].constructor == Object) {
        source[p] = mergeRecursive(source[p], target[p])
      } else {
        source[p] = target[p]
      }
    } catch (e) {
      source[p] = target[p]
    }
  }
  return source
}

/**
 * 构造树型结构数据
 * @param data 数据源
 * @param id id字段 默认 'id'
 * @param parentId 父节点字段 默认 'parentId'
 * @param children 孩子节点字段 默认 'children'
 */
export function handleTree(data: any[], id?: string, parentId?: string, children?: string): any[] {
  const config = {
    id: id || 'id',
    parentId: parentId || 'parentId',
    childrenList: children || 'children'
  }

  const childrenListMap: Record<string, any> = {}
  const tree: any[] = []
  for (const d of data) {
    const id = d[config.id]
    childrenListMap[id] = d
    if (!d[config.childrenList]) {
      d[config.childrenList] = []
    }
  }

  for (const d of data) {
    const parentId = d[config.parentId]
    const parentObj = childrenListMap[parentId]
    if (!parentObj) {
      tree.push(d)
    } else {
      parentObj[config.childrenList].push(d)
    }
  }
  return tree
}

/**
* 参数处理
* @param params  参数
*/
export function tansParams(params: Record<string, any>): string {
  let result = ''
  for (const propName of Object.keys(params)) {
    const value = params[propName]
    const part = encodeURIComponent(propName) + "="
    if (value !== null && value !== "" && typeof (value) !== "undefined") {
      if (typeof value === 'object') {
        for (const key of Object.keys(value)) {
          if (value[key] !== null && value[key] !== "" && typeof (value[key]) !== 'undefined') {
            const params = propName + '[' + key + ']'
            const subPart = encodeURIComponent(params) + "="
            result += subPart + encodeURIComponent(value[key]) + "&"
          }
        }
      } else {
        result += part + encodeURIComponent(value) + "&"
      }
    }
  }
  return result
}

// 返回项目路径
export function getNormalPath(p: string): string {
  if (p.length === 0 || !p || p == 'undefined') {
    return p
  }
  let res = p.replace('//', '/')
  if (res[res.length - 1] === '/') {
    return res.slice(0, res.length - 1)
  }
  return res
}

// 验证是否为blob格式
export function blobValidate(data: Blob): boolean {
  return data.type !== 'application/json'
}

/** 照片/封面地址转可展示 URL：外链原样，站内相对路径（/profile/...）补 baseURL 前缀 */
export function photoUrl(url?: string): string {
  if (!url) return ''
  if (/^(https?:|data:|blob:)/i.test(url)) return url
  return import.meta.env.VITE_APP_BASE_API + url
}

/** 摘要高亮片段：hit 为 true 的段落用高亮样式渲染 */
export interface KeywordSegment {
  text: string
  hit: boolean
}

/**
 * 摘要按检索词切片段（供搜索结果关键词高亮）。
 * 多词检索（空格分隔）任一词命中即标亮；大小写不敏感，与 MySQL 全文匹配行为一致；
 * 按字面量 indexOf 切分——关键词含 ( [ * 等元字符也不会报错，不拼 RegExp、不产出 HTML，
 * 模板里循环渲染片段即可（不要走 v-html）
 */
export function splitByKeyword(text: string | undefined, keyword: string | undefined): KeywordSegment[] {
  const words = (keyword || '').trim().split(/\s+/).filter(w => w.length > 0)
  if (!text) return []
  if (words.length === 0) return [{ text, hit: false }]
  const lower = text.toLowerCase()
  const ranges: Array<[number, number]> = []
  for (const word of words) {
    const lw = word.toLowerCase()
    let index = 0
    while ((index = lower.indexOf(lw, index)) !== -1) {
      ranges.push([index, index + lw.length])
      index += lw.length
    }
  }
  if (ranges.length === 0) return [{ text, hit: false }]
  ranges.sort((a, b) => a[0] - b[0])
  // 相邻/重叠命中区间合并，避免片段交叉
  const merged: Array<[number, number]> = []
  for (const range of ranges) {
    const last = merged[merged.length - 1]
    if (last && range[0] <= last[1]) {
      last[1] = Math.max(last[1], range[1])
    } else {
      merged.push([range[0], range[1]])
    }
  }
  const segments: KeywordSegment[] = []
  let pos = 0
  for (const [start, end] of merged) {
    if (start > pos) segments.push({ text: text.slice(pos, start), hit: false })
    segments.push({ text: text.slice(start, end), hit: true })
    pos = end
  }
  if (pos < text.length) segments.push({ text: text.slice(pos), hit: false })
  return segments
}

/**
 * 在已渲染的 DOM 内为检索词打高亮标记（详情页承接：列表带关键词进入时全文可导航）。
 * 遍历文本节点，命中片段用 mark.kw-hit 包裹，返回全部标记元素（供上一处/下一处跳转）。
 * 多词任一命中即标记；重叠区间合并；跨元素边界的词不处理（与浏览器原生查找行为一致）。
 * 纯 DOM 操作，不产出 HTML 字符串；重复调用前会先清除旧标记
 */
export function applyKeywordHighlights(root: HTMLElement | null | undefined, keyword: string | undefined): HTMLElement[] {
  if (!root) return []
  clearKeywordHighlights(root)
  const words = (keyword || '').trim().split(/\s+/).filter(w => w.length > 0)
  if (words.length === 0) return []

  // 先收集再改写：遍历过程中替换节点会使 TreeWalker 失效
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode: (node: Text) => {
      const value = node.nodeValue || ''
      const parent = node.parentElement
      if (!value.trim() || !parent || parent.closest('mark')) return NodeFilter.FILTER_REJECT
      const lower = value.toLowerCase()
      return words.some(w => lower.includes(w.toLowerCase())) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    }
  })
  const targets: Text[] = []
  while (walker.nextNode()) targets.push(walker.currentNode as Text)

  const marks: HTMLElement[] = []
  for (const node of targets) {
    const text = node.nodeValue || ''
    const lower = text.toLowerCase()
    const ranges: Array<[number, number]> = []
    for (const word of words) {
      const lw = word.toLowerCase()
      let index = 0
      while ((index = lower.indexOf(lw, index)) !== -1) {
        ranges.push([index, index + lw.length])
        index += lw.length
      }
    }
    if (ranges.length === 0) continue
    ranges.sort((a, b) => a[0] - b[0])
    const merged: Array<[number, number]> = []
    for (const range of ranges) {
      const last = merged[merged.length - 1]
      if (last && range[0] <= last[1]) last[1] = Math.max(last[1], range[1])
      else merged.push([range[0], range[1]])
    }
    const frag = document.createDocumentFragment()
    let pos = 0
    for (const [start, end] of merged) {
      if (start > pos) frag.appendChild(document.createTextNode(text.slice(pos, start)))
      const mark = document.createElement('mark')
      mark.className = 'kw-hit'
      mark.textContent = text.slice(start, end)
      frag.appendChild(mark)
      marks.push(mark)
      pos = end
    }
    if (pos < text.length) frag.appendChild(document.createTextNode(text.slice(pos)))
    node.parentNode?.replaceChild(frag, node)
  }
  return marks
}

/** 清除 {@link applyKeywordHighlights} 产生的标记并合并相邻文本节点，恢复原文 */
export function clearKeywordHighlights(root: HTMLElement | null | undefined): void {
  if (!root) return
  root.querySelectorAll('mark.kw-hit').forEach(mark => {
    const parent = mark.parentNode
    if (!parent) return
    parent.replaceChild(document.createTextNode(mark.textContent || ''), mark)
    parent.normalize()
  })
}
