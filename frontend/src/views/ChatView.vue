<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from '../components/Icon.vue'
import Modal from '../components/Modal.vue'
import PageNav from '../components/PageNav.vue'
import { api, dateTime, documentUrl } from '../api.js'

const route = useRoute()
const router = useRouter()
const items = ref([])
const kb = ref(null)
const selected = ref('')
const conversations = ref({ items: [], page: 0, pages: 0 })
const history = ref(null)
const draft = ref('')
const sent = ref('')
const sending = ref(false)
const loading = ref(true)
const error = ref('')
const deleteError = ref('')
const deleteCandidate = ref(null)
const deleting = ref(false)
const thread = ref()
const endpoint = computed(() => '/knowledge-bases/' + kb.value?.id + '/conversations')
let epoch = 0
let lastRequest = null

async function init() {
  const current = ++epoch
  if (route.name !== 'chat') return
  loading.value = true
  error.value = ''
  kb.value = null
  selected.value = ''
  history.value = null
  sent.value = ''
  conversations.value = { items: [], page: 0, pages: 0 }
  draft.value = ''
  try {
    const visible = await api('/knowledge-bases')
    if (current !== epoch) return
    items.value = visible.items.filter(item => item.status === 'ACTIVE')
    const id = Number(route.query.kb)
    if (id && !items.value.some(item => item.id === id)) {
      const detail = await api('/knowledge-bases/' + id)
      if (current !== epoch) return
      if (detail.status === 'ACTIVE') items.value.unshift(detail)
    }
    kb.value = items.value.find(item => item.id === id) || items.value[0] || null
    if (!kb.value) return
    selected.value = String(kb.value.id)
    await refreshList(0)
    if (current !== epoch) return
    const chat = Number(route.query.chat)
    if (chat) await read(chat, 0, true)
  } catch (e) { if (current === epoch) error.value = e.message }
  finally { if (current === epoch) loading.value = false }
}

async function refreshList(page = conversations.value.page) {
  const current = epoch
  try {
    const result = await api(endpoint.value + '?page=' + page)
    if (current !== epoch) return
    if (!result.items.length && result.page > 0) return refreshList(result.page - 1)
    conversations.value = result
  } catch (e) { if (current === epoch) error.value = e.message }
}

async function read(id, page = 0, jump = false) {
  const current = epoch
  error.value = ''
  try {
    const result = await api(endpoint.value + '/' + id + '?page=' + page)
    if (current !== epoch) return
    history.value = result
    if (jump) await scrollToEnd()
  } catch (e) { if (current === epoch) error.value = e.message }
}

function selectScope() {
  router.push({ name: 'chat', query: { kb: selected.value } })
}

function selectConversation(item) {
  if (sending.value) return
  draft.value = ''
  lastRequest = null
  read(item.id, 0, true)
}

function newChat() {
  if (sending.value) return
  history.value = null
  draft.value = ''
  error.value = ''
  lastRequest = null
}

async function ask() {
  const text = draft.value.trim()
  if (!text || sending.value || !kb.value) return
  const current = epoch
  const path = endpoint.value
  sending.value = true
  error.value = ''
  sent.value = text
  draft.value = ''
  await scrollToEnd()
  try {
    let conversation = history.value?.conversation
    if (!conversation) {
      conversation = await api(path, { method: 'POST' })
      if (current !== epoch) return
      history.value = { conversation, messages: { items: [], page: 0, pages: 0 } }
    }
    if (!lastRequest || lastRequest.conversation !== conversation.id || lastRequest.text !== text) {
      lastRequest = { conversation: conversation.id, text, id: crypto.randomUUID() }
    }
    const result = await api(path + '/' + conversation.id + '/messages', {
      method: 'POST', body: { requestId: lastRequest.id, question: text }
    })
    if (current !== epoch) return
    if (result.status !== 'PENDING') lastRequest = null
    sent.value = ''
    await read(conversation.id, 0, true)
    await refreshList(0)
  } catch (e) {
    if (current !== epoch) return
    error.value = e.message
    draft.value = text
  } finally {
    if (current === epoch) sent.value = ''
    sending.value = false
  }
}

function confirmDelete(item) {
  deleteError.value = ''
  deleteCandidate.value = item
}

async function remove() {
  deleting.value = true
  try {
    const id = deleteCandidate.value.id
    await api(endpoint.value + '/' + id, { method: 'DELETE' })
    if (history.value?.conversation.id === id) newChat()
    deleteCandidate.value = null
    await refreshList()
  } catch (e) { deleteError.value = e.message }
  finally { deleting.value = false }
}

async function scrollToEnd() {
  await nextTick()
  if (thread.value) thread.value.scrollTop = thread.value.scrollHeight
}

watch(() => [route.name, route.query.kb, route.query.chat], init, { immediate: true })
onBeforeUnmount(() => { epoch++ })
</script>

<template>
  <section class="content chat-content">
    <div class="page-head">
      <div><h1>智能问答</h1><p>围绕一个知识库提问，并核对回答引用的文档。</p></div>
      <label v-if="items.length" class="scope-select">当前知识库
        <select v-model="selected" :disabled="sending" @change="selectScope"><option v-for="item in items" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select>
      </label>
    </div>
    <p v-if="loading" class="notice" role="status">正在载入问答工作台…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div v-if="!loading && !kb && !error" class="panel empty">
      <span class="glyph"><Icon name="books" :size="25" /></span><h2>暂无可用知识库</h2><p>选择有访问权限的知识库后，即可开始问答。</p>
      <RouterLink class="button" :to="{ name: 'knowledge' }">查看知识库<Icon name="arrow" :size="17" /></RouterLink>
    </div>
    <div v-if="kb" class="chat-layout panel">
      <aside class="chat-history">
        <button class="button primary" type="button" :disabled="sending" @click="newChat"><Icon name="plus" :size="18" />新会话</button>
        <h2>会话历史</h2>
        <p v-if="!conversations.items.length" class="muted small">暂无历史会话</p>
        <div class="history-list">
          <div v-for="session in conversations.items" :key="session.id" class="history-row" :class="{ active: history?.conversation.id === session.id }">
            <button type="button" class="thread-select" :disabled="sending" @click="selectConversation(session)">
              <strong>{{ session.title }}</strong><small>{{ dateTime(session.updatedAt) }}</small>
            </button>
            <button class="icon-btn" type="button" :aria-label="'删除会话 ' + session.title" :disabled="sending" @click="confirmDelete(session)"><Icon name="trash" :size="15" /></button>
          </div>
        </div>
        <PageNav :data="conversations" @change="refreshList" />
      </aside>
      <div class="chat-main">
        <div class="chat-title"><span class="glyph small-glyph"><Icon name="spark" :size="19" /></span><div><strong>{{ history?.conversation.title || '新会话' }}</strong><small>{{ kb.name }}</small></div></div>
        <div ref="thread" class="thread" role="log" aria-label="问答消息" aria-live="polite">
          <div v-if="!history?.messages.items.length && !sent" class="chat-empty">
            <span class="glyph"><Icon name="spark" :size="26" /></span>
            <h2>你想了解什么？</h2><p>提出与「{{ kb.name }}」资料有关的问题。</p>
          </div>
          <PageNav v-if="history" :data="history.messages" @change="page => read(history.conversation.id, page)" />
          <article v-for="message in [...(history?.messages.items || [])].reverse()" :key="message.id" class="turn">
            <div class="bubble question">{{ message.question }}</div>
            <div class="answer-row">
              <span class="glyph small-glyph"><Icon name="spark" :size="17" /></span>
              <div class="answer-content">
                <div v-if="message.status === 'FAILED'" class="notice error">{{ message.error }}</div>
                <div v-else-if="message.status === 'PENDING'" class="bubble answer">这轮仍在处理。<button class="text-button" @click="read(history.conversation.id, 0, true)">刷新</button></div>
                <div v-else class="bubble answer">{{ message.answer }}</div>
                <details v-if="message.sources.length" class="citations" open>
                  <summary><Icon name="link" :size="16" />参考来源（{{ message.sources.length }}）</summary>
                  <div v-for="(source, index) in message.sources" :key="index" class="citation">
                    <a v-if="source.originalAvailable" class="inline-link" :href="documentUrl(kb.id, source.id)"><Icon name="file" :size="15" />{{ source.name }}</a>
                    <span v-else>{{ source.name }} · 原文已移除</span>
                    <p v-if="source.excerpt">{{ source.excerpt }}</p>
                  </div>
                </details>
                <small v-else-if="message.status === 'NO_EVIDENCE'" class="muted">未检索到可验证的来源</small>
              </div>
            </div>
          </article>
          <div v-if="sent" class="turn"><div class="bubble question">{{ sent }}</div><div class="answer-row"><span class="glyph small-glyph"><Icon name="spark" :size="17" /></span><div class="bubble answer" role="status">正在查找相关资料…</div></div></div>
        </div>
        <form class="composer" @submit.prevent="ask">
          <label for="question" class="sr-only">问题</label>
          <textarea id="question" v-model="draft" required maxlength="2000" rows="2" placeholder="输入问题…" @keydown.ctrl.enter.prevent="ask" @keydown.meta.enter.prevent="ask"></textarea>
          <button class="button primary" type="submit" :disabled="sending || !draft.trim()"><Icon name="send" :size="17" />发送</button>
        </form>
      </div>
    </div>
    <Modal v-if="deleteCandidate" title="删除会话" :busy="deleting" @close="deleteCandidate = null">
      <p>“{{ deleteCandidate.title }}”将从你的会话历史移除。</p>
      <p v-if="deleteError" class="notice error" role="alert">{{ deleteError }}</p>
      <div class="modal-actions"><button class="button" :disabled="deleting" @click="deleteCandidate = null">取消</button><button class="button danger-btn" :disabled="deleting" @click="remove">删除</button></div>
    </Modal>
  </section>
</template>
