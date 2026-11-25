<script setup>
defineProps({ name: { type: String, required: true }, size: { type: Number, default: 20 } })
const paths = {
  brand: 'M12 2 3 7l9 5 9-5-9-5ZM3 12l9 5 9-5M3 17l9 5 9-5',
  books: 'M4 19V5a2 2 0 0 1 2-2h13v14H6a2 2 0 1 0 0 4h13M8 7h7M8 11h5',
  chat: 'M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l1.7-4.2A8.5 8.5 0 1 1 21 11.5Z M8 10h8M8 14h5',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-7.87a4 4 0 0 1 0 7.75',
  chart: 'M3 3v18h18M8 16v-3m5 3V8m5 8V5',
  plus: 'M12 5v14M5 12h14',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  back: 'M19 12H5m6 6-6-6 6-6',
  chevron: 'm9 18 6-6-6-6',
  file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Zm0 0v6h6M8 13h8m-8 4h6',
  upload: 'M12 17V4m-5 5 5-5 5 5M5 17v3h14v-3',
  link: 'M10 13a4 4 0 0 0 6 .4l3-3a4 4 0 0 0-5.7-5.7l-1.5 1.5M14 11a4 4 0 0 0-6-.4l-3 3a4 4 0 1 0 5.7 5.7l1.5-1.5',
  exit: 'M10 17l5-5-5-5m5 5H3m9-8h6a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6',
  spark: 'M12 2c0 6-4 10-10 10 6 0 10 4 10 10 0-6 4-10 10-10-6 0-10-4-10-10Z',
  close: 'm6 6 12 12M18 6 6 18',
  send: 'm22 2-7 20-4-9-9-4 20-7ZM22 2 11 13',
  trash: 'M3 6h18M9 6V3h6v3m4 0v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6m5 4v7m4-7v7',
  search: 'M21 21l-4.35-4.35M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
  refresh: 'M20 7v5h-5M4 17v-5h5m-4.3-2A8 8 0 0 1 18 6l2 4m-16 4 2 4a8 8 0 0 0 13.3-4'
}
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name]" /></svg>
</template>
