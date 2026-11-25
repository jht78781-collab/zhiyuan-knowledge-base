<script setup>
import { nextTick, onMounted, ref } from 'vue'
import Icon from './Icon.vue'
defineProps({ title: String, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref()
onMounted(async () => {
  await nextTick()
  dialog.value?.showModal()
})
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="modal" aria-labelledby="modal-title" @cancel.prevent="!busy && emit('close')">
      <div class="modal-head">
        <h2 id="modal-title">{{ title }}</h2>
        <button class="icon-btn" type="button" aria-label="关闭" :disabled="busy" @click="emit('close')"><Icon name="close" /></button>
      </div>
      <slot />
    </dialog>
  </Teleport>
</template>
