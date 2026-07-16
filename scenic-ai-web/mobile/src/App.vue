<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import DigitalHumanStage from './components/DigitalHumanStage.vue'
import ScenicMap from './components/ScenicMap.vue'
import StrandsBackground from './components/effects/StrandsBackground.vue'
import {
  askQuestion,
  fetchAttractions,
  fetchDashboard,
  fetchOverview,
  fetchRecords,
  fetchTtsVoices,
  getApiBase,
  recommendRoute,
  submitFeedback,
  synthesizeSpeech,
} from './api'
import type {
  Attraction,
  ChatResponse,
  ConversationRecord,
  DashboardResponse,
  Overview,
  RoutePlanResponse,
  TtsVoiceResponse,
} from './types'

type SkinKey = 'caramel' | 'mint' | 'berry' | 'cocoa'
type TtsMode = 'browser' | 'server'

const apiBase = getApiBase()
const browserSpeechSupported =
  typeof window !== 'undefined'
  && typeof window.speechSynthesis !== 'undefined'
  && typeof window.SpeechSynthesisUtterance !== 'undefined'

const browserVoiceStorageKey = 'scenic-mobile-browser-voice'
const serverVoiceStorageKey = 'scenic-mobile-server-voice'
const ttsModeStorageKey = 'scenic-mobile-tts-mode'
const preferredVoiceName = 'MicrosoftXiaoyi Online (Natural) - Chinese (Mainland)'

const overview = ref<Overview | null>(null)
const attractions = ref<Attraction[]>([])
const dashboard = ref<DashboardResponse | null>(null)
const records = ref<ConversationRecord[]>([])

const publicLoading = ref(false)
const publicError = ref('')
const adminLoading = ref(false)
const adminError = ref('')

const chatForm = reactive({
  question: '',
  sessionId: 'mobile-guest',
})
const chatLoading = ref(false)
const chatError = ref('')
const chatResponse = ref<ChatResponse | null>(null)
const feedbackStatus = ref<'helpful' | 'unhelpful' | ''>('')
const feedbackComment = ref('')
const feedbackMessage = ref('')

const routeForm = reactive({
  interests: ['文化', '拍照'],
  durationHours: 6,
  pace: '适中',
  companionType: '朋友',
})
const routeLoading = ref(false)
const routeError = ref('')
const routeResponse = ref<RoutePlanResponse | null>(null)

const spokenContent = ref('数字人已就位，可以随时开始讲解。')
const narratorMessage = ref('数字人待机中。')
const speaking = ref(false)
const autoSpeakEnabled = ref(true)
const selectedSkin = ref<SkinKey>('caramel')
const availableVoices = ref<SpeechSynthesisVoice[]>([])
const selectedVoiceUri = ref('')
const hasStoredBrowserVoicePreference = ref(false)
const serverVoices = ref<TtsVoiceResponse[]>([])
const selectedServerVoice = ref('')
const ttsMode = ref<TtsMode>(browserSpeechSupported ? 'browser' : 'server')
const ttsLoading = ref(false)
const assistantPanelOpen = ref(false)

let activeAudio: HTMLAudioElement | null = null
let activeAudioUrl: string | null = null
let startupSpeechTimer = 0

const interestOptions = ['文化', '拍照', '亲子', '演艺', '夜游', '休闲']

const quickQuestions = computed(() =>
  overview.value?.hotQuestions?.length
    ? overview.value.hotQuestions
    : ['灵山大佛有什么亮点？', '适合带长辈怎么玩？', '有没有轻松一点的路线？'],
)

const overviewCards = computed(() => [
  {
    title: '景点数量',
    value: `${attractions.value.length}`,
    note: '适合快速筛选景点',
  },
  {
    title: '服务模块',
    value: `${overview.value?.mvpScope.length ?? 0}`,
    note: '问答、路线、地图、后台摘要',
  },
  {
    title: '后端地址',
    value: apiBase,
    note: '手机调试时这里必须可访问',
  },
])

const activeBrowserVoice = computed(() => {
  const manualVoice = availableVoices.value.find((voice) => voice.voiceURI === selectedVoiceUri.value)
  if (manualVoice) {
    return manualVoice
  }
  return pickPreferredVoice(availableVoices.value)
})

const activeServerVoice = computed(() =>
  serverVoices.value.find((voice) => voice.name === selectedServerVoice.value) ?? serverVoices.value[0] ?? null,
)

const activeVoiceLabel = computed(() => {
  if (ttsMode.value === 'server') {
    return activeServerVoice.value?.name ?? '服务端音色未准备好'
  }
  if (!browserSpeechSupported) {
    return '当前浏览器不支持语音接口'
  }
  return activeBrowserVoice.value?.name ?? '自动优选'
})

const topRecords = computed(() => records.value.slice(0, 4))
const firstRouteAttraction = computed(() => {
  const firstStopName = routeResponse.value?.stops[0]?.attractionName
  if (!firstStopName) {
    return null
  }
  return attractions.value.find((item) => item.name === firstStopName) ?? null
})

function getVoiceScore(voice: SpeechSynthesisVoice) {
  const name = voice.name.toLowerCase()
  const lang = voice.lang.toLowerCase()
  let score = 0

  if (lang === 'zh-cn') score += 120
  else if (lang.startsWith('zh-cn')) score += 110
  else if (lang.startsWith('zh')) score += 90
  else if (lang.startsWith('en')) score -= 40

  if (/(natural|xiaoxiao|xiaoyi|xiaomo|yaoyao|tingting|huihui|meijia|female|woman|girl)/i.test(name)) {
    score += 35
  }

  if (/(male|man|boy|yunyang|yunjian|xiaogang)/i.test(name)) {
    score -= 12
  }

  if (voice.default) score += 8
  if (voice.localService) score += 4

  return score
}

function findPreferredVoice(voices: SpeechSynthesisVoice[]) {
  const exactMatch = voices.find((voice) => voice.name === preferredVoiceName)
  if (exactMatch) {
    return exactMatch
  }

  return voices.find((voice) => voice.name.toLowerCase().includes('xiaoyi')) ?? null
}

function pickPreferredVoice(voices: SpeechSynthesisVoice[]) {
  const preferredVoice = findPreferredVoice(voices)
  if (preferredVoice) {
    return preferredVoice
  }

  if (voices.length === 0) {
    return null
  }

  return [...voices].sort((left, right) => {
    const scoreDiff = getVoiceScore(right) - getVoiceScore(left)
    if (scoreDiff !== 0) {
      return scoreDiff
    }
    return left.name.localeCompare(right.name)
  })[0]
}

function setBrowserVoicePreference(voiceUri: string, announceChange = true) {
  selectedVoiceUri.value = voiceUri
  hasStoredBrowserVoicePreference.value = voiceUri.length > 0

  if (voiceUri) {
    localStorage.setItem(browserVoiceStorageKey, voiceUri)
  } else {
    localStorage.removeItem(browserVoiceStorageKey)
  }

  if (!announceChange) {
    return
  }

  narratorMessage.value = voiceUri
    ? `已切换到浏览器音色 ${activeBrowserVoice.value?.name ?? '所选语音'}。`
    : `已恢复浏览器自动优选，当前使用 ${activeVoiceLabel.value}。`
}

function setServerVoicePreference(voiceName: string, announceChange = true) {
  selectedServerVoice.value = voiceName

  if (voiceName) {
    localStorage.setItem(serverVoiceStorageKey, voiceName)
  } else {
    localStorage.removeItem(serverVoiceStorageKey)
  }

  if (announceChange && voiceName) {
    narratorMessage.value = `已切换到服务端音色 ${voiceName}。`
  }
}

function setTtsMode(mode: TtsMode) {
  ttsMode.value = mode
  localStorage.setItem(ttsModeStorageKey, mode)
  narratorMessage.value = mode === 'server' ? '已切换到服务端发音。' : '已切换到浏览器发音。'
}

function refreshVoices() {
  if (!browserSpeechSupported) {
    return
  }

  const voices = window.speechSynthesis.getVoices()
  availableVoices.value = [...voices].sort((left, right) => {
    const scoreDiff = getVoiceScore(right) - getVoiceScore(left)
    if (scoreDiff !== 0) {
      return scoreDiff
    }
    return left.name.localeCompare(right.name)
  })

  if (selectedVoiceUri.value && !availableVoices.value.some((voice) => voice.voiceURI === selectedVoiceUri.value)) {
    selectedVoiceUri.value = ''
  }

  const storedVoiceUri = localStorage.getItem(browserVoiceStorageKey) ?? ''
  const storedVoiceExists = storedVoiceUri
    ? availableVoices.value.some((voice) => voice.voiceURI === storedVoiceUri)
    : false

  if (!selectedVoiceUri.value && (!hasStoredBrowserVoicePreference.value || !storedVoiceExists)) {
    const defaultVoice = findPreferredVoice(availableVoices.value)
    if (defaultVoice) {
      setBrowserVoicePreference(defaultVoice.voiceURI, false)
    }
  }
}

function cleanupAudio() {
  if (activeAudio) {
    activeAudio.pause()
    activeAudio.src = ''
    activeAudio = null
  }
  if (activeAudioUrl) {
    URL.revokeObjectURL(activeAudioUrl)
    activeAudioUrl = null
  }
}

function stopSpeaking() {
  if (browserSpeechSupported) {
    window.speechSynthesis.cancel()
  }
  cleanupAudio()
  speaking.value = false
  ttsLoading.value = false
}

async function playServerSpeech(text: string) {
  const blob = await synthesizeSpeech({
    text,
    voiceName: selectedServerVoice.value || undefined,
  })

  cleanupAudio()

  activeAudioUrl = URL.createObjectURL(blob)
  activeAudio = new Audio(activeAudioUrl)
  activeAudio.preload = 'auto'
  activeAudio.onended = () => {
    speaking.value = false
    narratorMessage.value = '服务端朗读完成。'
    cleanupAudio()
  }
  activeAudio.onerror = () => {
    speaking.value = false
    narratorMessage.value = '服务端语音播放失败。'
    cleanupAudio()
  }
  activeAudio.onpause = () => {
    if (!activeAudio?.ended) {
      speaking.value = false
    }
  }

  speaking.value = true
  narratorMessage.value = `服务端正在使用 ${activeVoiceLabel.value} 朗读。`
  await activeAudio.play()
}

async function speakText(text: string) {
  if (!text.trim()) {
    narratorMessage.value = '没有可朗读的内容。'
    return
  }

  stopSpeaking()

  if (ttsMode.value === 'server') {
    ttsLoading.value = true
    try {
      await playServerSpeech(text)
    } catch (error) {
      narratorMessage.value = error instanceof Error ? error.message : '服务端语音生成失败。'
      speaking.value = false
    } finally {
      ttsLoading.value = false
    }
    return
  }

  if (!browserSpeechSupported) {
    narratorMessage.value = '当前浏览器不支持语音合成，请切换到服务端发音。'
    return
  }

  const utterance = new SpeechSynthesisUtterance(text)
  if (activeBrowserVoice.value) {
    utterance.voice = activeBrowserVoice.value
    utterance.lang = activeBrowserVoice.value.lang
  } else {
    utterance.lang = 'zh-CN'
  }
  utterance.rate = 0.94
  utterance.pitch = 1.04
  utterance.volume = 1
  utterance.onstart = () => {
    speaking.value = true
    narratorMessage.value = '浏览器语音正在朗读。'
  }
  utterance.onend = () => {
    speaking.value = false
    narratorMessage.value = '浏览器朗读完成。'
  }
  utterance.onerror = () => {
    speaking.value = false
    narratorMessage.value = '浏览器语音朗读失败。'
  }
  window.speechSynthesis.speak(utterance)
}

function announce(text: string, speak = true) {
  const content = text.trim() || '没有可播报的内容。'
  spokenContent.value = content
  narratorMessage.value = speak && autoSpeakEnabled.value ? '正在自动播报最新内容。' : '内容已更新。'
  if (speak && autoSpeakEnabled.value) {
    void speakText(content)
  }
}

function speakCurrentIntro() {
  void speakText(spokenContent.value)
}

function scheduleStartupSpeech(delayMs = 720) {
  if (!autoSpeakEnabled.value || startupSpeechTimer) {
    return
  }

  startupSpeechTimer = window.setTimeout(() => {
    startupSpeechTimer = 0
    if (!autoSpeakEnabled.value || speaking.value || ttsLoading.value) {
      return
    }
    void speakText(spokenContent.value)
  }, delayMs)
}

function openAssistantPanel() {
  assistantPanelOpen.value = true
}

function closeAssistantPanel() {
  assistantPanelOpen.value = false
}

function resolveAttraction(target: Attraction | string) {
  if (typeof target !== 'string') {
    return target
  }
  return attractions.value.find((item) => item.name === target) ?? null
}

function buildNavigationCopyText(item: Attraction) {
  const scenicName = overview.value?.scenicName ?? '景区'
  const lines = [
    `${scenicName} - ${item.name}`,
    `${item.area} · ${item.theme}`,
  ]

  if (typeof item.latitude === 'number' && typeof item.longitude === 'number') {
    lines.push(`${item.latitude}, ${item.longitude}`)
  }

  return lines.join('\n')
}

function buildBaiduNavigationUrl(item: Attraction) {
  const scenicName = overview.value?.scenicName ?? ''
  const encodedName = encodeURIComponent(item.name)
  const encodedRegion = encodeURIComponent(scenicName)

  if (typeof item.latitude === 'number' && typeof item.longitude === 'number') {
    return `https://api.map.baidu.com/direction?destination=latlng:${item.latitude},${item.longitude}|name:${encodedName}&mode=driving&region=${encodedRegion}&output=html&src=scenic-ai-mobile`
  }

  return `https://map.baidu.com/search/${encodedName}/?querytype=s&wd=${encodedName}`
}

async function copyNavigationTarget(item: Attraction) {
  const text = buildNavigationCopyText(item)
  if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
    return false
  }

  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

async function startNavigation(target: Attraction | string) {
  const attraction = resolveAttraction(target)
  if (!attraction) {
    narratorMessage.value = '当前景点缺少可导航的位置信息。'
    return
  }

  const copied = await copyNavigationTarget(attraction)
  narratorMessage.value = copied
    ? `已复制 ${attraction.name} 的地点信息，正在打开百度地图。`
    : `正在打开百度地图前往 ${attraction.name}。`

  const url = buildBaiduNavigationUrl(attraction)
  const opened = window.open(url, '_blank', 'noopener')
  if (!opened) {
    window.location.href = url
  }
}

function toggleInterest(tag: string) {
  const exists = routeForm.interests.includes(tag)
  if (exists) {
    routeForm.interests = routeForm.interests.filter((item) => item !== tag)
    if (routeForm.interests.length === 0) {
      routeForm.interests = [tag]
    }
    return
  }
  routeForm.interests = [...routeForm.interests, tag]
}

async function loadPublicData() {
  publicLoading.value = true
  publicError.value = ''
  try {
    const [overviewData, attractionData] = await Promise.all([fetchOverview(), fetchAttractions()])
    overview.value = overviewData
    attractions.value = attractionData
    spokenContent.value = overviewData.welcomeMessage
    narratorMessage.value = '数字人待机中。'
  } catch (error) {
    publicError.value = error instanceof Error ? error.message : '加载移动端数据失败。'
  } finally {
    publicLoading.value = false
  }
}

async function loadAdminData() {
  adminLoading.value = true
  adminError.value = ''
  try {
    const [dashboardData, recordData] = await Promise.all([fetchDashboard(), fetchRecords()])
    dashboard.value = dashboardData
    records.value = recordData
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '加载运营数据失败。'
  } finally {
    adminLoading.value = false
  }
}

async function loadServerVoices() {
  try {
    serverVoices.value = await fetchTtsVoices()
    const stored = localStorage.getItem(serverVoiceStorageKey) ?? ''
    const exists = serverVoices.value.some((voice) => voice.name === stored)
    if (exists) {
      setServerVoicePreference(stored, false)
    } else if (serverVoices.value.length > 0) {
      setServerVoicePreference(serverVoices.value[0].name, false)
    }
  } catch (error) {
    narratorMessage.value = error instanceof Error ? error.message : '加载服务端音色失败。'
  }
}

async function submitChat(question?: string) {
  if (question) {
    chatForm.question = question
  }
  if (!chatForm.question.trim()) {
    chatError.value = '请输入要咨询的问题。'
    return
  }

  chatLoading.value = true
  chatError.value = ''
  feedbackStatus.value = ''
  feedbackComment.value = ''
  feedbackMessage.value = ''

  try {
    chatResponse.value = await askQuestion({
      question: chatForm.question.trim(),
      sessionId: chatForm.sessionId,
    })
    announce(chatResponse.value.answer)
  } catch (error) {
    chatError.value = error instanceof Error ? error.message : '问答请求失败。'
  } finally {
    chatLoading.value = false
  }
}

async function submitRoute() {
  routeLoading.value = true
  routeError.value = ''
  try {
    routeResponse.value = await recommendRoute({
      interests: routeForm.interests,
      durationHours: routeForm.durationHours,
      pace: routeForm.pace,
      companionType: routeForm.companionType,
    })
  } catch (error) {
    routeError.value = error instanceof Error ? error.message : '路线推荐失败。'
  } finally {
    routeLoading.value = false
  }
}

async function submitChatFeedback(helpful: boolean) {
  if (!chatResponse.value) {
    return
  }

  try {
    await submitFeedback({
      recordId: chatResponse.value.recordId,
      helpful,
      comment: feedbackComment.value.trim(),
    })
    feedbackStatus.value = helpful ? 'helpful' : 'unhelpful'
    feedbackMessage.value = helpful ? '已收到，感谢反馈。' : '已收到，我们会继续优化。'
  } catch (error) {
    feedbackMessage.value = error instanceof Error ? error.message : '反馈提交失败。'
  }
}

watch(selectedVoiceUri, (voiceUri) => {
  if (voiceUri && !availableVoices.value.some((voice) => voice.voiceURI === voiceUri)) {
    setBrowserVoicePreference('', false)
  }
})

watch(ttsMode, (mode) => {
  localStorage.setItem(ttsModeStorageKey, mode)
})

watch(assistantPanelOpen, (isOpen) => {
  document.body.style.overflow = isOpen ? 'hidden' : ''
})

onMounted(async () => {
  const storedMode = localStorage.getItem(ttsModeStorageKey)
  if (storedMode === 'browser' || storedMode === 'server') {
    ttsMode.value = storedMode === 'browser' && !browserSpeechSupported ? 'server' : storedMode
  }

  if (browserSpeechSupported) {
    const storedVoice = localStorage.getItem(browserVoiceStorageKey) ?? ''
    hasStoredBrowserVoicePreference.value = storedVoice.length > 0
    setBrowserVoicePreference(storedVoice, false)
    refreshVoices()
    window.speechSynthesis.addEventListener('voiceschanged', refreshVoices)
    window.setTimeout(refreshVoices, 180)
  } else {
    ttsMode.value = 'server'
  }

  await Promise.all([loadPublicData(), submitRoute(), loadAdminData(), loadServerVoices()])
  scheduleStartupSpeech()
})

onBeforeUnmount(() => {
  if (startupSpeechTimer) {
    window.clearTimeout(startupSpeechTimer)
  }
  stopSpeaking()
  if (browserSpeechSupported) {
    window.speechSynthesis.removeEventListener('voiceschanged', refreshVoices)
  }
  document.body.style.overflow = ''
})
</script>

<template>
  <div class="mobile-app">
    <StrandsBackground
      class="mobile-strands mobile-strands-fixed"
      :colors="['#F97316', '#7C3AED', '#06B6D4', '#EAB308']"
      :count="4"
      :speed="0.28"
      :amplitude="1.08"
      :waviness="1.02"
      :thickness="0.58"
      :glow="2.25"
      :taper="2.8"
      :spread="1.08"
      :intensity="0.38"
      :saturation="1.28"
      :opacity="0.48"
      :scale="0.95"
    />
    <main class="content-shell">
      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">移动端概览</p>
            <h2>{{ overview?.scenicName ?? '灵山胜境' }}</h2>
          </div>
          <p class="section-tip">数字人固定悬浮在右下角</p>
        </div>

        <p v-if="publicError" class="notice notice-error">{{ publicError }}</p>
        <p v-else-if="publicLoading" class="notice">正在加载景区数据...</p>

        <div class="stat-grid">
          <article v-for="card in overviewCards" :key="card.title" class="stat-card">
            <span class="stat-label">{{ card.title }}</span>
            <strong class="stat-value">{{ card.value }}</strong>
            <p class="muted-text">{{ card.note }}</p>
          </article>
        </div>
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">地图</p>
            <h2>景点和路线地图</h2>
          </div>
          <button
            v-if="firstRouteAttraction"
            type="button"
            class="ghost-button map-nav-button"
            @click="startNavigation(firstRouteAttraction)"
          >
            导航到首站
          </button>
        </div>
        <ScenicMap
          :attractions="attractions"
          :route-stops="routeResponse?.stops ?? []"
          :scenic-name="overview?.scenicName ?? '灵山胜境'"
        />
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">热门问题</p>
            <h2>点一下直接发问</h2>
          </div>
        </div>

        <div class="chip-row">
          <button
            v-for="question in quickQuestions"
            :key="question"
            type="button"
            class="chip-button"
            @click="submitChat(question)"
          >
            {{ question }}
          </button>
        </div>
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">景点清单</p>
            <h2>重点景点</h2>
          </div>
        </div>

        <div class="attraction-list">
          <article v-for="item in attractions" :key="item.id" class="attraction-card">
            <div class="attraction-top">
              <div>
                <strong>{{ item.name }}</strong>
                <p class="muted-text">{{ item.area }} · {{ item.theme }}</p>
              </div>
              <span class="score-badge">热度 {{ item.popularityScore }}</span>
            </div>
            <p class="body-text">{{ item.highlight }}</p>
            <p class="muted-text">建议停留 {{ item.suggestedDurationMinutes }} 分钟 · {{ item.openHours }}</p>
            <div class="action-row">
              <button type="button" class="ghost-button" @click="startNavigation(item)">开始导航</button>
            </div>
          </article>
        </div>
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">智能问答</p>
            <h2>输入问题，数字人开口</h2>
          </div>
        </div>

        <div class="field-group">
          <label class="field-label">
            <span>输入问题</span>
            <textarea
              v-model="chatForm.question"
              class="text-input textarea"
              rows="4"
              placeholder="例如：灵山大佛有什么亮点？带长辈怎么安排更轻松？"
            />
          </label>
          <div class="action-row dual-row">
            <button type="button" class="primary-button" :disabled="chatLoading" @click="submitChat()">
              {{ chatLoading ? '正在生成回答...' : '提交问题' }}
            </button>
            <button type="button" class="ghost-button" @click="chatForm.question = ''">清空</button>
          </div>
          <p v-if="chatError" class="notice notice-error">{{ chatError }}</p>
        </div>

        <article v-if="chatResponse" class="response-card">
          <div class="record-head">
            <div>
              <strong>{{ chatResponse.question }}</strong>
              <p class="muted-text">情绪识别：{{ chatResponse.emotion }}</p>
            </div>
            <span class="score-badge">记录 {{ chatResponse.recordId }}</span>
          </div>
          <p class="answer-text">{{ chatResponse.answer }}</p>

          <div v-if="chatResponse.sources.length" class="source-list">
            <article v-for="source in chatResponse.sources" :key="source.title" class="source-card">
              <strong>{{ source.title }}</strong>
              <p class="muted-text">{{ source.excerpt }}</p>
              <p class="muted-text">来源：{{ source.source }}</p>
            </article>
          </div>

          <div class="feedback-block">
            <div class="action-row dual-row">
              <button
                type="button"
                class="ghost-button"
                :class="{ active: feedbackStatus === 'helpful' }"
                @click="submitChatFeedback(true)"
              >
                有帮助
              </button>
              <button
                type="button"
                class="ghost-button"
                :class="{ active: feedbackStatus === 'unhelpful' }"
                @click="submitChatFeedback(false)"
              >
                还可以更好
              </button>
            </div>
            <label class="field-label">
              <span>补充意见</span>
              <textarea
                v-model="feedbackComment"
                class="text-input textarea"
                rows="3"
                placeholder="例如：希望解释更直白一点"
              />
            </label>
            <p v-if="feedbackMessage" class="muted-text">{{ feedbackMessage }}</p>
          </div>
        </article>
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">路线推荐</p>
            <h2>按兴趣和时长生成路线</h2>
          </div>
        </div>

        <div class="field-group">
          <div class="chip-row">
            <button
              v-for="tag in interestOptions"
              :key="tag"
              type="button"
              class="chip-button"
              :class="{ active: routeForm.interests.includes(tag) }"
              @click="toggleInterest(tag)"
            >
              {{ tag }}
            </button>
          </div>

          <div class="form-grid">
            <label class="field-label">
              <span>游览时长</span>
              <select v-model="routeForm.durationHours" class="text-input">
                <option :value="4">4 小时</option>
                <option :value="6">6 小时</option>
                <option :value="8">8 小时</option>
              </select>
            </label>
            <label class="field-label">
              <span>游玩节奏</span>
              <select v-model="routeForm.pace" class="text-input">
                <option value="轻松">轻松</option>
                <option value="适中">适中</option>
              </select>
            </label>
            <label class="field-label">
              <span>同行类型</span>
              <select v-model="routeForm.companionType" class="text-input">
                <option value="朋友">朋友</option>
                <option value="亲子">亲子</option>
                <option value="情侣">情侣</option>
                <option value="长辈">长辈</option>
              </select>
            </label>
          </div>

          <div class="action-row">
            <button type="button" class="primary-button" :disabled="routeLoading" @click="submitRoute()">
              {{ routeLoading ? '正在生成路线...' : '生成路线' }}
            </button>
          </div>
          <p v-if="routeError" class="notice notice-error">{{ routeError }}</p>
        </div>

        <article v-if="routeResponse" class="response-card">
          <div class="record-head">
            <div>
              <strong>{{ routeResponse.title }}</strong>
              <p class="muted-text">推荐总时长：{{ routeResponse.totalDurationHours }} 小时</p>
            </div>
          </div>

          <ol class="route-list">
            <li v-for="stop in routeResponse.stops" :key="`${stop.order}-${stop.attractionName}`" class="route-item">
              <div class="route-order">{{ stop.order }}</div>
              <div class="route-body">
                <div class="attraction-top">
                  <strong>{{ stop.attractionName }}</strong>
                  <span class="score-badge">{{ stop.duration }}</span>
                </div>
                <p class="body-text">{{ stop.highlight }}</p>
                <p class="muted-text">{{ stop.reason }}</p>
                <div class="action-row">
                  <button type="button" class="ghost-button" @click="startNavigation(stop.attractionName)">开始导航</button>
                </div>
              </div>
            </li>
          </ol>
        </article>
      </section>

      <section class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">后台摘要</p>
            <h2>移动端运营信息</h2>
          </div>
        </div>

        <p v-if="adminError" class="notice notice-error">{{ adminError }}</p>
        <p v-else-if="adminLoading" class="notice">正在加载后台数据...</p>

        <div v-if="dashboard" class="stat-grid">
          <article class="stat-card">
            <span class="stat-label">问答总量</span>
            <strong class="stat-value">{{ dashboard.conversationCount }}</strong>
            <p class="muted-text">游客问答记录累计数量</p>
          </article>
          <article class="stat-card">
            <span class="stat-label">反馈总量</span>
            <strong class="stat-value">{{ dashboard.feedbackCount }}</strong>
            <p class="muted-text">累计收集到的游客反馈</p>
          </article>
          <article class="stat-card">
            <span class="stat-label">平均响应</span>
            <strong class="stat-value">{{ dashboard.avgResponseMillis }} ms</strong>
            <p class="muted-text">当前问答平均处理耗时</p>
          </article>
          <article class="stat-card">
            <span class="stat-label">满意度</span>
            <strong class="stat-value">{{ dashboard.satisfactionRate }}%</strong>
            <p class="muted-text">根据反馈估算出的满意度</p>
          </article>
        </div>

        <div v-if="topRecords.length" class="record-list">
          <article v-for="record in topRecords" :key="record.id" class="record-card">
            <div class="record-head">
              <strong>{{ record.question }}</strong>
              <span class="score-badge">{{ record.emotion }}</span>
            </div>
            <p class="body-text">{{ record.answer }}</p>
            <p class="muted-text">来源：{{ record.matchedSource }} · {{ record.responseMillis }} ms</p>
          </article>
        </div>
      </section>

      <StrandsBackground
        class="mobile-strands mobile-strands-bottom"
        :colors="['#06B6D4', '#F97316', '#7C3AED', '#EAB308']"
        :count="5"
        :speed="0.32"
        :amplitude="1.12"
        :waviness="1.16"
        :thickness="0.6"
        :glow="2.5"
        :taper="2.7"
        :spread="1.18"
        :intensity="0.46"
        :saturation="1.34"
        :opacity="0.64"
        :scale="0.92"
      />
    </main>

    <aside class="floating-assistant" aria-label="右下角数字人">
      <div class="floating-stage-shell">
        <DigitalHumanStage class="floating-stage" :skin="selectedSkin" :speaking="speaking" />
      </div>
      <button type="button" class="floating-expand" @click="openAssistantPanel">展开数字人</button>
    </aside>

    <div v-if="assistantPanelOpen" class="assistant-sheet-backdrop" @click="closeAssistantPanel"></div>
    <section v-if="assistantPanelOpen" class="assistant-sheet" aria-label="数字人控制面板">
      <div class="assistant-sheet-head">
        <div>
          <p class="section-kicker">数字人控制</p>
          <h2>声音和播报</h2>
        </div>
        <button type="button" class="sheet-close" aria-label="关闭数字人控制面板" @click="closeAssistantPanel">×</button>
      </div>

      <div class="voice-status">
        <p class="body-text">{{ narratorMessage }}</p>
        <p class="muted-text">当前音色：{{ activeVoiceLabel }}</p>
        <p v-if="ttsLoading" class="muted-text">服务端正在生成音频...</p>
      </div>

      <div class="action-row dual-row">
        <button type="button" class="primary-button" @click="speakCurrentIntro">朗读开场</button>
        <button type="button" class="ghost-button" @click="stopSpeaking">停止朗读</button>
      </div>

      <label class="switch-row">
        <input v-model="autoSpeakEnabled" type="checkbox" />
        <span>启动与问答后自动朗读</span>
      </label>

      <label class="field-label">
        <span>发音通道</span>
        <select :value="ttsMode" class="text-input" @change="setTtsMode(($event.target as HTMLSelectElement).value as TtsMode)">
          <option value="server">服务端语音</option>
          <option v-if="browserSpeechSupported" value="browser">浏览器语音</option>
        </select>
      </label>

      <label v-if="ttsMode === 'server'" class="field-label">
        <span>服务端音色</span>
        <select
          :value="selectedServerVoice"
          class="text-input"
          :disabled="serverVoices.length === 0"
          @change="setServerVoicePreference(($event.target as HTMLSelectElement).value)"
        >
          <option v-for="voice in serverVoices" :key="voice.name" :value="voice.name">
            {{ voice.name }} · {{ voice.culture }} · {{ voice.gender }}
          </option>
        </select>
      </label>

      <label v-else class="field-label">
        <span>浏览器音色</span>
        <select
          v-model="selectedVoiceUri"
          class="text-input"
          @change="setBrowserVoicePreference(selectedVoiceUri)"
        >
          <option value="">自动优选更自然的中文音色</option>
          <option v-for="voice in availableVoices" :key="voice.voiceURI" :value="voice.voiceURI">
            {{ voice.name }} · {{ voice.lang }}
          </option>
        </select>
      </label>
    </section>
  </div>
</template>

<style scoped>
.mobile-app {
  position: relative;
  isolation: isolate;
  overflow-x: hidden;
  min-height: 100vh;
  background:
    radial-gradient(circle at 50% 0%, rgba(255, 223, 163, 0.08), transparent 26%),
    radial-gradient(circle at 50% 100%, rgba(120, 227, 193, 0.06), transparent 32%),
    #07110d;
}

.mobile-strands {
  pointer-events: none;
}

.mobile-strands-fixed {
  position: fixed;
  inset: 0;
  z-index: 0;
  opacity: 0.5;
  mix-blend-mode: screen;
}

.mobile-strands-bottom {
  position: relative;
  z-index: 0;
  height: 210px;
  margin: 4px 0 0;
  opacity: 0.72;
  mix-blend-mode: screen;
}

.content-shell {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 14px;
  padding: 14px 14px calc(260px + env(safe-area-inset-bottom));
}

.section-card,
.stat-card,
.attraction-card,
.response-card,
.source-card,
.record-card,
.assistant-sheet {
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 22px;
  background: rgba(7, 18, 13, 0.86);
  backdrop-filter: blur(14px);
}

.section-card,
.assistant-sheet {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.section-head,
.record-head,
.attraction-top,
.assistant-sheet-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.section-kicker {
  margin: 0 0 4px;
  color: rgba(235, 242, 236, 0.6);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h2,
strong,
p {
  margin: 0;
}

h2 {
  color: #eef6ef;
  font-size: 23px;
  line-height: 1.2;
}

.section-tip,
.muted-text {
  color: rgba(235, 242, 236, 0.68);
  font-size: 13px;
  line-height: 1.6;
}

.body-text,
.answer-text {
  color: #eef6ef;
  line-height: 1.75;
}

.answer-text {
  font-size: 15px;
}

.voice-status,
.field-group,
.feedback-block,
.attraction-list,
.record-list,
.source-list,
.chip-row,
.action-row,
.form-grid,
.stat-grid {
  display: grid;
  gap: 12px;
}

.dual-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.stat-grid {
  grid-template-columns: 1fr;
}

.stat-card,
.attraction-card,
.response-card,
.source-card,
.record-card {
  padding: 14px;
}

.stat-label,
.field-label span {
  color: rgba(235, 242, 236, 0.7);
  font-size: 13px;
}

.stat-value {
  display: block;
  margin: 6px 0 4px;
  color: #f7fbf7;
  font-size: 26px;
  line-height: 1.1;
  word-break: break-all;
}

.text-input {
  width: 100%;
  min-height: 44px;
  padding: 12px 14px;
  border: 1px solid rgba(235, 242, 236, 0.16);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  color: #11211a;
}

.textarea {
  min-height: 120px;
  resize: vertical;
}

.field-label {
  display: grid;
  gap: 8px;
}

.primary-button,
.ghost-button,
.chip-button,
.floating-expand,
.sheet-close {
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 14px;
  cursor: pointer;
}

.primary-button {
  background: linear-gradient(135deg, #e6bf83 0%, #8fe0bd 100%);
  color: #0a1f16;
  font-weight: 700;
}

.ghost-button {
  border-color: rgba(235, 242, 236, 0.16);
  background: rgba(255, 255, 255, 0.05);
  color: #eef6ef;
}

.chip-button {
  border-color: rgba(235, 242, 236, 0.14);
  background: rgba(255, 255, 255, 0.04);
  color: #eef6ef;
}

.chip-button.active,
.ghost-button.active {
  border-color: rgba(230, 191, 131, 0.58);
  background: rgba(230, 191, 131, 0.12);
}

.switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: rgba(235, 242, 236, 0.82);
  font-size: 14px;
}

.score-badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(230, 191, 131, 0.36);
  border-radius: 999px;
  background: rgba(230, 191, 131, 0.12);
  color: #f5dfbf;
  font-size: 12px;
  white-space: nowrap;
}

.map-nav-button {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  white-space: nowrap;
}

.route-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.route-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  gap: 10px;
}

.route-order {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(143, 224, 189, 0.14);
  color: #d7f5ea;
  font-weight: 700;
}

.route-body {
  display: grid;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(235, 242, 236, 0.08);
}

.notice {
  padding: 12px 14px;
  border: 1px solid rgba(230, 191, 131, 0.24);
  border-radius: 14px;
  background: rgba(230, 191, 131, 0.08);
  color: #f6e8cc;
}

.notice-error {
  border-color: rgba(234, 129, 129, 0.3);
  background: rgba(234, 129, 129, 0.1);
  color: #ffd4d4;
}

.floating-assistant {
  position: fixed;
  right: 10px;
  bottom: calc(16px + env(safe-area-inset-bottom));
  z-index: 560;
  display: grid;
  justify-items: end;
  gap: 8px;
  pointer-events: none;
}

.floating-stage-shell {
  width: clamp(150px, 38vw, 220px);
  height: clamp(240px, 40vh, 340px);
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0));
  filter: drop-shadow(0 18px 28px rgba(9, 18, 13, 0.28));
  overflow: hidden;
  pointer-events: auto;
}

.floating-stage {
  width: 100%;
  height: 100%;
}

.floating-expand {
  min-height: 40px;
  padding: 0 16px;
  border-color: rgba(223, 210, 193, 0.72);
  border-radius: 999px;
  background: rgba(255, 248, 242, 0.96);
  color: #604738;
  box-shadow: 0 10px 28px rgba(15, 22, 18, 0.18);
  pointer-events: auto;
}

.assistant-sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 720;
  background: rgba(3, 9, 6, 0.56);
  backdrop-filter: blur(8px);
}

.assistant-sheet {
  position: fixed;
  left: 14px;
  right: 14px;
  bottom: calc(14px + env(safe-area-inset-bottom));
  z-index: 730;
  max-height: min(78vh, 720px);
  overflow: auto;
}

.sheet-close {
  width: 44px;
  min-width: 44px;
  padding: 0;
  border-color: rgba(235, 242, 236, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #eef6ef;
  font-size: 26px;
  line-height: 1;
}

@media (min-width: 720px) {
  .content-shell {
    width: min(100%, 720px);
    margin: 0 auto;
    padding-bottom: 300px;
  }

  .assistant-sheet {
    width: min(720px, calc(100vw - 28px));
    left: 50%;
    right: auto;
    transform: translateX(-50%);
  }
}
</style>
