export function addressesDiffer(origin: string, destination: string): boolean {
  return origin.trim() !== destination.trim()
}

export function validPower(value: string | null): boolean {
  return value == null || value === '' || /^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/.test(value)
}

export function validVlan(value: number | null): boolean {
  return value == null || (Number.isInteger(value) && value >= 1 && value <= 4094)
}

export function validCoordinates(latitude: string | null, longitude: string | null): boolean {
  if (latitude === null && longitude === null) return true
  if (latitude === null || longitude === null) return false
  const coordinate = /^-?(?:0|[1-9]\d{0,2})(?:\.\d{1,7})?$/
  return coordinate.test(latitude) && coordinate.test(longitude)
    && Math.abs(Number(latitude)) <= 90 && Math.abs(Number(longitude)) <= 180
}
