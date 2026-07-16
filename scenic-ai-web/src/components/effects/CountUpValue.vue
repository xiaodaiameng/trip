<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number | string
    from?: number
    duration?: number
    separator?: string
    startWhen?: boolean
  }>(),
  {
    from: 0,
    duration: 1000,
    separator: '',
    startWhen: true,
  },
)

const hostRef = ref<HTMLSpanElement | null>(null)
const currentValue = ref(props.from)
const hasStarted = ref(false)
let animationFrame = 0
let observer: IntersectionObserver | null = null

const parsedValue = computed(() => {
  if (typeof props.value === 'number') {
    return {
      numeric: props.value,
      suffix: '',
      raw: '',
      decimals: getDecimalPlaces(props.value),
    }
  }

  const normalized = props.value.trim()
  const match = normalized.match(/^(-?\d+(?:\.\d+)?)(.*)$/)
  if (!match) {
    return {
      numeric: null,
      suffix: '',
      raw: props.value,
      decimals: 0,
    }
  }

  const numeric = Number(match[1])
  return {
    numeric,
    suffix: match[2] ?? '',
    raw: '',
    decimals: getDecimalPlaces(numeric),
  }
})

const displayValue = computed(() => {
  if (parsedValue.value.raw) {
    return parsedValue.value.raw
  }

  const numeric = parsedValue.value.numeric
  if (numeric == null) {
    return ''
  }

  const formatted = formatNumber(currentValue.value, parsedValue.value.decimals)
  return `${formatted}${parsedValue.value.suffix}`
})

function getDecimalPlaces(value: number) {
  const text = String(value)
  return text.includes('.') ? text.split('.')[1].length : 0
}

function formatNumber(value: number, decimals: number) {
  const normalized = Number(value.toFixed(decimals))
  const formatted = new Intl.NumberFormat('zh-CN', {
    useGrouping: Boolean(props.separator),
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(normalized)

  return props.separator ? formatted.replace(/,/g, props.separator) : formatted
}

function easeOutCubic(progress: number) {
  return 1 - Math.pow(1 - progress, 3)
}

function runAnimation() {
  const target = parsedValue.value.numeric
  if (target == null) {
    return
  }

  window.cancelAnimationFrame(animationFrame)
  currentValue.value = props.from

  const startedAt = performance.now()
  const distance = target - props.from

  const tick = (time: number) => {
    const progress = Math.min((time - startedAt) / props.duration, 1)
    currentValue.value = props.from + distance * easeOutCubic(progress)

    if (progress < 1) {
      animationFrame = window.requestAnimationFrame(tick)
      return
    }

    currentValue.value = target
  }

  animationFrame = window.requestAnimationFrame(tick)
}

function start() {
  if (hasStarted.value || !props.startWhen) {
    return
  }
  hasStarted.value = true
  runAnimation()
}

onMounted(() => {
  if (!hostRef.value || parsedValue.value.raw) {
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        start()
        observer?.disconnect()
        observer = null
      }
    },
    { threshold: 0.2 },
  )
  observer.observe(hostRef.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.cancelAnimationFrame(animationFrame)
})

watch(
  () => props.value,
  () => {
    hasStarted.value = false
    if (props.startWhen) {
      runAnimation()
      hasStarted.value = true
    }
  },
)
</script>

<template>
  <span ref="hostRef" class="count-up-value">{{ displayValue }}</span>
</template>

<style scoped>
.count-up-value {
  display: inline-block;
  font-variant-numeric: tabular-nums;
}
</style>
