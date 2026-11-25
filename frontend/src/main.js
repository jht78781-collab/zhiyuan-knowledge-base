import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import { clearSession, loadSession, user } from './session.js'
import './style.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/knowledge' },
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('./views/KnowledgeView.vue'), meta: { title: '知识库' } },
    { path: '/knowledge/:id', name: 'knowledgeDetail', component: () => import('./views/KnowledgeDetail.vue'), meta: { title: '知识库详情' } },
    { path: '/chat', name: 'chat', component: () => import('./views/ChatView.vue'), meta: { title: '智能问答' } },
    { path: '/users', name: 'users', component: () => import('./views/UsersView.vue'), meta: { title: '用户管理', admin: true } },
    { path: '/usage', name: 'usage', component: () => import('./views/UsageView.vue'), meta: { title: '用量统计', admin: true } },
    { path: '/:pathMatch(.*)*', redirect: '/knowledge' }
  ]
})

router.beforeEach(async to => {
  await loadSession()
  if (!user.value && to.name !== 'login') return { name: 'login' }
  if (user.value && (to.name === 'login' || (to.meta.admin && user.value.role !== 'ADMIN'))) return { name: 'knowledge' }
})

window.addEventListener('session-expired', () => {
  clearSession()
  if (router.currentRoute.value.name !== 'login') router.replace({ name: 'login' })
})

createApp(App).use(router).mount('#app')
