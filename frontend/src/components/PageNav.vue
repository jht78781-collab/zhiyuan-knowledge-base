<script setup>
import Icon from './Icon.vue'
defineProps({ data: { type: Object, required: true } })
defineEmits(['change'])
</script>

<template>
  <div v-if="data.pages > 1" class="pagination" aria-label="分页">
    <span>第 {{ data.page + 1 }} / {{ data.pages }} 页 · 共 {{ data.total }} 条</span>
    <button class="icon-btn" type="button" aria-label="上一页" :disabled="data.page === 0" @click="$emit('change', data.page - 1)"><Icon name="back" :size="16" /></button>
    <button class="icon-btn" type="button" aria-label="下一页" :disabled="data.page + 1 >= data.pages" @click="$emit('change', data.page + 1)"><Icon name="arrow" :size="16" /></button>
  </div>
</template>
