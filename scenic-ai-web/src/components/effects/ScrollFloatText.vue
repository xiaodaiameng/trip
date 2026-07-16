<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    className?: string
    stagger?: number
  }>(),
  {
    className: '',
    stagger: 0.025,
  },
)

const hostRef = ref<HTMLSpanElement | null>(null)
const visible = ref(false)
let observer: IntersectionObserver | null = null

const characters = computed(() => Array.from(props.text))

onMounted(() => {
  if (!hostRef.value) {
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        visible.value = true
        observer?.disconnect()
        observer = null
      }
    },
    { threshold: 0.35 },
  )
  observer.observe(hostRef.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <span ref="hostRef" class="scroll-float" :class="[className, { visible }]">
    <span
      v-for="(char, index) in characters"
      :key="`${char}-${index}`"
      class="scroll-float-char"
      :style="{ transitionDelay: `${index * stagger}s` }"
    >
      {{ char === ' ' ? '\u00a0' : char }}
    </span>
  </span>
</template>

<style scoped>
.scroll-float {
  display: inline-block;
  overflow: hidden;
}

.scroll-float-char {
  display: inline-block;
  opacity: 0;
  transform: translateY(72%) scaleY(1.32);
  transform-origin: 50% 0%;
  transition:
    opacity 0.72s ease,
    transform 0.72s cubic-bezier(0.2, 0.9, 0.25, 1.18);
}

.scroll-float.visible .scroll-float-char {
  opacity: 1;
  transform: translateY(0) scaleY(1);
}

@media (prefers-reduced-motion: reduce) {
  .scroll-float-char {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
