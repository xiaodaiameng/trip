<script setup lang="ts">
withDefaults(
  defineProps<{
    text: string
    speed?: number
    delay?: number
    color?: string
    shineColor?: string
    direction?: 'left' | 'right'
    disabled?: boolean
    className?: string
  }>(),
  {
    speed: 2.8,
    delay: 0,
    color: '#c8d8c6',
    shineColor: '#ffffff',
    direction: 'left',
    disabled: false,
    className: '',
  },
)
</script>

<template>
  <span
    class="shiny-text"
    :class="[className, { disabled, reverse: direction === 'right' }]"
    :style="{
      '--shiny-speed': `${speed}s`,
      '--shiny-delay': `${delay}s`,
      '--shiny-color': color,
      '--shiny-shine': shineColor,
    }"
  >
    {{ text }}
  </span>
</template>

<style scoped>
.shiny-text {
  display: inline-block;
  color: var(--shiny-color);
  background-image: linear-gradient(
    110deg,
    var(--shiny-color) 0%,
    var(--shiny-color) 36%,
    var(--shiny-shine) 50%,
    var(--shiny-color) 64%,
    var(--shiny-color) 100%
  );
  background-size: 220% auto;
  background-position: 150% center;
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: shiny-sweep var(--shiny-speed) linear var(--shiny-delay) infinite;
}

.shiny-text.reverse {
  animation-direction: reverse;
}

.shiny-text.disabled {
  animation: none;
}

@keyframes shiny-sweep {
  0% {
    background-position: 150% center;
  }

  54%,
  100% {
    background-position: -70% center;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shiny-text {
    animation: none;
  }
}
</style>
