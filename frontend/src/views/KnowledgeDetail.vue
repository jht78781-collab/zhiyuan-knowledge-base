<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import PageNav from '../components/PageNav.vue'
import { api, dateTime, docStatus, documentUrl } from '../api.js'
import { user } from '../session.js'

const route = useRoute()
const router = useRouter()
const endpoint = computed(() => '/knowledge-bases/' + route.params.id)
const blankPage = () => ({ items: [], page: 0, pages: 0 })
const kb = ref(null)
const docs = ref(blankPage())
const members = ref(blankPage())
const panel = ref('docs')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const drag = ref(false)
const files = ref()
const modal = ref(null)
const modalError = ref('')
const settings = reactive({ name: '', description: '' })
const grant = reactive({ username: '', permission: 'VIEWER' })
const active = computed(() => kb.value?.status === 'ACTIVE')
let timer
let epoch = 0
const pending = doc => ['UPLOADING', 'QUEUED', 'INDEXING', 'DELETING'].includes(doc.status)

async function load() {
  const current = ++epoch
  loading.value = true
  error.value = ''
  notice.value = ''
  docs.value = blankPage()
  members.value = blankPage()
  panel.value = 'docs'
  kb.value = null
  try {
    const [scope, documents] = await Promise.all([api(endpoint.value), api(endpoint.value + '/documents')])
    if (current !== epoch) return
    kb.value = scope
    Object.assign(settings, { name: scope.name, description: scope.description })
    docs.value = documents
  } catch (e) { if (current === epoch) error.value = e.message }
  finally { if (current === epoch) loading.value = false }
}

async function refreshDocs(page = docs.value.page, silent = false) {
  const current = epoch
  try {
    const result = await api(endpoint.value + '/documents?page=' + page)
    if (current !== epoch) return
    if (!result.items.length && result.page > 0) return refreshDocs(result.page - 1, silent)
    docs.value = result
  } catch (e) {
    if (current !== epoch) return
    if (kb.value?.status === 'DELETING' && e.status === 404) return router.replace({ name: 'knowledge' })
    if (!silent) error.value = e.message
  }
}

async function refreshMembers(page = members.value.page) {
  const current = epoch
  try {
    const result = await api(endpoint.value + '/members?page=' + page)
    if (current !== epoch) return
    if (!result.items.length && result.page > 0) return refreshMembers(result.page - 1)
    members.value = result
  } catch (e) { if (current === epoch) error.value = e.message }
}

function tab(name) {
  panel.value = name
  error.value = ''
  notice.value = ''
  if (name === 'members') refreshMembers(0)
}

async function upload(list) {
  if (!list?.length || busy.value || !active.value || !kb.value.canManage) return
  busy.value = true
  error.value = ''
  notice.value = ''
  const location = endpoint.value
  const current = epoch
  const failures = []
  let saved = 0
  for (const file of list) {
    if (current !== epoch) break
    const body = new FormData()
    body.append('file', file)
    try {
      const document = await api(location + '/documents', { method: 'POST', body })
      if (document.error) failures.push(file.name + '：' + document.error)
      else saved++
    } catch (e) { failures.push(file.name + '：' + e.message) }
  }
  if (current === epoch) {
    if (saved) notice.value = '已保存 ' + saved + ' 个文件，正在等待索引。'
    if (failures.length) error.value = failures.join(' ')
    await refreshDocs(0)
  }
  if (files.value) files.value.value = ''
  busy.value = false
}

function dropped(event) {
  drag.value = false
  upload(event.dataTransfer.files)
}

async function retry(doc) {
  busy.value = true
  error.value = ''
  try {
    await api(endpoint.value + '/documents/' + doc.id + '/retry', { method: 'POST' })
    await refreshDocs()
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}

async function edit() {
  busy.value = true
  notice.value = error.value = ''
  try {
    kb.value = await api(endpoint.value, { method: 'PUT', body: settings })
    notice.value = '已保存知识库设置。'
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}

async function grantAccess() {
  busy.value = true
  notice.value = error.value = ''
  try {
    await api(endpoint.value + '/members', { method: 'POST', body: grant })
    grant.username = ''
    notice.value = '已保存成员权限。'
    await refreshMembers(0)
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}

function confirm(type, item) {
  modalError.value = ''
  modal.value = { type, item }
}

async function remove() {
  busy.value = true
  modalError.value = ''
  const action = modal.value
  try {
    if (action.type === 'document') {
      await api(endpoint.value + '/documents/' + action.item.id, { method: 'DELETE' })
      await refreshDocs()
    } else if (action.type === 'member') {
      await api(endpoint.value + '/members/' + action.item.userId, { method: 'DELETE' })
      await refreshMembers()
    } else {
      await api('/admin' + endpoint.value, { method: 'DELETE' })
      await router.replace({ name: 'knowledge' })
    }
    modal.value = null
  } catch (e) { modalError.value = e.message }
  finally { busy.value = false }
}

function sizeLabel(size) {
  if (size < 1024) return size + ' B'
  if (size < 1048576) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1048576).toFixed(1) + ' MB'
}

watch(() => route.params.id, id => { if (id) load() }, { immediate: true })
onMounted(() => {
  timer = setInterval(() => {
    if (!loading.value && !busy.value && panel.value === 'docs' &&
        (docs.value.items.some(pending) || kb.value?.status === 'DELETING')) refreshDocs(docs.value.page, true)
  }, 4000)
})
onBeforeUnmount(() => { clearInterval(timer); epoch++ })
</script>

<template>
  <section class="content">
    <RouterLink class="inline-link return-link" :to="{ name: 'knowledge' }"><Icon name="back" :size="17" />知识库列表</RouterLink>
    <p v-if="loading" class="notice" role="status">正在载入知识库…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <template v-if="kb">
      <div class="page-head">
        <div><h1>{{ kb.name }}</h1><p>{{ kb.description || '查看知识库文档及索引状态。' }}</p></div>
        <RouterLink v-if="active" class="button primary" :to="{ name: 'chat', query: { kb: kb.id } }"><Icon name="chat" :size="18" />开始问答</RouterLink>
      </div>
      <p v-if="!active" class="notice warning">知识库已停止访问，正在清理文件和索引。清理失败时可在文档列表重试。</p>
      <p v-else-if="!kb.difyConfigured && kb.canManage" class="notice warning">Dify 尚未配置完整。填写服务端知识库密钥及此应用别名后重启，再重试失败任务。</p>
      <div class="tabs" aria-label="知识库功能">
        <button :class="{ active: panel === 'docs' }" @click="tab('docs')">文档</button>
        <button v-if="kb.canManage && active" :class="{ active: panel === 'members' }" @click="tab('members')">成员权限</button>
        <button v-if="kb.canManage && active" :class="{ active: panel === 'settings' }" @click="tab('settings')">设置</button>
      </div>
      <p v-if="notice" class="notice ok" role="status">{{ notice }}</p>
      <section v-if="panel === 'docs'">
        <div v-if="kb.canManage && active" class="dropzone" :class="{ dragging: drag }"
          @dragover.prevent="drag = true" @dragleave.prevent="drag = false" @drop.prevent="dropped">
          <span class="glyph"><Icon name="upload" :size="23" /></span>
          <div><strong>添加原始文档</strong><p>拖放 PDF、DOCX 或 UTF-8 TXT，或点击选择文件。</p></div>
          <input ref="files" class="hidden" type="file" accept=".pdf,.docx,.txt" multiple @change="upload($event.target.files)">
          <button type="button" class="button" :disabled="busy" @click="files.click()">{{ busy ? '处理中…' : '选择文件' }}</button>
        </div>
        <div class="panel data-panel">
          <div class="panel-head"><h2>文档列表</h2><button class="icon-btn" type="button" aria-label="刷新文档" @click="refreshDocs()"><Icon name="refresh" :size="17" /></button></div>
          <p v-if="!docs.items.length" class="empty-line">暂无{{ kb.canManage ? '' : '可用' }}文档。</p>
          <div v-else class="table-wrap"><table>
            <thead><tr><th>文档名称</th><th>大小</th><th>状态</th><th>上传时间</th><th>操作</th></tr></thead>
            <tbody><tr v-for="doc in docs.items" :key="doc.id">
              <td><div class="file-name"><Icon name="file" :size="17" /><strong>{{ doc.name }}</strong></div><small v-if="doc.error" class="table-error">{{ doc.error }}</small></td>
              <td>{{ sizeLabel(doc.size) }}</td>
              <td><span class="tag" :class="doc.status === 'AVAILABLE' ? 'success' : doc.status.includes('FAILED') ? 'danger' : 'neutral'">{{ docStatus[doc.status] }}</span></td>
              <td class="nowrap">{{ dateTime(doc.createdAt) }}</td>
              <td class="cell-actions">
                <a v-if="doc.originalAvailable && active" class="inline-link" :href="documentUrl(kb.id, doc.id)">下载</a>
                <button v-if="kb.canManage && (doc.status === 'DELETE_FAILED' || (doc.status === 'FAILED' && doc.originalAvailable))"
                  type="button" class="text-button" :disabled="busy" @click="retry(doc)">重试</button>
                <button v-if="kb.canManage && active && !['DELETING', 'DELETE_FAILED'].includes(doc.status)" type="button"
                  class="text-button danger-text" :disabled="busy" @click="confirm('document', doc)">删除</button>
              </td>
            </tr></tbody>
          </table></div>
          <PageNav :data="docs" @change="refreshDocs" />
        </div>
      </section>
      <section v-else-if="panel === 'members'" class="panel data-panel">
        <div class="panel-head"><h2>成员权限</h2></div>
        <form class="inline-form" @submit.prevent="grantAccess">
          <label>账号<input v-model.trim="grant.username" required maxlength="64" placeholder="输入已创建的账号"></label>
          <label>权限<select v-model="grant.permission"><option value="VIEWER">查看与提问</option><option value="MANAGER">管理知识库</option></select></label>
          <button class="button primary" :disabled="busy">保存权限</button>
        </form>
        <p v-if="!members.items.length" class="empty-line">尚未分配成员。</p>
        <div v-else class="table-wrap"><table>
          <thead><tr><th>成员</th><th>账号</th><th>权限</th><th>状态</th><th>操作</th></tr></thead>
          <tbody><tr v-for="member in members.items" :key="member.userId">
            <td>{{ member.displayName }}</td><td>{{ member.username }}</td>
            <td>{{ member.permission === 'MANAGER' ? '管理知识库' : '查看与提问' }}</td>
            <td>{{ member.enabled ? '启用' : '停用' }}</td>
            <td><button type="button" class="text-button danger-text" :disabled="busy" @click="confirm('member', member)">撤销</button></td>
          </tr></tbody>
        </table></div>
        <PageNav :data="members" @change="refreshMembers" />
      </section>
      <section v-else class="panel settings">
        <form class="form-stack" @submit.prevent="edit">
          <h2>基本设置</h2>
          <label>知识库名称<input v-model.trim="settings.name" required maxlength="100"></label>
          <label>简介<textarea v-model.trim="settings.description" rows="4" maxlength="1000"></textarea></label>
          <dl v-if="kb.datasetId" class="binding"><dt>Dify 知识库 ID</dt><dd>{{ kb.datasetId }}</dd><dt>应用别名</dt><dd>{{ kb.appAlias }}</dd></dl>
          <div><button class="button primary" :disabled="busy">保存设置</button></div>
        </form>
        <div v-if="user?.role === 'ADMIN'" class="danger-zone">
          <div><h3>删除知识库</h3><p>成员将立即失去访问权限，关联文档随后进入清理。</p></div>
          <button type="button" class="button danger-btn" :disabled="busy" @click="confirm('knowledge', kb)">删除知识库</button>
        </div>
      </section>
    </template>
    <Modal v-if="modal" :title="modal.type === 'member' ? '撤销成员权限' : '确认删除'" :busy="busy" @close="modal = null">
      <p v-if="modal.type === 'member'">撤销 {{ modal.item.displayName }} 的权限后，此成员将无法访问相关文档及会话。</p>
      <p v-else>“{{ modal.item.name }}”将立即停止访问，系统随后清理应用管理的文件与索引。</p>
      <p v-if="modalError" class="notice error" role="alert">{{ modalError }}</p>
      <div class="modal-actions"><button class="button" :disabled="busy" @click="modal = null">取消</button><button class="button danger-btn" :disabled="busy" @click="remove">确认</button></div>
    </Modal>
  </section>
</template>
