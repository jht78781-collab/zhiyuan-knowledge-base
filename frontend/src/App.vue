<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './components/Icon.vue'
import { logout, user } from './session.js'

const route = useRoute()
const router = useRouter()
const items = computed(() => [
  { name: 'knowledge', label: '知识库', icon: 'books' },
  { name: 'chat', label: '智能问答', icon: 'chat' },
  ...(user.value?.role === 'ADMIN' ? [
    { name: 'users', label: '用户管理', icon: 'users' },
    { name: 'usage', label: '用量统计', icon: 'chart' }
  ] : [])
])
const signingOut = ref(false)
const logoutError = ref('')
const active = name => route.name === name || name === 'knowledge' && route.name === 'knowledgeDetail'

async function exit() {
  signingOut.value = true
  logoutError.value = ''
  try {
    await logout()
    await router.replace({ name: 'login' })
  } catch (error) {
    logoutError.value = error.message
  } finally { signingOut.value = false }
}
</script>

<template>
  <RouterView v-if="route.name === 'login' || !user" />
  <div v-else class="app-frame">
    <aside class="sidebar">
      <RouterLink class="brand" :to="{ name: 'knowledge' }">
        <span class="brand-mark"><Icon name="brand" :size="22" /></span>
        <span>知源<span class="brand-sub">企业知识库</span></span>
      </RouterLink>
      <nav class="navigation" aria-label="主导航">
        <RouterLink v-for="item in items" :key="item.name" :to="{ name: item.name }"
          :class="{ selected: active(item.name) }"><Icon :name="item.icon" :size="19" />{{ item.label }}</RouterLink>
      </nav>
      <div class="profile">
        <span class="avatar">{{ user.displayName.slice(0, 1) }}</span>
        <div class="profile-name"><strong>{{ user.displayName }}</strong><small>{{ user.role === 'ADMIN' ? '系统管理员' : '成员' }}</small></div>
        <button type="button" class="icon-btn logout" aria-label="退出登录" :disabled="signingOut" @click="exit"><Icon name="exit" :size="18" /></button>
      </div>
    </aside>
    <main class="workspace">
      <header class="topbar"><span>工作台</span><Icon name="chevron" :size="14" /><strong>{{ route.meta.title }}</strong></header>
      <p v-if="logoutError" class="notice error global-error" role="alert">{{ logoutError }}</p>
      <RouterView />
    </main>
  </div>
</template>
