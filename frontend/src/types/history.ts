import type { Ledger, LedgerInput } from './ledger'

const fieldLabels: { key: keyof LedgerInput; label: string }[] = [
  { key: 'ledgerCode', label: '台账编号' }, { key: 'originalAddress', label: '原始地址' },
  { key: 'destinationAddress', label: '目标地址' }, { key: 'direction', label: '流转方向' },
  { key: 'applicationMethod', label: '申请方式' }, { key: 'applicantContact', label: '申请联系人' },
  { key: 'applicantPhone', label: '联系电话' }, { key: 'applicationDate', label: '申请日期' },
  { key: 'deviceCondition', label: '设备状况' }, { key: 'accessRoom', label: '接入机房' },
  { key: 'fiberCore', label: '纤芯' }, { key: 'networkStandard', label: '制式' },
  { key: 'bbuModel', label: 'BBU 型号' }, { key: 'rruModel', label: 'RRU 型号' },
  { key: 'bbuRruPowerWatts', label: 'BBU–RRU 功耗' }, { key: 'managementIp', label: '网管 IP' },
  { key: 'managementVlan', label: '网管 VLAN' }, { key: 'businessIp', label: '业务 IP' },
  { key: 'businessVlan', label: '业务 VLAN' }, { key: 'deviceSerialNo', label: '设备序列号' },
  { key: 'assetOwner', label: '资产归属' }, { key: 'remarks', label: '备注' }
]

export function changedFields(before: Ledger, after: Ledger) {
  return fieldLabels.filter(({ key }) => before[key] !== after[key]).map(({ key, label }) => ({
    label, before: display(before[key]), after: display(after[key])
  }))
}

function display(value: string | number | null) { return value === null || value === '' ? '—' : String(value) }
