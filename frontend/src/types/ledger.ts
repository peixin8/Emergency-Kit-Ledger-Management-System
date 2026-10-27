export type Direction = 'IN' | 'OUT' | 'TRANSFER'
export type ApplicationMethod = 'ONLINE' | 'PAPER' | 'PHONE' | 'OTHER'
export type DeviceCondition = 'GOOD' | 'REPAIR' | 'FAULT' | 'RETIRED'


export interface LedgerInput {
  ledgerCode: string
  originalAddress: string
  destinationAddress: string
  direction: Direction
  applicationMethod: ApplicationMethod
  applicantContact: string
  applicantPhone: string
  applicationDate: string
  deviceCondition: DeviceCondition
  accessRoom: string
  fiberCore: string
  networkStandard: string
  bbuModel: string
  rruModel: string
  bbuRruPowerWatts: string | null
  managementIp: string
  managementVlan: number | null
  businessIp: string
  businessVlan: number | null
  deviceSerialNo: string
  assetOwner: string
  remarks: string
}
export interface Ledger extends LedgerInput {
  id: string
  organizationId: string
  version: string
  createdAt: string
  updatedAt: string
}
export interface LedgerPage { records: Ledger[]; total: number; page: number; pageSize: number }
export interface LedgerHistoryEntry { id: string; ledgerId: string; action: 'CREATE' | 'UPDATE' | 'DELETE'; occurredAt: string; before: Ledger | null; after: Ledger | null }
export interface LedgerHistoryPage { records: LedgerHistoryEntry[]; total: number; page: number; pageSize: number }
export interface PageQuery { page: number; pageSize: number; keyword?: string; status?: DeviceCondition; direction?: Direction }

export const directionLabels: Record<Direction, string> = { IN: '进场', OUT: '出场', TRANSFER: '调拨' }
export const methodLabels: Record<ApplicationMethod, string> = { ONLINE: '线上申请', PAPER: '纸质申请', PHONE: '电话申请', OTHER: '其他方式' }
export const conditionLabels: Record<DeviceCondition, string> = { GOOD: '状况良好', REPAIR: '维修中', FAULT: '故障', RETIRED: '退役' }

export function emptyLedger(): LedgerInput {
  return {
    ledgerCode: '', originalAddress: '', destinationAddress: '', direction: 'IN',
    applicationMethod: 'ONLINE', applicantContact: '', applicantPhone: '',
    applicationDate: new Date().toLocaleDateString('sv-SE'), deviceCondition: 'GOOD',
    accessRoom: '', fiberCore: '', networkStandard: '', bbuModel: '', rruModel: '',
    bbuRruPowerWatts: '', managementIp: '', managementVlan: null, businessIp: '',
    businessVlan: null, deviceSerialNo: '', assetOwner: '', remarks: ''
  }
}
