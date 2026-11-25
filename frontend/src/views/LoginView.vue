<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../session.js'
import Icon from '../components/Icon.vue'

const form = reactive({ username: '', password: '' })
const router = useRouter()
const busy = ref(false)
const error = ref('')

async function submit() {
  busy.value = true
  error.value = ''
  try {
    await login(form)
    await router.replace({ name: 'knowledge' })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
    form.password = ''
  }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-side">
      <div class="brand"><span class="brand-mark"><Icon name="brand" :size="22" /></span><span>知源<span class="brand-sub">企业知识库</span></span></div>
      <div class="login-intro">
        <h1>向企业资料<br>直接提问<span>。</span></h1>
        <p>打开授权的知识库，查找答案和引用原文。</p>
      </div>
      <div class="login-art" aria-hidden="true">
        <div class="login-orbit"></div>
        <div class="art-card art-doc"><Icon name="file" :size="44" /><i></i><i></i><i></i></div>
        <div class="art-card art-chat"><Icon name="spark" :size="36" /><i></i><i></i></div>
      </div>
    </section>
    <section class="login-main">
      <form class="form-stack login-form" @submit.prevent="submit">
        <div class="form-title"><h2>登录工作台</h2><p>使用管理员分配的账号访问知识库。</p></div>
        <label>账号<input v-model="form.username" required maxlength="64" autocomplete="username" placeholder="输入账号"></label>
        <label>密码<input v-model="form.password" required type="password" maxlength="72" autocomplete="current-password" placeholder="输入密码"></label>
        <p v-if="error" class="notice error" role="alert">{{ error }}</p>
        <button class="button primary login-submit" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}<Icon name="arrow" :size="18" /></button>
      </form>
    </section>
  </main>
</template>
