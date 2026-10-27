import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ViewTag { path: string; fullPath: string; title: string; name: string }
const home: ViewTag = { path: '/ledgers', fullPath: '/ledgers', title: '台账列表', name: 'ledgers' }
export const useTagsStore = defineStore('tags', () => {
  const tags = ref<ViewTag[]>([home])
  const key = () => 'emergency-ledger-tags-shared'
  function restore() {
    tags.value = [home]
    try {
      const data: unknown = JSON.parse(sessionStorage.getItem(key()) || '[]')
      if (Array.isArray(data)) for (const item of data) {
        if (typeof item === 'object' && item !== null && 'path' in item && item.path === '/ledgers' && !tags.value.some(t => t.path === item.path)) tags.value.push(home)
      }
    } catch { /* ignore bad cache */ }
  }
  function add(tag: ViewTag) {
    if (tag.path !== '/ledgers') return
    if (!tags.value.some(t => t.path === tag.path)) tags.value.push(tag)
    sessionStorage.setItem(key(), JSON.stringify(tags.value))
  }
  function close(path: string) {
    tags.value = tags.value.filter(t => t.path !== path || t.path === home.path)
    sessionStorage.setItem(key(), JSON.stringify(tags.value))
    return tags.value[tags.value.length - 1]?.fullPath ?? home.fullPath
  }
  function closeOthers() { tags.value = [home]; sessionStorage.setItem(key(), JSON.stringify(tags.value)) }
  function clear() { sessionStorage.removeItem(key()); tags.value = [home] }
  return { tags, restore, add, close, closeOthers, clear }
})
