<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    colors?: string[]
    animationSpeed?: number
    direction?: 'horizontal' | 'vertical' | 'diagonal'
    pauseOnHover?: boolean
    showBorder?: boolean
  }>(),
  {
    colors: () => ['#f2f5ee', '#e0b968', '#c97a61', '#f2f5ee'],
    animationSpeed: 6,
    direction: 'horizontal',
    pauseOnHover: false,
    showBorder: false,
  },
)

const gradientAngle = computed(() => {
  if (props.direction === 'vertical') {
    return 'to bottom'
  }
  if (props.direction === 'diagonal') {
    return 'to bottom right'
  }
  return 'to right'
})

const gradientColors = computed(() => [...props.colors, props.colors[0]].join(', '))
const backgroundSize = computed(() => {
  if (props.direction === 'vertical') {
    return '100% 300%'
  }
  if (props.direction === 'diagonal') {
    return '300% 300%'
  }
  return '300% 100%'
})
</script>

<template>
  <div
    class="gradient-text-title"
    :class="{
      'with-border': showBorder,
      'pause-on-hover': pauseOnHover,
    }"
    :style="{
      '--gradient-angle': gradientAngle,
      '--gradient-colors': gradientColors,
      '--gradient-size': backgroundSize,
      '--gradient-speed': `${animationSpeed}s`,
    }"
  >
    <div v-if="showBorder" class="gradient-border" aria-hidden="true"></div>
    <span class="gradient-text-content">{{ text }}</span>
  </div>
</template>

<style scoped>
.gradient-text-title {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: fit-content;
  overflow: hidden;
}

.gradient-text-title.with-border {
  padding: 0.3rem 0.65rem;
  border-radius: 1rem;
}

.gradient-border,
.gradient-text-content {
  background-image: linear-gradient(var(--gradient-angle), var(--gradient-colors));
  background-size: var(--gradient-size);
  background-repeat: repeat;
  animation: gradient-shift var(--gradient-speed) ease-in-out infinite alternate;
}

.gradient-border {
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
}

.gradient-border::before {
  content: '';
  position: absolute;
  inset: 1px;
  border-radius: inherit;
  background: rgba(14, 30, 24, 0.88);
}

.gradient-text-content {
  position: relative;
  z-index: 1;
  display: inline-block;
  color: transparent;
  background-clip: text;
  -webkit-background-clip: text;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-weight: 800;
  letter-spacing: 0;
  line-height: 1;
  white-space: nowrap;
}

.pause-on-hover:hover .gradient-border,
.pause-on-hover:hover .gradient-text-content {
  animation-play-state: paused;
}

@keyframes gradient-shift {
  0% {
    background-position: 0% 50%;
  }

  100% {
    background-position: 100% 50%;
  }
}
</style>
