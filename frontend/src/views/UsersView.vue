<script setup>
import { onMounted, reactive, ref } from 'vue'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import PageNav from '../components/PageNav.vue'
import { api } from '../api.js'

const users = ref({ items: [], page: 0, pages: 0 })
const loading = ref(true)
const error = ref('')
const formError = ref('')
const working = ref(false)
const selected = ref(null)
const visible = ref(false)
const form = reactive({ username: '', displayName: '', password: '', role: 'USER', enabled: true })

async function list(page = users.value.page) {
  loading.value = true
  error.value = ''
  try { users.value = await api('/admin/users?page=' + page) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function open(item = null) {
  selected.value = item
  Object.assign(form, item ? { ...item, password: '' } : { username: '', displayName: '', password: '', role: 'USER', enabled: true })
  formError.value = ''
  visible.value = true
}

async function save() {
  formError.value = ''
  working.value = true
  try {
    if (selected.value) {
      await api('/admin/users/' + selected.value.id, { method: 'PUT', body: {
        role: form.role, enabled: form.enabled, password: form.password || null
      } })
    } else {
      const { username, displayName, password, role } = form
      await api('/admin/users', { method: 'POST', body: { username, displayName, password, role } })
    }
    visible.value = false
    form.password = ''
    await list()
  } catch (e) { formError.value = e.message }
  finally { working.value = false }
}

onMounted(() => list())
</script>

<template>
  <section class="content">
    <div class="page-head">
      <div><h1>用户管理</h1><p>创建员工账号，调整角色、启用状态与密码。</p></div>
      <button class="button primary" type="button" @click="open()"><Icon name="plus" :size="18" />新增用户</button>
    </div>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="list()">重新加载</button></p>
    <div class="panel data-panel">
      <div class="panel-head"><h2>账号列表</h2></div>
      <p v-if="loading" class="empty-line" role="status">正在载入账号…</p>
      <div v-else class="table-wrap"><table>
        <thead><tr><th>姓名</th><th>账号</th><th>角色</th><th>状态</th><th>操作</th></tr></thead>
        <tbody><tr v-for="item in users.items" :key="item.id">
          <td><strong>{{ item.displayName }}</strong></td><td>{{ item.username }}</td>
          <td>{{ item.role === 'ADMIN' ? '系统管理员' : '普通用户' }}</td>
          <td><span class="tag" :class="item.enabled ? 'success' : 'neutral'">{{ item.enabled ? '启用' : '停用' }}</span></td>
          <td><button class="text-button" type="button" @click="open(item)">编辑</button></td>
        </tr></tbody>
      </table></div>
      <PageNav :data="users" @change="list" />
    </div>
    <Modal v-if="visible" :title="selected ? '编辑 ' + selected.username : '新增用户'" :busy="working" @close="visible = false">
      <form class="form-stack" @submit.prevent="save">
        <label v-if="!selected">账号<input v-model.trim="form.username" required minlength="3" maxlength="64" autocomplete="off"></label>
        <label v-if="!selected">姓名<input v-model.trim="form.displayName" required maxlength="80"></label>
        <label>角色<select v-model="form.role"><option value="USER">普通用户</option><option value="ADMIN">系统管理员</option></select></label>
        <label v-if="selected">状态<select v-model="form.enabled"><option :value="true">启用</option><option :value="false">停用</option></select></label>
        <label>{{ selected ? '重置密码' : '初始密码' }}
          <input v-model="form.password" :required="!selected" type="password" minlength="12" maxlength="72" autocomplete="new-password"
            :placeholder="selected ? '留空则保留当前密码' : '至少 12 个字符'">
        </label>
        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="modal-actions"><button type="button" class="button" :disabled="working" @click="visible = false">取消</button><button class="button primary" :disabled="working">保存</button></div>
      </form>
    </Modal>
  </section>
</template>
