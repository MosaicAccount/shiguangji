import { describe, it, expect } from 'vitest'
import { toCoord } from '../map'

describe('toCoord', () => {
  it('解析 [经度, 纬度] 数组', () => {
    expect(toCoord([116.4074, 39.9042])).toEqual({ lat: 39.9042, lng: 116.4074 })
  })

  it('数字字符串可转换', () => {
    expect(toCoord(['116', '39'])).toEqual({ lat: 39, lng: 116 })
  })

  it('非数组返回 null', () => {
    expect(toCoord(null)).toBeNull()
    expect(toCoord(undefined)).toBeNull()
    expect(toCoord('116,39')).toBeNull()
    expect(toCoord({})).toBeNull()
  })

  it('长度不足返回 null', () => {
    expect(toCoord([116])).toBeNull()
    expect(toCoord([])).toBeNull()
  })

  it('非数字值返回 null', () => {
    expect(toCoord([NaN, 39])).toBeNull()
    expect(toCoord([116, NaN])).toBeNull()
    expect(toCoord(['abc', 39])).toBeNull()
    expect(toCoord([116, 'abc'])).toBeNull()
    expect(toCoord([Infinity, 39])).toBeNull()
  })
})
