import boundary from '../data/xiamen-boundary.json'

export type Position = [longitude: number, latitude: number]
export type Region = Position[][][]

function parseRing(input: unknown): Position[] | null {
  if (!Array.isArray(input) || input.length < 4) return null
  const ring: Position[] = []
  for (const coordinate of input) {
    if (!Array.isArray(coordinate) || coordinate.length !== 2) return null
    const [longitude, latitude]: unknown[] = coordinate
    if (typeof longitude !== 'number' || typeof latitude !== 'number' || !Number.isFinite(longitude) || !Number.isFinite(latitude) || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) return null
    ring.push([longitude, latitude])
  }
  const first = ring[0]!
  const last = ring[ring.length - 1]!
  if (first[0] !== last[0] || first[1] !== last[1]) return null
  let area = 0
  for (let index = 1; index < ring.length; index++) {
    const a = ring[index - 1]!
    const b = ring[index]!
    area += (a[0] - first[0]) * (b[1] - first[1]) - (b[0] - first[0]) * (a[1] - first[1])
  }
  return Math.abs(area) > 1e-12 ? ring : null
}

export function parseRegion(input: unknown): Region | null {
  if (typeof input !== 'object' || input === null || !('type' in input) || !('coordinates' in input)) return null
  const polygons: unknown = input.type === 'Polygon' ? [input.coordinates] : input.type === 'MultiPolygon' ? input.coordinates : null
  if (!Array.isArray(polygons) || !polygons.length) return null
  const region: Region = []
  for (const polygon of polygons) {
    if (!Array.isArray(polygon) || !polygon.length) return null
    const rings: Position[][] = []
    for (const inputRing of polygon) {
      const ring = parseRing(inputRing)
      if (!ring) return null
      rings.push(ring)
    }
    region.push(rings)
  }
  return region
}

export const xiamenRegion = parseRegion(boundary.geometry)

// -1：环外，0：边界，1：环内。边界容差远小于七位小数坐标精度。
function ringLocation(ring: Position[], latitude: number, longitude: number): -1 | 0 | 1 {
  let inside = false
  for (let index = 1; index < ring.length; index++) {
    const [ax, ay] = ring[index - 1]!
    const [bx, by] = ring[index]!
    const dx = bx - ax
    const dy = by - ay
    const length = Math.hypot(dx, dy)
    if (length > 0 && Math.abs((longitude - ax) * dy - (latitude - ay) * dx) <= 1e-10 * length && longitude >= Math.min(ax, bx) - 1e-10 && longitude <= Math.max(ax, bx) + 1e-10 && latitude >= Math.min(ay, by) - 1e-10 && latitude <= Math.max(ay, by) + 1e-10) return 0
    if ((ay > latitude) !== (by > latitude) && longitude < (bx - ax) * (latitude - ay) / (by - ay) + ax) inside = !inside
  }
  return inside ? 1 : -1
}

export function containsCoordinate(region: Region | null, latitude: number, longitude: number): boolean {
  if (!region || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return false
  for (const polygon of region) {
    const locations = polygon.map(ring => ringLocation(ring, latitude, longitude))
    if (locations.includes(0)) return true
    if (locations[0] === 1 && locations.slice(1).every(location => location === -1)) return true
  }
  return false
}

export function regionBounds(region: Region | null): [[number, number], [number, number]] | null {
  if (!region?.length) return null
  let west = Infinity, south = Infinity, east = -Infinity, north = -Infinity
  for (const polygon of region) for (const ring of polygon) for (const [longitude, latitude] of ring) {
    west = Math.min(west, longitude); east = Math.max(east, longitude)
    south = Math.min(south, latitude); north = Math.max(north, latitude)
  }
  return [[south, west], [north, east]]
}

export function regionPath(region: Region | null, project: (latitude: number, longitude: number) => { x: number; y: number }): string | null {
  if (!region?.length) return null
  let path = ''
  for (const polygon of region) for (const ring of polygon) {
    for (let index = 0; index < ring.length; index++) {
      const coordinate = ring[index]!
      const point = project(coordinate[1], coordinate[0])
      if (!Number.isFinite(point.x) || !Number.isFinite(point.y)) return null
      path += `${index ? 'L' : 'M'}${point.x} ${point.y}`
    }
    path += 'Z'
  }
  return path
}
