import type { Ledger } from './ledger'

export interface Coordinates { latitude: string; longitude: string }

export function ledgerCoordinates(ledger: Pick<Ledger, 'latitude' | 'longitude'> | null): Coordinates | null {
  if (!ledger || ledger.latitude === null || ledger.longitude === null || ledger.latitude.trim() === '' || ledger.longitude.trim() === '') return null
  const latitude = Number(ledger.latitude)
  const longitude = Number(ledger.longitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null
  return { latitude: ledger.latitude, longitude: ledger.longitude }
}

// 高德导航使用 GCJ-02；台账地图与数据库坐标均为 WGS84。
function toGcj02(longitude: number, latitude: number): [number, number] {
  if (longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271) return [longitude, latitude]
  const x = longitude - 105
  const y = latitude - 35
  let dLat = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  dLat += (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
  dLat += (20 * Math.sin(y * Math.PI) + 40 * Math.sin(y / 3 * Math.PI)) * 2 / 3
  dLat += (160 * Math.sin(y / 12 * Math.PI) + 320 * Math.sin(y * Math.PI / 30)) * 2 / 3
  let dLon = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  dLon += (20 * Math.sin(6 * x * Math.PI) + 20 * Math.sin(2 * x * Math.PI)) * 2 / 3
  dLon += (20 * Math.sin(x * Math.PI) + 40 * Math.sin(x / 3 * Math.PI)) * 2 / 3
  dLon += (150 * Math.sin(x / 12 * Math.PI) + 300 * Math.sin(x / 30 * Math.PI)) * 2 / 3
  const radLat = latitude / 180 * Math.PI
  const ee = 0.006693421622965943
  const magic = 1 - ee * Math.sin(radLat) ** 2
  const sqrtMagic = Math.sqrt(magic)
  dLat = dLat * 180 / ((6378245 * (1 - ee)) / (magic * sqrtMagic) * Math.PI)
  dLon = dLon * 180 / (6378245 / sqrtMagic * Math.cos(radLat) * Math.PI)
  return [longitude + dLon, latitude + dLat]
}

export function amapNavigationUrl(coordinates: Coordinates, name: string): string {
  const [longitude, latitude] = toGcj02(Number(coordinates.longitude), Number(coordinates.latitude))
  const params = new URLSearchParams({ to: `${longitude.toFixed(7)},${latitude.toFixed(7)},${name}`, mode: 'car', src: 'emergency-ledger' })
  return `https://uri.amap.com/navigation?${params.toString()}`
}
