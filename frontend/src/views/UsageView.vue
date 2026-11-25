<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import Icon from '../components/Icon.vue'
import { api } from '../api.js'

const summary = ref(null)
const filter = reactive({ from: '', to: '' })
const loading = ref(true)
const error = ref('')
const number = value => value == null ? '—' : new Intl.NumberFormat('zh-CN').format(value)
const cards = computed(() => summary.value ? [
  { title: '调用次数', value: number(summary.value.calls), icon: 'chat', detail: summary.value.running + ' 次处理中' },
  { title: '成功响应', value: number(summary.value.succeeded), icon: 'spark', detail: summary.value.failed + ' 次失败' },
  { title: '平均响应时间', value: number(summary.value.averageMs), unit: summary.value.averageMs == null ? '' : 'ms',
    icon: 'chart', detail: summary.value.timed + ' 次有耗时记录' },
  { title: '已知总 Token', value: number(summary.value.totalTokens), icon: 'books', detail: summary.value.missingTotalTokens + ' 次缺少总用量' }
] : [])

async function load() {
  loading.value = true
  error.value = ''
  const params = new URLSearchParams()
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  try {
    summary.value = await api('/admin/statistics?' + params)
    filter.from = summary.value.from
    filter.to = summary.value.to
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <section class="content">
    <div class="page-head"><div><h1>用量统计</h1><p>查看问答调用、响应耗时与模型返回的实际 Token 用量。</p></div></div>
    <form class="panel inline-form filters" @submit.prevent="load">
      <label>开始日期<input v-model="filter.from" type="date" :max="filter.to"></label>
      <label>结束日期<input v-model="filter.to" type="date" :min="filter.from"></label>
      <button class="button primary" :disabled="loading">{{ loading ? '正在查询…' : '查询' }}</button>
      <small v-if="summary" class="muted">时区 {{ summary.zone }}</small>
    </form>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <template v-if="summary">
      <div class="metric-grid">
        <article v-for="item in cards" :key="item.title" class="panel metric">
          <span class="glyph small-glyph"><Icon :name="item.icon" :size="18" /></span>
          <h2>{{ item.title }}</h2><strong>{{ item.value }} <small v-if="item.unit">{{ item.unit }}</small></strong><p>{{ item.detail }}</p>
        </article>
      </div>
      <div class="panel data-panel">
        <div class="panel-head"><h2>Token 明细</h2></div>
        <dl class="token-breakdown"><div><dt>已知输入 Token</dt><dd>{{ number(summary.inputTokens) }}</dd></div>
          <div><dt>已知输出 Token</dt><dd>{{ number(summary.outputTokens) }}</dd></div>
          <div><dt>已知总 Token</dt><dd>{{ number(summary.totalTokens) }}</dd></div></dl>
        <p class="table-note">仅汇总 Dify 返回的数值。「—」表示所选时段无该项记录。</p>
      </div>
    </template>
  </section>
</template>
