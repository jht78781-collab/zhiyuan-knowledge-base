<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import PageNav from '../components/PageNav.vue'
import { api } from '../api.js'
import { user } from '../session.js'

const router = useRouter()
const view = ref({ items: [], pages: 0 })
const loading = ref(true)
const error = ref('')
const showCreate = ref(false)
const saving = ref(false)
const createError = ref('')
const form = reactive({ name: '', description: '', datasetId: '', appAlias: '' })

async function load(page = 0) {
  loading.value = true
  error.value = ''
  try { view.value = await api('/knowledge-overview?page=' + page) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { name: '', description: '', datasetId: '', appAlias: '' })
  createError.value = ''
  showCreate.value = true
}

async function create() {
  saving.value = true
  createError.value = ''
  try {
    const kb = await api('/admin/knowledge-bases', { method: 'POST', body: form })
    showCreate.value = false
    await router.push({ name: 'knowledgeDetail', params: { id: kb.id } })
  } catch (e) { createError.value = e.message }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="content">
    <div class="page-head">
      <div><h1>知识库</h1><p>选择一个知识库查看文档，或根据资料提问。</p></div>
      <button v-if="user?.role === 'ADMIN'" type="button" class="button primary" @click="openCreate"><Icon name="plus" :size="18" />新建知识库</button>
    </div>
    <p v-if="loading" class="notice" role="status">正在载入知识库…</p>
    <p v-else-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load()">重新加载</button></p>
    <div v-else-if="!view.items.length" class="empty panel">
      <span class="glyph"><Icon name="books" :size="25" /></span>
      <h2>{{ user?.role === 'ADMIN' ? '还没有知识库' : '暂无授权知识库' }}</h2>
      <p>{{ user?.role === 'ADMIN' ? '创建知识库后，即可上传并索引文档。' : '请联系管理员为你的账号分配访问权限。' }}</p>
      <button v-if="user?.role === 'ADMIN'" class="button primary" @click="openCreate">创建第一个知识库<Icon name="arrow" :size="17" /></button>
    </div>
    <div v-else class="kb-grid">
      <article v-for="{ knowledge: kb, availableDocuments } in view.items" :key="kb.id" class="kb-card panel">
        <div class="kb-card-head"><span class="glyph"><Icon name="books" :size="22" /></span>
          <span class="tag" :class="kb.status === 'DELETING' ? 'warning' : kb.difyConfigured ? 'success' : 'neutral'">
            {{ kb.status === 'DELETING' ? '清理中' : kb.difyConfigured ? '已配置' : '待配置 Dify' }}
          </span>
        </div>
        <h2>{{ kb.name }}</h2>
        <p class="clamp muted">{{ kb.description || '暂无简介' }}</p>
        <div class="kb-card-foot">
          <span class="muted"><Icon name="file" :size="15" />{{ availableDocuments }} 份可问答文档</span>
          <RouterLink class="inline-link" :to="{ name: 'knowledgeDetail', params: { id: kb.id } }">打开<Icon name="arrow" :size="16" /></RouterLink>
        </div>
      </article>
    </div>
    <PageNav :data="view" @change="load" />
    <Modal v-if="showCreate" title="新建知识库" :busy="saving" @close="showCreate = false">
      <form class="form-stack" @submit.prevent="create">
        <label>知识库名称<input v-model.trim="form.name" required maxlength="100" placeholder="例如：员工制度"></label>
        <label>简介<textarea v-model.trim="form.description" maxlength="1000" rows="3" placeholder="这个知识库包含哪些资料？"></textarea></label>
        <label>Dify 知识库 ID<input v-model.trim="form.datasetId" required maxlength="36" placeholder="在 Dify 中创建知识库后复制 ID"></label>
        <label>Dify 应用别名<input v-model.trim="form.appAlias" required maxlength="64" placeholder="例如：hr">
          <small>别名须与服务端匹配。每个知识库使用一个独立的 Dify 应用。</small>
        </label>
        <p v-if="createError" class="notice error" role="alert">{{ createError }}</p>
        <div class="modal-actions"><button type="button" class="button" :disabled="saving" @click="showCreate = false">取消</button><button class="button primary" :disabled="saving">{{ saving ? '正在创建…' : '创建知识库' }}</button></div>
      </form>
    </Modal>
  </section>
</template>
