<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import DigitalHumanStage from './components/DigitalHumanStage.vue'
import ScenicMap from './components/ScenicMap.vue'
import StrandsBackground from './components/effects/StrandsBackground.vue'
import heroImage from './assets/hero.png'
import {
  adminLogin,
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
  LoginResponse,
  Overview,
  RoutePlanResponse,
  TtsVoiceResponse,
} from './types'

type SkinKey = 'caramel' | 'mint' | 'berry' | 'cocoa'
type TtsMode = 'browser' | 'server'

interface SpeechRecognitionAlternativeLike {
  transcript: string
}

interface SpeechRecognitionResultLike {
  0: SpeechRecognitionAlternativeLike
  isFinal: boolean
  length: number
}

interface SpeechRecognitionEventLike extends Event {
  results: ArrayLike<SpeechRecognitionResultLike>
}

interface SpeechRecognitionErrorEventLike extends Event {
  error: string
}

interface SpeechRecognitionLike extends EventTarget {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  onstart: ((this: SpeechRecognitionLike, event: Event) => void) | null
  onresult: ((this: SpeechRecognitionLike, event: SpeechRecognitionEventLike) => void) | null
  onerror: ((this: SpeechRecognitionLike, event: SpeechRecognitionErrorEventLike) => void) | null
  onend: ((this: SpeechRecognitionLike, event: Event) => void) | null
  start(): void
  stop(): void
  abort(): void
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike

interface SpeechWindow extends Window {
  SpeechRecognition?: SpeechRecognitionCtor
  webkitSpeechRecognition?: SpeechRecognitionCtor
}

const assistantName = '小灵'
const apiBase = getApiBase()
const browserSpeechSupported =
  typeof window !== 'undefined'
  && typeof window.speechSynthesis !== 'undefined'
  && typeof window.SpeechSynthesisUtterance !== 'undefined'
const hearingSupported =
  typeof window !== 'undefined'
  && ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)

const browserVoiceStorageKey = 'scenic-mobile-browser-voice'
const serverVoiceStorageKey = 'scenic-mobile-server-voice'
const ttsModeStorageKey = 'scenic-mobile-tts-mode'
const adminProfileStorageKey = 'scenic-admin-profile'
const preferredVoiceName = 'MicrosoftXiaoyi Online (Natural) - Chinese (Mainland)'

const overview = ref<Overview | null>(null)
const attractions = ref<Attraction[]>([])
const dashboard = ref<DashboardResponse | null>(null)
const records = ref<ConversationRecord[]>([])

const publicLoading = ref(false)
const publicError = ref('')
const adminLoading = ref(false)
const adminError = ref('')
const adminMessage = ref('')
const adminProfile = ref<LoginResponse | null>(null)
const adminForm = reactive({
  username: 'admin',
  password: '',
})

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

const spokenContent = ref(`${assistantName}已就位，可以随时开始讲解。`)
const narratorMessage = ref(`${assistantName}待机中。`)
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
const hearingEnabled = ref(false)
const listening = ref(false)
const recognitionTranscript = ref('')

let activeAudio: HTMLAudioElement | null = null
let activeAudioUrl: string | null = null
let recognition: SpeechRecognitionLike | null = null
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
    note: '景点、路线、地图、运营摘要',
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

const hearingStatusLabel = computed(() => {
  if (!hearingSupported) {
    return '当前浏览器不支持听力'
  }
  if (listening.value) {
    return `${assistantName}正在听`
  }
  return hearingEnabled.value ? '听力待命中' : '听力已关闭'
})

const hearingToggleLabel = computed(() => (hearingEnabled.value || listening.value ? '关闭听力' : '开启听力'))

const topRecords = computed(() => records.value.slice(0, 4))
const firstRouteAttraction = computed(() => {
  const firstStopName = routeResponse.value?.stops[0]?.attractionName
  if (!firstStopName) {
    return null
  }
  return attractions.value.find((item) => item.name === firstStopName) ?? null
})

function getRecognitionCtor() {
  if (typeof window === 'undefined') {
    return null
  }
  const speechWindow = window as SpeechWindow
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null
}

function resolveRecognitionError(error: string) {
  if (error === 'not-allowed' || error === 'service-not-allowed') {
    return '请先允许麦克风权限，然后再让小灵听你说话。'
  }
  if (error === 'no-speech') {
    return '小灵刚才没有听到有效语音，你可以再说一遍。'
  }
  if (error === 'audio-capture') {
    return '没有检测到可用麦克风，请检查设备后再试。'
  }
  if (error === 'network') {
    return '语音识别暂时不可用，请稍后再试。'
  }
  return '语音识别启动失败，请换用最新版 Edge 或 Chrome 再试。'
}

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

function stopListeningSession() {
  recognition?.abort()
  recognition = null
  listening.value = false
  recognitionTranscript.value = ''
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
  stopListeningSession()

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

function closeHearing() {
  hearingEnabled.value = false
  stopListeningSession()
  narratorMessage.value = `${assistantName}已停止收听。`
}

function openHearing() {
  if (!hearingSupported) {
    narratorMessage.value = '当前浏览器暂不支持语音输入，请使用最新版 Edge 或 Chrome。'
    return
  }
  hearingEnabled.value = true
  startListening()
}

function toggleHearing() {
  if (hearingEnabled.value || listening.value) {
    closeHearing()
    return
  }
  openHearing()
}

function startListening() {
  const RecognitionCtor = getRecognitionCtor()
  if (!RecognitionCtor) {
    narratorMessage.value = '当前浏览器暂不支持语音输入，请使用最新版 Edge 或 Chrome。'
    return
  }

  stopSpeaking()
  stopListeningSession()

  let finalTranscript = ''
  let failed = false
  const nextRecognition = new RecognitionCtor()
  recognition = nextRecognition
  recognitionTranscript.value = ''

  nextRecognition.lang = 'zh-CN'
  nextRecognition.continuous = false
  nextRecognition.interimResults = true
  nextRecognition.maxAlternatives = 1

  nextRecognition.onstart = () => {
    listening.value = true
    narratorMessage.value = `${assistantName}正在听，请直接说出你的问题。`
  }

  nextRecognition.onresult = (event) => {
    let mergedTranscript = ''
    for (let index = 0; index < event.results.length; index += 1) {
      const result = event.results[index]
      const segment = result[0]?.transcript ?? ''
      mergedTranscript += segment
      if (result.isFinal) {
        finalTranscript += segment
      }
    }

    const transcript = (finalTranscript || mergedTranscript).trim()
    recognitionTranscript.value = transcript
    chatForm.question = transcript
  }

  nextRecognition.onerror = (event) => {
    failed = true
    listening.value = false
    recognition = null
    if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
      hearingEnabled.value = false
    }
    narratorMessage.value = resolveRecognitionError(event.error)
  }

  nextRecognition.onend = () => {
    listening.value = false
    recognition = null

    if (failed) {
      return
    }

    const transcript = (finalTranscript || recognitionTranscript.value || chatForm.question).trim()
    recognitionTranscript.value = ''
    if (!transcript) {
      narratorMessage.value = `${assistantName}这次没有听清，你可以再试一次。`
      return
    }

    hearingEnabled.value = false
    narratorMessage.value = `${assistantName}听到了，正在整理回答。`
    void submitChat(transcript)
  }

  try {
    nextRecognition.start()
  } catch {
    recognition = null
    listening.value = false
    narratorMessage.value = '语音输入没有成功开启，请检查麦克风权限后重试。'
  }
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
  narratorMessage.value = `${assistantName}在线，可以朗读，也可以听你提问。`
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
    if (!overviewData.welcomeMessage.includes(assistantName)) {
      overviewData.welcomeMessage = `可以叫我${assistantName}。${overviewData.welcomeMessage}`
    }
    overview.value = overviewData
    attractions.value = attractionData
    spokenContent.value = overviewData.welcomeMessage
    narratorMessage.value = `${assistantName}待机中。`
  } catch (error) {
    publicError.value = error instanceof Error ? error.message : '加载移动端数据失败。'
  } finally {
    publicLoading.value = false
  }
}

async function loadAdminData() {
  adminLoading.value = true
  adminError.value = ''
  if (!adminProfile.value?.token && !localStorage.getItem('scenic-admin-token')) {
    adminError.value = '运营数据仅管理员可查看，请登录后台后再打开。'
    adminLoading.value = false
    return
  }
  try {
    const [dashboardData, recordData] = await Promise.all([fetchDashboard(), fetchRecords()])
    dashboard.value = dashboardData
    records.value = recordData
  } catch (error) {
    adminError.value = error instanceof Error ? error.message : '加载运营数据失败。'
    if (adminError.value.includes('管理员')) {
      adminProfile.value = null
      persistAdminSession(null)
    }
  } finally {
    adminLoading.value = false
  }
}

function restoreAdminSession() {
  const raw = localStorage.getItem(adminProfileStorageKey)
  if (!raw) {
    return
  }
  try {
    const profile = JSON.parse(raw) as LoginResponse
    if (profile?.token) {
      adminProfile.value = profile
      localStorage.setItem('scenic-admin-token', profile.token)
    }
  } catch {
    persistAdminSession(null)
  }
}

function persistAdminSession(profile: LoginResponse | null) {
  if (!profile) {
    localStorage.removeItem(adminProfileStorageKey)
    localStorage.removeItem('scenic-admin-token')
    return
  }
  localStorage.setItem(adminProfileStorageKey, JSON.stringify(profile))
  localStorage.setItem('scenic-admin-token', profile.token)
}

async function loginToAdmin() {
  if (!adminForm.username.trim() || !adminForm.password.trim()) {
    adminMessage.value = '请输入管理员账号和密码。'
    return
  }

  adminLoading.value = true
  adminError.value = ''
  adminMessage.value = ''
  try {
    const profile = await adminLogin({
      username: adminForm.username.trim(),
      password: adminForm.password,
    })
    adminProfile.value = profile
    persistAdminSession(profile)
    adminMessage.value = `欢迎回来，${profile.displayName}`
    await loadAdminData()
  } catch (error) {
    adminMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试。'
  } finally {
    adminLoading.value = false
  }
}

function logoutAdmin() {
  adminProfile.value = null
  dashboard.value = null
  records.value = []
  adminError.value = '运营数据仅管理员可查看，请登录后台后再打开。'
  adminMessage.value = '已退出后台。'
  persistAdminSession(null)
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
  restoreAdminSession()

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
      <section
        class="mobile-hero"
        :style="{
          backgroundImage: `linear-gradient(90deg, rgba(21, 38, 31, 0.9), rgba(21, 38, 31, 0.52)), url(${heroImage})`,
        }"
      >
        <div class="mobile-hero-main">
          <p class="eyebrow">灵境智游</p>
          <h1>LingVista灵言</h1>
          <p class="hero-copy">
            古人有云，今人有灵。
          </p>

          <div class="mobile-nav-strip" aria-label="移动端功能导航">
            <a class="nav-button" href="#mobile-map">地图</a>
            <a class="nav-button" href="#mobile-chat">问答</a>
            <a class="nav-button" href="#mobile-route">路线</a>
            <a class="nav-button" href="#mobile-dashboard">运营</a>
          </div>
        </div>

        <div class="hero-metrics">
          <article v-for="card in overviewCards" :key="card.title" class="hero-metric">
            <span>{{ card.title }}</span>
            <strong>{{ card.value }}</strong>
            <p>{{ card.note }}</p>
          </article>
        </div>
      </section>

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

      <section id="mobile-map" class="section-card">
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
          </article>
        </div>
      </section>

      <section id="mobile-chat" class="section-card">
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

      <section id="mobile-route" class="section-card">
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

      <section id="mobile-dashboard" class="section-card">
        <div class="section-head">
          <div>
            <p class="section-kicker">后台摘要</p>
            <h2>移动端运营信息</h2>
          </div>
          <a
            class="ghost-button mobile-download-button"
            href="/downloads/scenic-questionnaire-data-package.zip"
            download="景区问卷数据资料包汇总.zip"
          >
            下载资料包
          </a>
        </div>

        <article v-if="!adminProfile" class="response-card">
          <div class="record-head">
            <div>
              <strong>管理员入口</strong>
              <p class="muted-text">运营数据需要管理员登录后查看，普通游客不会受影响。</p>
            </div>
          </div>
          <div class="form-grid">
            <label class="field-label">
              <span>账号</span>
              <input v-model="adminForm.username" class="text-input" autocomplete="username" placeholder="请输入管理员账号" />
            </label>
            <label class="field-label">
              <span>密码</span>
              <input
                v-model="adminForm.password"
                type="password"
                class="text-input"
                autocomplete="current-password"
                placeholder="请输入管理员密码"
                @keyup.enter="loginToAdmin"
              />
            </label>
          </div>
          <div class="action-row">
            <button type="button" class="primary-button" :disabled="adminLoading" @click="loginToAdmin">
              {{ adminLoading ? '正在登录...' : '登录后台' }}
            </button>
          </div>
          <p v-if="adminMessage" class="notice" :class="{ 'notice-error': !adminMessage.includes('欢迎') && !adminMessage.includes('退出') }">
            {{ adminMessage }}
          </p>
        </article>

        <article v-else class="response-card">
          <div class="record-head">
            <div>
              <strong>欢迎，{{ adminProfile.displayName }}</strong>
              <p class="muted-text">可以刷新运营摘要，或退出当前后台会话。</p>
            </div>
          </div>
          <div class="action-row dual-row">
            <button type="button" class="ghost-button" :disabled="adminLoading" @click="loadAdminData">刷新数据</button>
            <button type="button" class="ghost-button" @click="logoutAdmin">退出后台</button>
          </div>
          <p v-if="adminMessage" class="muted-text">{{ adminMessage }}</p>
        </article>

        <p v-if="adminError" class="notice" :class="{ 'notice-error': !adminError.includes('管理员') }">{{ adminError }}</p>
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
          <p class="section-kicker">小灵控制台</p>
          <h2>声音、播报和听力</h2>
        </div>
        <button type="button" class="sheet-close" aria-label="关闭数字人控制面板" @click="closeAssistantPanel">×</button>
      </div>

      <div class="voice-status">
        <p class="body-text">{{ narratorMessage }}</p>
        <p class="muted-text">当前音色：{{ activeVoiceLabel }}</p>
        <p class="muted-text">听力状态：{{ hearingStatusLabel }}</p>
        <p v-if="recognitionTranscript" class="muted-text">已听到：{{ recognitionTranscript }}</p>
        <p v-if="ttsLoading" class="muted-text">服务端正在生成音频...</p>
      </div>

      <div class="action-row dual-row">
        <button type="button" class="primary-button" @click="speakCurrentIntro">朗读开场</button>
        <button type="button" class="ghost-button" @click="stopSpeaking">停止朗读</button>
      </div>

      <div class="action-row">
        <button type="button" class="ghost-button hearing-button" :disabled="!hearingSupported || chatLoading" @click="toggleHearing">
          {{ hearingToggleLabel }}
        </button>
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
          <option value="">自动优选更自然的讲解音色</option>
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
  --surface: rgba(247, 251, 241, 0.9);
  --surface-strong: rgba(255, 255, 255, 0.96);
  --line: rgba(220, 232, 210, 0.52);
  --line-strong: rgba(230, 186, 98, 0.56);
  --ink: #13251f;
  --muted: #667568;
  --gold: #d9ad58;
  --gold-strong: #e8c370;
  --coral: #cf765c;
  --mint: #88b99b;
  position: relative;
  isolation: isolate;
  overflow-x: hidden;
  min-height: 100vh;
  background:
    linear-gradient(135deg, rgba(13, 30, 24, 0.96) 0%, rgba(18, 42, 33, 0.94) 34%, rgba(48, 55, 42, 0.92) 100%),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px 80px);
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
  width: min(1180px, calc(100vw - 28px));
  margin: 0 auto;
  gap: 18px;
  padding: 24px 0 calc(280px + env(safe-area-inset-bottom));
}

.mobile-hero {
  position: relative;
  display: grid;
  gap: 18px;
  min-width: 0;
  min-height: 520px;
  padding: 22px;
  border: 1px solid rgba(231, 193, 112, 0.2);
  border-radius: 8px;
  background-position: center;
  background-size: cover;
  color: #f8fcf2;
  box-shadow: 0 28px 80px rgba(5, 15, 12, 0.28);
  overflow: hidden;
}

.mobile-hero-main {
  display: grid;
  gap: 14px;
  min-width: 0;
  align-content: end;
  min-height: 250px;
}

.eyebrow {
  margin: 0;
  color: rgba(232, 195, 112, 0.88);
  font-size: 13px;
  font-weight: 700;
}

h1 {
  max-width: 860px;
  margin: 0;
  color: #ffffff;
  font-size: clamp(44px, 17vw, 76px);
  line-height: 1.02;
  overflow-wrap: anywhere;
}

.hero-copy {
  max-width: 760px;
  margin: 0;
  color: rgba(248, 252, 242, 0.86);
  font-size: 17px;
  line-height: 1.75;
}

.mobile-nav-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
  margin-top: 8px;
}

.nav-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 46px;
  padding: 0 12px;
  border: 1px solid rgba(248, 252, 242, 0.2);
  border-radius: 8px;
  background: rgba(248, 252, 242, 0.1);
  color: rgba(248, 252, 242, 0.9);
  font-size: 15px;
  font-weight: 700;
  backdrop-filter: blur(10px);
}

.nav-button:active,
.nav-button:hover {
  border-color: rgba(232, 195, 112, 0.62);
  background: rgba(232, 195, 112, 0.2);
  color: #ffffff;
}

.hero-metrics {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  min-width: 0;
}

.hero-metric {
  position: relative;
  display: grid;
  gap: 8px;
  min-height: 116px;
  padding: 18px;
  border: 1px solid rgba(232, 195, 112, 0.32);
  border-radius: 8px;
  background: rgba(12, 31, 25, 0.68);
  color: #f8fcf2;
  box-shadow: 0 22px 50px rgba(5, 16, 13, 0.24);
  backdrop-filter: blur(12px);
  overflow: hidden;
}

.hero-metric span {
  color: rgba(232, 195, 112, 0.88);
  font-size: 15px;
  font-weight: 800;
}

.hero-metric strong {
  color: #ffffff;
  font-size: 30px;
  line-height: 1.1;
  word-break: break-all;
}

.hero-metric p {
  margin: 0;
  color: rgba(248, 252, 242, 0.72);
  font-size: 14px;
  line-height: 1.65;
}

.section-card,
.stat-card,
.attraction-card,
.response-card,
.source-card,
.record-card,
.assistant-sheet {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(241, 247, 237, 0.92)),
    var(--surface);
  color: var(--ink);
  box-shadow:
    0 20px 54px rgba(3, 17, 12, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(14px);
}

.section-card::before,
.stat-card::before,
.attraction-card::before,
.response-card::before,
.source-card::before,
.record-card::before,
.assistant-sheet::before,
.hero-metric::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(115deg, transparent 0%, transparent 42%, rgba(255, 255, 255, 0.46) 50%, transparent 58%, transparent 100%);
  transform: translateX(-120%);
  transition: transform 0.78s ease;
  pointer-events: none;
}

.section-card:hover::before,
.stat-card:hover::before,
.attraction-card:hover::before,
.response-card:hover::before,
.source-card:hover::before,
.record-card:hover::before,
.assistant-sheet:hover::before,
.hero-metric:hover::before {
  transform: translateX(120%);
}

.section-card > *,
.stat-card > *,
.attraction-card > *,
.response-card > *,
.source-card > *,
.record-card > *,
.assistant-sheet > *,
.hero-metric > * {
  position: relative;
  z-index: 1;
}

.section-card,
.assistant-sheet {
  display: grid;
  gap: 18px;
  padding: 18px;
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
  color: var(--gold);
  font-size: 12px;
  font-weight: 800;
}

h2,
strong,
p {
  margin: 0;
}

h2 {
  color: var(--ink);
  font-size: 23px;
  line-height: 1.2;
}

.section-tip,
.muted-text {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}

.body-text,
.answer-text {
  color: #20342b;
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
  color: var(--muted);
  font-size: 13px;
}

.stat-value {
  display: block;
  margin: 6px 0 4px;
  color: var(--ink);
  font-size: 26px;
  line-height: 1.1;
  word-break: break-all;
}

.text-input {
  width: 100%;
  min-height: 44px;
  padding: 12px 14px;
  border: 1px solid rgba(31, 63, 50, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  color: #11211a;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.62);
}

.text-input:focus {
  outline: 2px solid rgba(232, 195, 112, 0.38);
  border-color: rgba(232, 195, 112, 0.72);
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
  border-radius: 8px;
  cursor: pointer;
}

.primary-button {
  background: linear-gradient(135deg, #214f3d, #357253);
  color: #ffffff;
  font-weight: 700;
  box-shadow: 0 14px 28px rgba(17, 62, 43, 0.18);
}

.ghost-button {
  border-color: rgba(31, 63, 50, 0.18);
  background: rgba(255, 255, 255, 0.78);
  color: #20342b;
}

.chip-button {
  border-color: rgba(25, 58, 47, 0.14);
  background: rgba(244, 248, 239, 0.9);
  color: #20342b;
}

.chip-button.active,
.ghost-button.active {
  border-color: rgba(136, 185, 155, 0.86);
  background: rgba(219, 238, 222, 0.86);
}

.switch-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 14px;
}

.score-badge {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(232, 195, 112, 0.56);
  border-radius: 8px;
  background: rgba(255, 246, 222, 0.86);
  color: #73551b;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.map-nav-button {
  min-height: 36px;
  padding: 0 12px;
  border-radius: 8px;
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
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(232, 195, 112, 0.24), rgba(136, 185, 155, 0.24));
  color: #254a39;
  font-weight: 700;
}

.route-body {
  display: grid;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(31, 63, 50, 0.1);
}

.notice {
  padding: 12px 14px;
  border: 1px solid rgba(232, 195, 112, 0.44);
  border-radius: 8px;
  background: rgba(255, 248, 229, 0.92);
  color: #73551b;
}

.notice-error {
  border-color: rgba(207, 118, 92, 0.42);
  background: rgba(255, 238, 232, 0.94);
  color: #7c3c2d;
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
  width: clamp(110px, 30vw, 170px);
  height: clamp(190px, 32vh, 290px);
  border: 1px solid rgba(232, 195, 112, 0.22);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(255, 255, 255, 0.08)),
    linear-gradient(135deg, rgba(48, 91, 67, 0.26), rgba(232, 195, 112, 0.12));
  filter: drop-shadow(0 18px 28px rgba(9, 18, 13, 0.28));
  overflow: hidden;
  pointer-events: auto;
}

.floating-stage {
  width: 100%;
  height: 100%;
}

.floating-expand {
  min-height: 38px;
  padding: 0 12px;
  border-color: rgba(232, 195, 112, 0.5);
  border-radius: 8px;
  background: rgba(247, 251, 242, 0.94);
  color: var(--ink);
  font-weight: 800;
  box-shadow: 0 10px 28px rgba(15, 22, 18, 0.18);
  pointer-events: auto;
}

.assistant-sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 720;
  background: rgba(8, 18, 15, 0.62);
  backdrop-filter: blur(12px);
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
  border-color: rgba(31, 63, 50, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--ink);
  font-size: 26px;
  line-height: 1;
}

.mobile-download-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 10px 14px;
  white-space: nowrap;
}

@media (min-width: 720px) {
  .content-shell {
    width: min(100%, 720px);
    margin: 0 auto;
    padding-bottom: 300px;
  }

  .hero-metrics,
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .attraction-list,
  .record-list,
  .source-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .assistant-sheet {
    width: min(720px, calc(100vw - 28px));
    left: 50%;
    right: auto;
    transform: translateX(-50%);
  }
}

@media (max-width: 520px) {
  .mobile-download-button {
    width: 100%;
  }

  .mobile-hero {
    min-height: 500px;
    padding: 18px 14px;
  }

  .mobile-nav-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  h1 {
    font-size: 42px;
  }
}
</style>
