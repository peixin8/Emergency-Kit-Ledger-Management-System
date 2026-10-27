import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', redirect: '/ledgers' },
    { path: '/', component: () => import('../layouts/MainLayout.vue'), children: [
      { path: '', redirect: '/ledgers' },
      { path: 'ledgers', name: 'ledgers', component: () => import('../views/LedgerListView.vue'), meta: { title: '台账列表' } }
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/ledgers' }
  ]
})
export default router
