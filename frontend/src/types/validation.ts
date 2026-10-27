export function addressesDiffer(origin: string, destination: string): boolean {
  return origin.trim() !== destination.trim()
}

export function validPower(value: string | null): boolean {
  return value == null || value === '' || /^(?:0|[1-9]\d{0,9})(?:\.\d{1,2})?$/.test(value)
}

export function validVlan(value: number | null): boolean {
  return value == null || (Number.isInteger(value) && value >= 1 && value <= 4094)
}
