<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Box, Document, Expand, Fold } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { useTagsStore } from '../stores/tags'

const route = useRoute()
const router = useRouter()
const tags = useTagsStore()
const collapsed = ref(false)
const title = computed(() => String(route.meta.title ?? '设备台账'))
onMounted(() => tags.restore())
watch(() => route.path, path => {
  if (path === '/ledgers') tags.add({ path, fullPath: path, title: '台账列表', name: 'ledgers' })
}, { immediate: true })
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="brand" :title="collapsed ? '应急宝台账' : undefined" aria-label="应急宝台账">
        <span class="brand-icon"><el-icon><Box /></el-icon></span>
        <span v-if="!collapsed" class="brand-copy"><strong>应急宝台账</strong><small>设备资产管理平台</small></span>
      </div>
      <div v-if="!collapsed" class="nav-label">工作空间</div>
      <el-menu class="side-menu" router :default-active="route.path" :collapse="collapsed" :collapse-transition="false" unique-opened>
        <el-sub-menu index="ledger-group">
          <template #title><el-icon><Document /></el-icon><span>设备台账</span></template>
          <el-menu-item index="/ledgers">进出申请台账</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div v-if="!collapsed" class="sidebar-foot"><span class="status-dot"></span> 共享台账 <span class="version">V2.0</span></div>
    </aside>
    <div class="main-area">
      <header class="header-bar">
        <div class="header-left">
          <button class="icon-button collapse-button" :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'" @click="collapsed = !collapsed"><el-icon size="19"><Expand v-if="collapsed" /><Fold v-else /></el-icon></button>
          <span class="header-divider"></span>
          <span class="breadcrumb">工作台</span><span class="crumb-arrow">/</span><strong>{{ title }}</strong>
        </div>
        <div class="header-right">
          <span class="environment-pill"><span class="status-dot"></span> 共享工作台</span>
        </div>
      </header>
      <div class="tags-bar"><button v-for="tag in tags.tags" :key="tag.path" class="tag-item" :class="{ active: route.path === tag.path }" @click="router.push(tag.fullPath)"><span class="tag-dot"></span>{{ tag.title }}</button></div>
      <main class="page-body"><router-view /></main>
    </div>
  </div>
</template>
