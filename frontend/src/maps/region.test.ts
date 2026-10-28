import { expect, it } from 'vitest'
import { containsCoordinate, parseRegion, regionBounds, regionPath, xiamenRegion } from './region'

const withHole = { type: 'Polygon', coordinates: [
  [[0, 0], [10, 0], [10, 10], [0, 10], [0, 0]],
  [[3, 3], [7, 3], [7, 7], [3, 7], [3, 3]]
] }

it('厦门行政边界区分域内、域外及边界点，不用包围盒冒充行政区', () => {
  expect(xiamenRegion).not.toBeNull()
  expect(containsCoordinate(xiamenRegion, 24.4798345, 118.0894251)).toBe(true)
  expect(containsCoordinate(xiamenRegion, 26.0745, 119.2965)).toBe(false)
  expect(containsCoordinate(xiamenRegion, 24.4, 117.9)).toBe(false)
  expect(containsCoordinate(xiamenRegion, 24.5933146, 117.882185)).toBe(true)
  expect(regionBounds(xiamenRegion)).toEqual([[24.3822222, 117.882185], [24.9072638, 118.4494442]])
})

it('Polygon洞内排除，外环与洞边界算区域内', () => {
  const region = parseRegion(withHole)
  expect(containsCoordinate(region, 2, 2)).toBe(true)
  expect(containsCoordinate(region, 5, 5)).toBe(false)
  expect(containsCoordinate(region, 0, 5)).toBe(true)
  expect(containsCoordinate(region, 3, 5)).toBe(true)
  expect(containsCoordinate(region, -1, 5)).toBe(false)
})

it('MultiPolygon保留每个面及洞，并生成同一套投影裁剪路径', () => {
  const region = parseRegion({ type: 'MultiPolygon', coordinates: [withHole.coordinates, [[[20, 20], [21, 20], [21, 21], [20, 21], [20, 20]]]] })
  expect(containsCoordinate(region, 20.5, 20.5)).toBe(true)
  expect(containsCoordinate(region, 5, 5)).toBe(false)
  expect(containsCoordinate(region, 15, 15)).toBe(false)
  const path = regionPath(region, (latitude, longitude) => ({ x: longitude * 2, y: latitude * 3 }))
  expect(path).toBe('M0 0L20 0L20 30L0 30L0 0ZM6 9L14 9L14 21L6 21L6 9ZM40 60L42 60L42 63L40 63L40 60Z')
})

it.each([null, {}, { type: 'Polygon', coordinates: [] }, { type: 'Polygon', coordinates: [[[0, 0], [1, 1], [2, 2], [0, 0]]] }, { type: 'Polygon', coordinates: [[[0, 0], [1, 0], [1, 1], [NaN, 0]]] }])('无效区域安全拒绝：%j', input => {
  expect(parseRegion(input)).toBeNull()
  expect(containsCoordinate(parseRegion(input), 0, 0)).toBe(false)
  expect(regionPath(parseRegion(input), () => ({ x: 0, y: 0 }))).toBeNull()
})
