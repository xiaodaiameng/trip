<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import heroImage from './assets/hero.png'
import DigitalHumanStage from './components/DigitalHumanStage.vue'
import ScenicMap from './components/ScenicMap.vue'
import CountUpValue from './components/effects/CountUpValue.vue'
import GradientTextTitle from './components/effects/GradientTextTitle.vue'
import RibbonsCursor from './components/effects/RibbonsCursor.vue'
import ScrollFloatText from './components/effects/ScrollFloatText.vue'
import StrandsBackground from './components/effects/StrandsBackground.vue'
import TextCursorTrail from './components/effects/TextCursorTrail.vue'
import {
  adminLogin,
  askQuestion,
  createKnowledge,
  deleteKnowledge,
  fetchAttractions,
  fetchDashboard,
  fetchKnowledge,
  fetchOverview,
  fetchRecords,
  getApiBase,
  recommendRoute,
  submitFeedback as submitFeedbackRequest,
  updateKnowledge,
} from './api'
import type {
  Attraction,
  ChatResponse,
  ConversationRecord,
  DashboardResponse,
  KnowledgeEntry,
  LoginResponse,
  Overview,
  RoutePlanResponse,
} from './types'

type ViewKey = 'home' | 'chat' | 'route' | 'dashboard' | 'knowledge'
type SkinKey = 'caramel' | 'mint' | 'berry' | 'cocoa'

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

const navItems: Array<{ key: ViewKey; label: string }> = [
  { key: 'home', label: '游客首页' },
  { key: 'chat', label: '智能问答' },
  { key: 'route', label: '路线推荐' },
  { key: 'dashboard', label: '后台看板' },
  { key: 'knowledge', label: '知识库' },
]

const interestOptions = ['文化', '拍照', '亲子', '演艺', '夜游', '休闲']
const categoryOptions = ['FAQ', '讲解', '路线', '服务']
const assistantName = '薇薇'
const activeView = ref<ViewKey>('home')
const pageBodyRef = ref<HTMLElement | null>(null)
const apiBase = getApiBase()

const overview = ref<Overview | null>(null)
const attractions = ref<Attraction[]>([])
const publicLoading = ref(false)
const publicError = ref('')

const chatForm = reactive({
  question: '',
  sessionId: 'web-guest',
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
const spokenContent = ref('灵山数字导览官已就位，随时可以开始讲解。')
const narratorMessage = ref('当前是待机状态，等你触发内容我就开口。')
const speaking = ref(false)
const autoSpeakEnabled = ref(true)
const selectedSkin = ref<SkinKey>('caramel')
const speechSupported = typeof window !== 'undefined' && 'speechSynthesis' in window
const hearingSupported =
  typeof window !== 'undefined'
  && ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)
const digitalOverlayOpen = ref(false)
const voiceStorageKey = 'scenic-preferred-voice'
const preferredVoiceName = 'MicrosoftXiaoyi Online (Natural) - Chinese (Mainland)'
const availableVoices = ref<SpeechSynthesisVoice[]>([])
const selectedVoiceUri = ref('')
const hasStoredVoicePreference = ref(false)
const hearingEnabled = ref(true)
const listening = ref(false)
const recognitionTranscript = ref('')

let recognition: SpeechRecognitionLike | null = null
let listeningResumeTimer = 0
let startupSpeechTimer = 0

spokenContent.value = `可以叫我${assistantName}，我已经准备好为你讲解灵山。`
narratorMessage.value = `${assistantName}待命中。`

const adminForm = reactive({
  username: 'admin',
  password: 'admin123',
})
const adminProfile = ref<LoginResponse | null>(null)
const adminLoading = ref(false)
const adminMessage = ref('')

const dashboard = ref<DashboardResponse | null>(null)
const records = ref<ConversationRecord[]>([])
const knowledgeList = ref<KnowledgeEntry[]>([])
const adminDataLoading = ref(false)
const adminDataError = ref('')

const knowledgeForm = reactive({
  id: null as number | null,
  title: '',
  category: 'FAQ',
  keywords: '',
  content: '',
  source: '后台补录',
  published: true,
})
const knowledgeSaving = ref(false)

const connectionLabel = computed(() => {
  if (publicLoading.value) {
    return '连接中'
  }
  if (publicError.value) {
    return '连接异常'
  }
  return '已连接'
})

const scenicName = computed(() => overview.value?.scenicName ?? '灵山胜境')

const summaryStats = computed(() => [
  {
    label: '景点资料',
    value: `${attractions.value.length} 个`,
    description: '适合首页展示、问答命中与路线组合',
  },
  {
    label: '服务能力',
    value: `${overview.value?.mvpScope.length ?? 0} 项`,
    description: '问答、讲解、推荐与后台能力已接入骨架',
  },
  {
    label: '当前后端',
    value: connectionLabel.value,
    description: '已统一中文接口提示、异常提示与日志语义',
  },
])

const topTrendValue = computed(() => {
  if (!dashboard.value || dashboard.value.weeklyTrend.length === 0) {
    return 1
  }
  return Math.max(...dashboard.value.weeklyTrend.map((item) => item.value), 1)
})

const voiceOptions = computed(() =>
  availableVoices.value.map((voice) => ({
    voiceURI: voice.voiceURI,
    label: `${voice.name} · ${voice.lang}${voice.localService ? ' · 本地' : ''}`,
  })),
)

const preferredVoicePlaceholder = `默认：${preferredVoiceName}`

const activeVoice = computed(() => {
  const manualVoice = availableVoices.value.find((voice) => voice.voiceURI === selectedVoiceUri.value)
  if (manualVoice) {
    return manualVoice
  }
  return pickPreferredVoice(availableVoices.value)
})

const activeVoiceLabel = computed(() => activeVoice.value?.name ?? preferredVoiceName)

const visitorStats = computed(() => [
  {
    label: '景点资料',
    value: `${attractions.value.length} 个`,
    description: '景点名称、亮点、开放时间和坐标信息已准备好',
  },
  {
    label: '热门问题',
    value: `${overview.value?.hotQuestions.length ?? 0} 条`,
    description: '可直接点选常见问题，让薇薇快速回答',
  },
  {
    label: '导览状态',
    value: connectionLabel.value,
    description: '问答、路线推荐和语音讲解按当前状态提供服务',
  },
])

const digitalActivityLabel = computed(() => {
  if (speaking.value) {
    return '薇薇播报中'
  }
  if (listening.value) {
    return '薇薇正在听'
  }
  return digitalOverlayOpen.value ? '薇薇在线' : '薇薇待机'
})

const hearingStatusLabel = computed(() => {
  if (!hearingSupported) {
    return '当前浏览器不支持听力'
  }
  if (listening.value) {
    return '听力正在收音'
  }
  return hearingEnabled.value ? '听力待命中' : '听力已关闭'
})

const hearingToggleLabel = computed(() => (hearingEnabled.value || listening.value ? '关闭听力' : '开启听力'))

function buildAssistantWelcome(text: string) {
  const normalized = text.trim()
  if (!normalized) {
    return `可以叫我${assistantName}。`
  }
  if (normalized.includes(assistantName)) {
    return normalized
  }
  return `可以叫我${assistantName}。${normalized}`
}

function getRecognitionCtor() {
  if (typeof window === 'undefined') {
    return null
  }
  const speechWindow = window as SpeechWindow
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null
}

function resolveRecognitionError(error: string) {
  if (error === 'not-allowed' || error === 'service-not-allowed') {
    return '请先允许麦克风权限，然后再让薇薇听你说话。'
  }
  if (error === 'no-speech') {
    return '薇薇刚才没有听到有效语音，你可以再说一遍。'
  }
  if (error === 'audio-capture') {
    return '没有检测到可用麦克风，请检查设备后再试。'
  }
  if (error === 'network') {
    return '语音识别暂时不可用，请稍后再试。'
  }
  return '语音识别启动失败，请换用最新版 Edge 或 Chrome 再试。'
}

function clearListeningResumeTimer() {
  if (listeningResumeTimer) {
    window.clearTimeout(listeningResumeTimer)
    listeningResumeTimer = 0
  }
}

function shouldResumeListening() {
  return hearingEnabled.value && hearingSupported && !listening.value && !speaking.value && !chatLoading.value
}

function scheduleListeningResume(delay = 480) {
  clearListeningResumeTimer()
  if (!shouldResumeListening()) {
    return
  }
  listeningResumeTimer = window.setTimeout(() => {
    listeningResumeTimer = 0
    if (shouldResumeListening()) {
      startListening()
    }
  }, delay)
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

function refreshVoices() {
  if (!speechSupported) {
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

  const storedVoiceUri = localStorage.getItem(voiceStorageKey) ?? ''
  const storedVoiceExists = storedVoiceUri
    ? availableVoices.value.some((voice) => voice.voiceURI === storedVoiceUri)
    : false

  const preferredVoice = findPreferredVoice(availableVoices.value)

  if (storedVoiceExists) {
    if (selectedVoiceUri.value !== storedVoiceUri) {
      updateVoicePreference(storedVoiceUri, false)
    }
    return
  }

  if (preferredVoice) {
    updateVoicePreference(preferredVoice.voiceURI, false)
  }
}

function updateVoicePreference(voiceUri: string, announceChange = true) {
  selectedVoiceUri.value = voiceUri
  hasStoredVoicePreference.value = voiceUri.length > 0

  if (voiceUri) {
    localStorage.setItem(voiceStorageKey, voiceUri)
  } else {
    localStorage.removeItem(voiceStorageKey)
  }

  if (!announceChange) {
    return
  }

  narratorMessage.value = voiceUri
    ? `已切换到 ${activeVoice.value?.name ?? preferredVoiceName}。`
    : `已恢复默认音色，当前使用 ${activeVoiceLabel.value}。`
}

function stopSpeaking() {
  if (speechSupported) {
    window.speechSynthesis.cancel()
  }
  speaking.value = false
}

function stopListeningSession() {
  clearListeningResumeTimer()
  recognition?.abort()
  recognition = null
  listening.value = false
  recognitionTranscript.value = ''
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
  narratorMessage.value = `${assistantName}已恢复收听。`
  scheduleListeningResume(120)
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
  let shouldRetry = false
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
    shouldRetry = hearingEnabled.value && event.error === 'no-speech'
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
      if (shouldRetry) {
        scheduleListeningResume(560)
      }
      return
    }

    const transcript = (finalTranscript || recognitionTranscript.value || chatForm.question).trim()
    recognitionTranscript.value = ''
    if (!transcript) {
      narratorMessage.value = `${assistantName}这次没有听清，你可以再试一次。`
      scheduleListeningResume(560)
      return
    }

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

function speakText(text: string) {
  if (!speechSupported) {
    narratorMessage.value = '当前浏览器不支持语音合成。'
    return
  }
  if (!text.trim()) {
    narratorMessage.value = '没有可朗读的内容。'
    return
  }
  stopListeningSession()
  stopSpeaking()
  const utterance = new SpeechSynthesisUtterance(text)
  if (activeVoice.value) {
    utterance.voice = activeVoice.value
    utterance.lang = activeVoice.value.lang
  } else {
    utterance.lang = 'zh-CN'
  }
  utterance.rate = 0.92
  utterance.pitch = 1.04
  utterance.volume = 1
  utterance.onstart = () => {
    speaking.value = true
    narratorMessage.value = '数字人正在开口讲解。'
  }
  utterance.onend = () => {
    speaking.value = false
    narratorMessage.value = '讲解完成。'
  }
  utterance.onerror = () => {
    speaking.value = false
    narratorMessage.value = '语音朗读失败。'
  }
  const originalOnEnd = utterance.onend
  utterance.onend = (event) => {
    originalOnEnd?.call(utterance, event)
    scheduleListeningResume(240)
  }
  const originalOnError = utterance.onerror
  utterance.onerror = (event) => {
    originalOnError?.call(utterance, event)
    scheduleListeningResume(240)
  }
  window.speechSynthesis.speak(utterance)
}

function scheduleStartupSpeech(delayMs = 720) {
  if (!autoSpeakEnabled.value || !speechSupported || startupSpeechTimer) {
    return
  }

  startupSpeechTimer = window.setTimeout(() => {
    startupSpeechTimer = 0
    if (!autoSpeakEnabled.value || speaking.value) {
      return
    }
    speakText(spokenContent.value)
  }, delayMs)
}

function announce(text: string, speak = true) {
  const content = text.trim() || '没有可播报的内容。'
  spokenContent.value = content
  narratorMessage.value = speak && autoSpeakEnabled.value ? '正在自动播报最新内容。' : '内容已更新，点击“朗读当前内容”即可播放。'
  if (speak && autoSpeakEnabled.value) {
    speakText(content)
  }
}

function openWeiwei() {
  digitalOverlayOpen.value = true
  scheduleListeningResume(120)
}

function closeDigitalOverlay() {
  digitalOverlayOpen.value = false
}

function handleWindowKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && digitalOverlayOpen.value) {
    closeDigitalOverlay()
  }
}

async function scrollToPageBody() {
  await nextTick()
  pageBodyRef.value?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

function selectView(view: ViewKey) {
  activeView.value = view
  void scrollToPageBody()
}

function restoreAdminSession() {
  const raw = localStorage.getItem('scenic-admin-profile')
  if (!raw) {
    return
  }
  try {
    adminProfile.value = JSON.parse(raw) as LoginResponse
  } catch {
    localStorage.removeItem('scenic-admin-profile')
  }
}

function persistAdminSession(profile: LoginResponse | null) {
  if (!profile) {
    localStorage.removeItem('scenic-admin-profile')
    return
  }
  localStorage.setItem('scenic-admin-profile', JSON.stringify(profile))
}

async function loadPublicData() {
  publicLoading.value = true
  publicError.value = ''
  try {
    const [overviewData, attractionData] = await Promise.all([fetchOverview(), fetchAttractions()])
    overviewData.welcomeMessage = buildAssistantWelcome(overviewData.welcomeMessage)
    overview.value = overviewData
    attractions.value = attractionData
    announce(
      `${overviewData.welcomeMessage}当前景区已准备好 ${attractionData.length} 条景点资料，热门问题包括 ${overviewData.hotQuestions.slice(0, 2).join('、')}。`,
    )
  } catch (error) {
    publicError.value = error instanceof Error ? error.message : '加载游客侧数据失败'
  } finally {
    publicLoading.value = false
  }
}

async function loadAdminData() {
  adminDataLoading.value = true
  adminDataError.value = ''
  try {
    const [dashboardData, knowledgeData, recordData] = await Promise.all([
      fetchDashboard(),
      fetchKnowledge(),
      fetchRecords(),
    ])
    dashboard.value = dashboardData
    knowledgeList.value = knowledgeData
    records.value = recordData
    announce(
      `后台数据已刷新，当前累计 ${dashboardData.conversationCount} 条问答，满意度 ${dashboardData.satisfactionRate}%，热门问题是 ${dashboardData.hotQuestions.slice(0, 2).map((item) => item.name).join('、')}。`,
    )
  } catch (error) {
    adminDataError.value = error instanceof Error ? error.message : '加载后台数据失败'
  } finally {
    adminDataLoading.value = false
  }
}

function fillQuestion(question: string) {
  selectView('chat')
  chatForm.question = question
}

function useAttractionQuestion(attraction: Attraction) {
  fillQuestion(`${attraction.name}有什么亮点？`)
}

async function submitChat(question?: string) {
  if (question) {
    chatForm.question = question
  }
  if (!chatForm.question.trim()) {
    chatError.value = '请输入你想咨询的问题'
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
    chatError.value = error instanceof Error ? error.message : '问答请求失败'
  } finally {
    chatLoading.value = false
    if (!autoSpeakEnabled.value) {
      scheduleListeningResume(180)
    }
  }
}

async function submitFeedback(helpful: boolean) {
  if (!chatResponse.value) {
    return
  }

  try {
    await submitFeedbackRequest({
      recordId: chatResponse.value.recordId,
      helpful,
      comment: feedbackComment.value.trim(),
    })
    feedbackStatus.value = helpful ? 'helpful' : 'unhelpful'
    feedbackMessage.value = helpful ? '感谢认可，我们会保持这个回答风格。' : '收到，我们会继续优化知识内容和回答方式。'
  } catch (error) {
    feedbackMessage.value = error instanceof Error ? error.message : '提交反馈失败'
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
    const firstStop = routeResponse.value.stops[0]
    announce(
      firstStop
        ? `${routeResponse.value.title}。第一站是 ${firstStop.attractionName}，建议停留 ${firstStop.duration}。`
        : `${routeResponse.value.title}。`,
    )
  } catch (error) {
    routeError.value = error instanceof Error ? error.message : '路线推荐失败'
  } finally {
    routeLoading.value = false
  }
}

async function loginToAdmin() {
  adminLoading.value = true
  adminMessage.value = ''
  try {
    const profile = await adminLogin({
      username: adminForm.username.trim(),
      password: adminForm.password,
    })
    adminProfile.value = profile
    persistAdminSession(profile)
    adminMessage.value = `欢迎回来，${profile.displayName}`
    announce(`欢迎回来，${profile.displayName}，我已经帮你准备好后台看板。`)
    await loadAdminData()
  } catch (error) {
    adminMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    adminLoading.value = false
  }
}

function logoutAdmin() {
  adminProfile.value = null
  dashboard.value = null
  records.value = []
  knowledgeList.value = []
  persistAdminSession(null)
  resetKnowledgeForm()
  adminMessage.value = '已退出后台演示会话'
}

function resetKnowledgeForm() {
  knowledgeForm.id = null
  knowledgeForm.title = ''
  knowledgeForm.category = 'FAQ'
  knowledgeForm.keywords = ''
  knowledgeForm.content = ''
  knowledgeForm.source = '后台补录'
  knowledgeForm.published = true
}

function editKnowledge(entry: KnowledgeEntry) {
  selectView('knowledge')
  knowledgeForm.id = entry.id
  knowledgeForm.title = entry.title
  knowledgeForm.category = entry.category
  knowledgeForm.keywords = entry.keywords.join('，')
  knowledgeForm.content = entry.content
  knowledgeForm.source = entry.source
  knowledgeForm.published = entry.published
}

async function saveKnowledge() {
  const payload = {
    title: knowledgeForm.title.trim(),
    category: knowledgeForm.category,
    keywords: knowledgeForm.keywords
      .split(/[，,]/)
      .map((item) => item.trim())
      .filter(Boolean),
    content: knowledgeForm.content.trim(),
    source: knowledgeForm.source.trim(),
    published: knowledgeForm.published,
  }

  if (!payload.title || !payload.content || !payload.source) {
    adminMessage.value = '请完整填写知识标题、内容和来源'
    return
  }

  knowledgeSaving.value = true
  adminMessage.value = ''
  try {
    if (knowledgeForm.id == null) {
      await createKnowledge(payload)
      adminMessage.value = '知识条目新增成功'
    } else {
      await updateKnowledge(knowledgeForm.id, payload)
      adminMessage.value = '知识条目更新成功'
    }
    resetKnowledgeForm()
    await loadAdminData()
  } catch (error) {
    adminMessage.value = error instanceof Error ? error.message : '保存知识条目失败'
  } finally {
    knowledgeSaving.value = false
  }
}

async function removeKnowledgeItem(id: number) {
  const confirmed = window.confirm('确认删除这条知识内容吗？')
  if (!confirmed) {
    return
  }
  try {
    await deleteKnowledge(id)
    adminMessage.value = '知识条目已删除'
    if (knowledgeForm.id === id) {
      resetKnowledgeForm()
    }
    await loadAdminData()
  } catch (error) {
    adminMessage.value = error instanceof Error ? error.message : '删除知识条目失败'
  }
}

function formatDateTime(value: string) {
  if (!value) {
    return '暂无'
  }
  return value.replace('T', ' ')
}

onMounted(async () => {
  restoreAdminSession()
  if (speechSupported) {
    refreshVoices()
    window.speechSynthesis.addEventListener('voiceschanged', refreshVoices)
    window.setTimeout(refreshVoices, 180)
  }
  window.addEventListener('keydown', handleWindowKeydown)
  await loadPublicData()
  await submitRoute()
  if (adminProfile.value) {
    await loadAdminData()
  }
  scheduleStartupSpeech()
  scheduleListeningResume(900)
})

onBeforeUnmount(() => {
  if (startupSpeechTimer) {
    window.clearTimeout(startupSpeechTimer)
  }
  stopSpeaking()
  stopListeningSession()
  if (speechSupported) {
    window.speechSynthesis.removeEventListener('voiceschanged', refreshVoices)
  }
  window.removeEventListener('keydown', handleWindowKeydown)
  document.body.style.overflow = ''
})

watch(selectedVoiceUri, (voiceUri) => {
  if (voiceUri && !availableVoices.value.some((voice) => voice.voiceURI === voiceUri)) {
    updateVoicePreference('', false)
  }
})

watch(digitalOverlayOpen, (isOpen) => {
  document.body.style.overflow = isOpen ? 'hidden' : ''
})
</script>

<template>
  <div class="app-shell">
    <StrandsBackground
      class="page-strands page-strands-fixed"
      :colors="['#F97316', '#7C3AED', '#06B6D4', '#EAB308']"
      :count="4"
      :speed="0.34"
      :amplitude="1.16"
      :waviness="1.08"
      :thickness="0.64"
      :glow="2.2"
      :taper="2.7"
      :spread="1.18"
      :intensity="0.42"
      :saturation="1.32"
      :opacity="0.42"
      :scale="1.08"
    />
    <RibbonsCursor v-if="activeView === 'home' || activeView === 'route'" />

    <header
      class="hero-band"
      :style="{
        backgroundImage: `linear-gradient(90deg, rgba(21, 38, 31, 0.88), rgba(21, 38, 31, 0.48)), url(${heroImage})`,
      }"
    >
      <StrandsBackground
        class="page-strands hero-strands"
        :colors="['#F97316', '#7C3AED', '#06B6D4']"
        :count="3"
        :speed="0.42"
        :amplitude="0.92"
        :waviness="1.08"
        :thickness="0.58"
        :glow="2.45"
        :taper="3"
        :spread="1.08"
        :intensity="0.46"
        :saturation="1.42"
        :opacity="0.58"
        :scale="1.35"
      />
      <div class="hero-content">
        <div class="hero-main">
          <div>
            <p class="eyebrow">灵山胜境导览系统</p>
            <h1 class="hero-title-block">
              <GradientTextTitle
                text="景区导览服务（AI 数字人）"
                :colors="['#f5eefe', '#c084fc', '#8b5cf6', '#f5eefe']"
                :animation-speed="5"
                direction="horizontal"
              />
            </h1>
            <p class="hero-copy">
              {{ overview?.welcomeMessage ?? '你好，我是灵山胜境中文导览助手，正在为你准备景点讲解、路线推荐与后台运营看板。' }}
            </p>
            <div class="hero-actions">
              <button type="button" class="primary-button hero-button" @click="openWeiwei">
                打开薇薇
              </button>
              <button type="button" class="ghost-button hero-button" @click="selectView('chat')">
                开始提问
              </button>
            </div>
          </div>
          <div class="hero-metrics" aria-label="游客端核心状态">
            <article v-for="item in visitorStats" :key="item.label" class="hero-metric glare-hover">
              <span>{{ item.label }}</span>
              <strong><CountUpValue :value="item.value" separator="," /></strong>
              <p>{{ item.description }}</p>
            </article>
          </div>
        </div>
        <nav class="nav-strip" aria-label="系统导航">
          <button
            v-for="item in navItems"
            :key="item.key"
            type="button"
            class="nav-button"
            :class="{ active: activeView === item.key }"
            @click="selectView(item.key)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>
    </header>

    <aside class="digital-stage" :class="[selectedSkin, { speaking, expanded: digitalOverlayOpen }]" aria-label="数字人导览官">
      <div v-if="digitalOverlayOpen" class="digital-overlay-backdrop" @click="closeDigitalOverlay"></div>
      <div class="digital-shell" :class="[selectedSkin, { speaking, expanded: digitalOverlayOpen }]">
        <button
          v-if="digitalOverlayOpen"
          type="button"
          class="digital-overlay-close"
          aria-label="关闭数字人面板"
          @click="closeDigitalOverlay"
        >
          ×
        </button>
        <div class="digital-stage-host" aria-label="数字人">
          <DigitalHumanStage :skin="selectedSkin" :speaking="speaking" />
          <button
            v-if="!digitalOverlayOpen"
            type="button"
            class="digital-human-hint"
            @click.stop="openWeiwei"
          >
            打开薇薇
          </button>
        </div>
        <div v-show="digitalOverlayOpen" class="digital-console">
          <div class="digital-status-row">
            <span class="score-badge">{{ digitalActivityLabel }}</span>
            <span class="hearing-status">{{ hearingStatusLabel }}</span>
            <label class="auto-switch">
              <input v-model="autoSpeakEnabled" type="checkbox" />
              <span>自动播报</span>
            </label>
          </div>
          <div class="digital-speech">
            <p class="digital-label">灵山中文数字导览官</p>
            <p class="digital-line">{{ spokenContent }}</p>
            <p class="digital-note">{{ narratorMessage }}</p>
          </div>
          <div v-if="speechSupported" class="voice-strip">
            <span class="skin-title">声音</span>
            <select v-model="selectedVoiceUri" class="text-input voice-select" @change="updateVoicePreference(selectedVoiceUri)">
              <option value="">{{ preferredVoicePlaceholder }}</option>
              <option v-for="voice in voiceOptions" :key="voice.voiceURI" :value="voice.voiceURI">
                {{ voice.label }}
              </option>
            </select>
            <p class="voice-note">当前：{{ activeVoiceLabel }}</p>
          </div>
          <div class="action-row compact">
            <button type="button" class="primary-button" :disabled="!speechSupported || speaking" @click="speakText(spokenContent)">
              朗读当前内容
            </button>
            <button
              type="button"
              class="ghost-button hearing-button"
              :disabled="!hearingSupported"
              @click="toggleHearing"
            >
              {{ hearingToggleLabel }}
            </button>
            <button type="button" class="ghost-button" @click="stopSpeaking">停止朗读</button>
          </div>
          <div class="status-mini">
            <div class="status-line">
              <span>景区主题</span>
              <strong>{{ scenicName }}</strong>
            </div>
            <div class="status-line">
              <span>后端地址</span>
              <strong>{{ apiBase }}</strong>
            </div>
            <div class="status-line">
              <span>模型模式</span>
              <strong>优先加载 GLB</strong>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <main ref="pageBodyRef" class="page-body">
      <section v-if="publicError" class="notice notice-error">
        <strong>游客侧数据加载失败：</strong>{{ publicError }}
      </section>

      <section v-if="activeView === 'home'" class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">游客端概览</p>
            <h2><ScrollFloatText text="从问答到路线，一屏看清当前系统骨架" /></h2>
          </div>
          <button type="button" class="ghost-button" @click="loadPublicData">刷新游客数据</button>
        </div>

        <div class="stats-grid">
          <article v-for="item in summaryStats" :key="item.label" class="stat-card">
            <span class="stat-label">{{ item.label }}</span>
            <strong class="stat-value"><CountUpValue :value="item.value" separator="," /></strong>
            <p class="muted-text">{{ item.description }}</p>
          </article>
        </div>

        <div class="page-subsection">
          <section v-if="false" class="content-panel">
            <div class="panel-head">
              <h3>热门提问</h3>
              <span class="panel-tip">点击后直接带入问答页</span>
            </div>
            <div class="chip-row">
              <button
                v-for="question in overview?.hotQuestions ?? []"
                :key="question"
                type="button"
                class="chip-button"
                @click="fillQuestion(question)"
              >
                {{ question }}
              </button>
            </div>
            <div class="scope-list">
              <span v-for="scope in overview?.mvpScope ?? []" :key="scope" class="scope-chip">{{ scope }}</span>
            </div>
          </section>

          <section class="content-panel">
            <div class="panel-head">
              <h3>系统建议推进</h3>
              <span class="panel-tip">当前版本最适合先做完整演示链路</span>
            </div>
            <ol class="step-list">
              <li>先用后端现有数据把游客首页、问答页和路线页做完整。</li>
              <li>后台先承接看板、记录和知识条目维护。</li>
              <li>等演示链路稳定后，再接入大模型、知识切片和语音能力。</li>
            </ol>
          </section>
        </div>

        <section class="page-subsection">
          <div class="panel-head">
            <h3>景点资料</h3>
            <span class="panel-tip">用于问答命中与路线推荐</span>
          </div>
          <div class="attraction-grid">
            <article v-for="item in attractions" :key="item.id" class="attraction-card">
              <div class="attraction-top">
                <div>
                  <h4>{{ item.name }}</h4>
                  <p class="muted-text">{{ item.area }} · {{ item.theme }}</p>
                </div>
                <span class="score-badge">热度 {{ item.popularityScore }}</span>
              </div>
              <p class="body-text">{{ item.highlight }}</p>
              <div class="tag-row">
                <span v-for="tag in item.tags" :key="tag" class="tag-chip">{{ tag }}</span>
              </div>
              <p class="muted-text">建议停留 {{ item.suggestedDurationMinutes }} 分钟，开放时间 {{ item.openHours }}</p>
              <div class="action-row">
                <button type="button" class="primary-button" @click="useAttractionQuestion(item)">问这个景点</button>
                <button type="button" class="ghost-button" @click="selectView('route')">用于路线推荐</button>
              </div>
            </article>
          </div>
        </section>
      </section>

      <section v-else-if="activeView === 'chat'" class="page-section inquiry-section">
        <TextCursorTrail text="问" class="inquiry-cursor-field" :spacing="72" :max-points="9">
          <div class="section-head">
            <div>
              <p class="section-kicker">智能问答</p>
              <h2><ScrollFloatText text="中文提问、中文回答、中文反馈闭环" /></h2>
            </div>
          </div>

          <div class="dual-grid">
          <section class="content-panel">
            <div class="panel-head">
              <h3>提问面板</h3>
              <span class="panel-tip">当前问答基于本地景区资料规则匹配</span>
            </div>
            <label class="field-label">
              <span>输入问题</span>
              <textarea
                v-model="chatForm.question"
                class="text-input textarea"
                rows="6"
                placeholder="例如：灵山大佛有什么亮点？我带长辈怎么安排路线更轻松？"
              />
            </label>
            <div class="action-row">
              <button type="button" class="primary-button" :disabled="chatLoading" @click="submitChat()">
                {{ chatLoading ? '正在生成回答...' : '提交问题' }}
              </button>
              <button type="button" class="ghost-button" @click="chatForm.question = ''">清空问题</button>
            </div>
            <p v-if="chatError" class="notice-inline">{{ chatError }}</p>
            <div class="chip-row compact-gap">
              <button
                v-for="question in overview?.hotQuestions ?? []"
                :key="question"
                type="button"
                class="chip-button"
                @click="submitChat(question)"
              >
                {{ question }}
              </button>
            </div>
          </section>

          <section class="content-panel">
            <div class="panel-head">
              <h3>回答结果</h3>
              <span class="panel-tip">来源和追问建议都保留中文</span>
            </div>

            <div v-if="chatResponse" class="chat-result">
              <div class="result-head">
                <div>
                  <strong>{{ chatResponse.question }}</strong>
                  <p class="muted-text">情绪识别：{{ chatResponse.emotion }}</p>
                </div>
                <span class="score-badge">记录 ID {{ chatResponse.recordId }}</span>
              </div>
              <p class="answer-text">{{ chatResponse.answer }}</p>

              <div v-if="chatResponse.sources.length > 0" class="source-list">
                <article v-for="source in chatResponse.sources" :key="source.title" class="source-card">
                  <h4>{{ source.title }}</h4>
                  <p class="body-text">{{ source.excerpt }}</p>
                  <span class="muted-text">来源：{{ source.source }}</span>
                </article>
              </div>

              <div class="follow-up">
                <h4>推荐继续追问</h4>
                <div class="chip-row compact-gap">
                  <button
                    v-for="suggestion in chatResponse.suggestions"
                    :key="suggestion"
                    type="button"
                    class="chip-button"
                    @click="submitChat(suggestion)"
                  >
                    {{ suggestion }}
                  </button>
                </div>
              </div>

              <div class="feedback-block">
                <div class="action-row">
                  <button
                    type="button"
                    class="ghost-button"
                    :class="{ active: feedbackStatus === 'helpful' }"
                    @click="submitFeedback(true)"
                  >
                    有帮助
                  </button>
                  <button
                    type="button"
                    class="ghost-button"
                    :class="{ active: feedbackStatus === 'unhelpful' }"
                    @click="submitFeedback(false)"
                  >
                    还可以更好
                  </button>
                </div>
                <label class="field-label">
                  <span>补充意见</span>
                  <textarea
                    v-model="feedbackComment"
                    class="text-input textarea small-textarea"
                    rows="3"
                    placeholder="例如：希望路线更具体，或者解释再浅显一点"
                  />
                </label>
                <p v-if="feedbackMessage" class="muted-text">{{ feedbackMessage }}</p>
              </div>
            </div>

            <div v-else class="empty-state">
              <h3>还没有回答结果</h3>
              <p>输入一个和灵山胜境有关的问题，系统会优先依据本地景区资料生成中文回答。</p>
            </div>
          </section>
          </div>
        </TextCursorTrail>
      </section>

      <section v-else-if="activeView === 'route'" class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">路线推荐</p>
            <h2><ScrollFloatText text="按兴趣、时长和同行类型生成中文路线" /></h2>
          </div>
        </div>

        <div class="dual-grid">
          <section class="content-panel">
            <div class="panel-head">
              <h3>推荐条件</h3>
              <span class="panel-tip">当前逻辑基于标签、热度与步行强度</span>
            </div>
            <div class="field-group">
              <span class="field-title">兴趣标签</span>
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
              <button type="button" class="primary-button" :disabled="routeLoading" @click="submitRoute">
                {{ routeLoading ? '正在生成路线...' : '生成路线' }}
              </button>
            </div>
            <p v-if="routeError" class="notice-inline">{{ routeError }}</p>
          </section>

          <section class="content-panel">
            <div class="panel-head">
              <h3>推荐结果</h3>
              <span class="panel-tip">适合直接用于演示讲解</span>
            </div>

            <div v-if="routeResponse" class="route-result">
              <div class="result-head">
                <div>
                  <strong>{{ routeResponse.title }}</strong>
                  <p class="muted-text">推荐总时长：{{ routeResponse.totalDurationHours }} 小时</p>
                </div>
              </div>

              <ScenicMap
                :attractions="attractions"
                :route-stops="routeResponse.stops"
                :scenic-name="scenicName"
              />

              <ol class="route-list">
                <li v-for="stop in routeResponse.stops" :key="stop.order" class="route-item">
                  <div class="route-order">{{ stop.order }}</div>
                  <div class="route-body">
                    <div class="route-head">
                      <strong>{{ stop.attractionName }}</strong>
                      <span class="muted-text">{{ stop.duration }}</span>
                    </div>
                    <p class="body-text">{{ stop.highlight }}</p>
                    <p class="muted-text">{{ stop.reason }}</p>
                  </div>
                </li>
              </ol>

              <div class="tips-block">
                <h4>路线提示</h4>
                <ul class="plain-list">
                  <li v-for="tip in routeResponse.tips" :key="tip">{{ tip }}</li>
                </ul>
              </div>
            </div>

            <div v-else class="empty-state">
              <h3>还没有路线结果</h3>
              <p>选好偏好后点击“生成路线”，系统会输出一条适合当前条件的中文游览方案。</p>
            </div>
          </section>
        </div>
      </section>

      <section v-else-if="activeView === 'dashboard'" class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">后台看板</p>
            <h2><ScrollFloatText text="用中文看清问答量、满意度和运营热点" /></h2>
          </div>
        </div>

        <section v-if="!adminProfile" class="content-panel">
          <div class="panel-head">
            <h3>后台登录</h3>
            <span class="panel-tip">当前是演示登录，便于快速联调和展示</span>
          </div>
          <div class="form-grid">
            <label class="field-label">
              <span>账号</span>
              <input v-model="adminForm.username" class="text-input" placeholder="请输入账号" />
            </label>
            <label class="field-label">
              <span>密码</span>
              <input v-model="adminForm.password" type="password" class="text-input" placeholder="请输入密码" />
            </label>
          </div>
          <div class="action-row">
            <button type="button" class="primary-button" :disabled="adminLoading" @click="loginToAdmin">
              {{ adminLoading ? '正在登录...' : '登录后台' }}
            </button>
            <span class="muted-text">演示账号：admin / admin123</span>
          </div>
          <p v-if="adminMessage" class="notice-inline">{{ adminMessage }}</p>
        </section>

        <template v-else>
          <section class="content-panel">
            <div class="section-head compact">
              <div>
                <h3>欢迎，{{ adminProfile.displayName }}</h3>
                <p class="muted-text">你可以刷新后台数据、查看记录，或进入知识库继续维护内容。</p>
              </div>
              <div class="action-row">
                <button type="button" class="ghost-button" @click="loadAdminData">刷新数据</button>
                <button type="button" class="ghost-button" @click="logoutAdmin">退出后台</button>
              </div>
            </div>
            <p v-if="adminMessage" class="muted-text">{{ adminMessage }}</p>
            <p v-if="adminDataError" class="notice-inline">{{ adminDataError }}</p>
          </section>

          <section v-if="dashboard" class="page-subsection">
            <div class="stats-grid">
              <article class="stat-card">
                <span class="stat-label">问答总量</span>
                <strong class="stat-value"><CountUpValue :value="dashboard.conversationCount" separator="," /></strong>
                <p class="muted-text">游客问答记录累计数量</p>
              </article>
              <article class="stat-card">
                <span class="stat-label">反馈总量</span>
                <strong class="stat-value"><CountUpValue :value="dashboard.feedbackCount" separator="," /></strong>
                <p class="muted-text">累计已收集到的游客反馈</p>
              </article>
              <article class="stat-card">
                <span class="stat-label">平均响应</span>
                <strong class="stat-value"><CountUpValue :value="dashboard.avgResponseMillis" separator="," /> ms</strong>
                <p class="muted-text">当前问答平均处理耗时</p>
              </article>
              <article class="stat-card">
                <span class="stat-label">满意度</span>
                <strong class="stat-value"><CountUpValue :value="dashboard.satisfactionRate" />%</strong>
                <p class="muted-text">根据反馈推算出的满意度</p>
              </article>
            </div>

            <div class="triple-grid">
              <section class="content-panel">
                <div class="panel-head">
                  <h3>近 7 天问答趋势</h3>
                  <span class="panel-tip">峰值按相对长度展示</span>
                </div>
                <div class="trend-list">
                  <div v-for="item in dashboard.weeklyTrend" :key="item.label" class="trend-row">
                    <span>{{ item.label }}</span>
                    <div class="trend-bar">
                      <div class="trend-fill" :style="{ width: `${(item.value / topTrendValue) * 100}%` }" />
                    </div>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </section>

              <section class="content-panel">
                <div class="panel-head">
                  <h3>情绪与热门景点</h3>
                  <span class="panel-tip">方便观察用户感受和关注点</span>
                </div>
                <ul class="ranking-list">
                  <li v-for="item in dashboard.emotionDistribution" :key="`emotion-${item.name}`">
                    <span>{{ item.name }}</span>
                    <strong>{{ item.value }}</strong>
                  </li>
                </ul>
                <ul class="ranking-list secondary">
                  <li v-for="item in dashboard.topAttractions" :key="`attraction-${item.name}`">
                    <span>{{ item.name }}</span>
                    <strong>{{ item.value }}</strong>
                  </li>
                </ul>
              </section>

              <section class="content-panel">
                <div class="panel-head">
                  <h3>热门问题</h3>
                  <span class="panel-tip">可直接沉淀进知识库优化</span>
                </div>
                <ul class="ranking-list">
                  <li v-for="item in dashboard.hotQuestions" :key="`question-${item.name}`">
                    <span>{{ item.name }}</span>
                    <strong>{{ item.value }}</strong>
                  </li>
                </ul>
              </section>
            </div>
          </section>

          <section class="page-subsection">
            <div class="panel-head">
              <h3>最新问答记录</h3>
              <span class="panel-tip">便于运营复盘当前回答质量</span>
            </div>
            <div class="record-list">
              <article v-for="record in records.slice(0, 8)" :key="record.id" class="record-card">
                <div class="record-head">
                  <strong>{{ record.question }}</strong>
                  <span class="score-badge">{{ record.emotion }}</span>
                </div>
                <p class="body-text">{{ record.answer }}</p>
                <p class="muted-text">
                  来源：{{ record.matchedSource }} · 耗时：{{ record.responseMillis }} ms · 时间：{{ formatDateTime(record.createdAt) }}
                </p>
              </article>
            </div>
          </section>
        </template>
      </section>

      <section v-else class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">知识库管理</p>
            <h2><ScrollFloatText text="中文内容编辑与后台维护放在同一视图" /></h2>
          </div>
        </div>

        <section v-if="!adminProfile" class="content-panel">
          <div class="empty-state">
            <h3>请先登录后台</h3>
            <p>知识库维护依赖后台演示会话。你可以先进入“后台看板”完成登录，再回来编辑内容。</p>
            <button type="button" class="primary-button" @click="selectView('dashboard')">去后台看板登录</button>
          </div>
        </section>

        <template v-else>
          <div class="dual-grid">
            <section class="content-panel">
              <div class="panel-head">
                <h3>{{ knowledgeForm.id == null ? '新增知识条目' : '编辑知识条目' }}</h3>
                <span class="panel-tip">内容、来源、关键词都建议保持中文</span>
              </div>
              <div class="form-grid">
                <label class="field-label">
                  <span>知识标题</span>
                  <input v-model="knowledgeForm.title" class="text-input" placeholder="例如：灵山大佛讲解词" />
                </label>
                <label class="field-label">
                  <span>知识分类</span>
                  <select v-model="knowledgeForm.category" class="text-input">
                    <option v-for="option in categoryOptions" :key="option" :value="option">{{ option }}</option>
                  </select>
                </label>
                <label class="field-label field-span-2">
                  <span>关键词</span>
                  <input v-model="knowledgeForm.keywords" class="text-input" placeholder="多个关键词请用中文逗号分隔" />
                </label>
                <label class="field-label field-span-2">
                  <span>知识来源</span>
                  <input v-model="knowledgeForm.source" class="text-input" placeholder="例如：运营FAQ / 游览指南 / 后台补录" />
                </label>
                <label class="field-label field-span-2">
                  <span>知识内容</span>
                  <textarea
                    v-model="knowledgeForm.content"
                    class="text-input textarea"
                    rows="8"
                    placeholder="请直接填写完整的中文知识内容"
                  />
                </label>
              </div>
              <label class="check-row">
                <input v-model="knowledgeForm.published" type="checkbox" />
                <span>发布后可参与问答匹配</span>
              </label>
              <div class="action-row">
                <button type="button" class="primary-button" :disabled="knowledgeSaving" @click="saveKnowledge">
                  {{ knowledgeSaving ? '正在保存...' : knowledgeForm.id == null ? '新增条目' : '保存修改' }}
                </button>
                <button type="button" class="ghost-button" @click="resetKnowledgeForm">重置表单</button>
              </div>
              <p v-if="adminMessage" class="muted-text">{{ adminMessage }}</p>
            </section>

            <section class="content-panel">
              <div class="panel-head">
                <h3>现有知识条目</h3>
                <span class="panel-tip">点击即可编辑或删除</span>
              </div>
              <div class="knowledge-list">
                <article v-for="entry in knowledgeList" :key="entry.id" class="knowledge-card">
                  <div class="record-head">
                    <div>
                      <strong>{{ entry.title }}</strong>
                      <p class="muted-text">{{ entry.category }} · {{ entry.source }}</p>
                    </div>
                    <span class="score-badge">{{ entry.published ? '已发布' : '未发布' }}</span>
                  </div>
                  <p class="body-text">{{ entry.content }}</p>
                  <div class="tag-row">
                    <span v-for="keyword in entry.keywords" :key="keyword" class="tag-chip">{{ keyword }}</span>
                  </div>
                  <p class="muted-text">更新时间：{{ formatDateTime(entry.updatedAt) }}</p>
                  <div class="action-row">
                    <button type="button" class="ghost-button" @click="editKnowledge(entry)">编辑</button>
                    <button type="button" class="ghost-button danger" @click="removeKnowledgeItem(entry.id)">删除</button>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </template>
      </section>

      <StrandsBackground
        class="page-strands page-strands-bottom"
        :colors="['#06B6D4', '#F97316', '#7C3AED', '#EAB308']"
        :count="5"
        :speed="0.36"
        :amplitude="1.05"
        :waviness="1.18"
        :thickness="0.62"
        :glow="2.55"
        :taper="2.8"
        :spread="1.24"
        :intensity="0.5"
        :saturation="1.35"
        :opacity="0.66"
        :scale="1.1"
      />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.hero-band {
  background-position: center;
  background-size: cover;
  color: #f5f5f0;
}

.hero-content,
.page-body {
  width: min(1180px, calc(100vw - 32px));
  margin: 0 auto;
}

.hero-content {
  padding: 28px 0 20px;
}

.hero-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  align-items: end;
}

.eyebrow,
.section-kicker {
  margin: 0 0 10px;
  font-size: 13px;
  color: rgba(245, 245, 240, 0.82);
}

h1,
h2,
h3,
h4,
p {
  margin: 0;
}

h1 {
  font-size: 40px;
  line-height: 1.2;
}

h2 {
  font-size: 28px;
  color: #18261d;
}

h3 {
  font-size: 20px;
  color: #18261d;
}

h4 {
  font-size: 16px;
  color: #18261d;
}

.hero-copy {
  margin-top: 14px;
  max-width: 720px;
  font-size: 16px;
  line-height: 1.7;
  color: rgba(245, 245, 240, 0.9);
}

.digital-stage {
  --cookie-panel: rgba(255, 248, 242, 0.96);
  --cookie-line: #e1d2c3;
  --cookie-text: #5d4030;
  --cookie-soft: #8d6d5d;
  position: fixed;
  right: 12px;
  bottom: 12px;
  z-index: 40;
  width: min(430px, calc(100vw - 24px));
  height: 560px;
  pointer-events: none;
  overflow: visible;
}

.digital-stage.expanded {
  inset: 0;
  z-index: 90;
  width: auto;
  height: auto;
}

.digital-overlay-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(22, 27, 22, 0.4);
  backdrop-filter: blur(8px);
  pointer-events: auto;
}

.digital-shell {
  position: relative;
  width: 100%;
  height: 100%;
}

.digital-shell.expanded {
  width: min(1240px, calc(100vw - 40px));
  height: min(88vh, 860px);
  margin: 20px auto;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(320px, 420px);
  gap: 20px;
  align-items: stretch;
  pointer-events: auto;
}

.digital-shell.expanded.caramel {
  --cookie-panel: rgba(255, 248, 242, 0.97);
  --cookie-line: #e1d2c3;
  --cookie-text: #5d4030;
  --cookie-soft: #8d6d5d;
}

.digital-shell.expanded.mint {
  --cookie-panel: rgba(242, 251, 248, 0.97);
  --cookie-line: #cfe0d9;
  --cookie-text: #3b544a;
  --cookie-soft: #6a8177;
}

.digital-shell.expanded.berry {
  --cookie-panel: rgba(255, 244, 248, 0.97);
  --cookie-line: #e9d1db;
  --cookie-text: #6a4050;
  --cookie-soft: #966b7d;
}

.digital-shell.expanded.cocoa {
  --cookie-panel: rgba(248, 242, 238, 0.97);
  --cookie-line: #dccabf;
  --cookie-text: #503a30;
  --cookie-soft: #7d695f;
}

.digital-stage-host {
  position: relative;
  width: 100%;
  height: 100%;
  filter: drop-shadow(0 26px 42px rgba(33, 28, 23, 0.2));
  pointer-events: auto;
}

.digital-stage-host[role='button'] {
  cursor: pointer;
}

.digital-human-hint {
  position: absolute;
  right: 18px;
  bottom: 174px;
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid rgba(255, 255, 255, 0.66);
  border-radius: 999px;
  background: rgba(255, 248, 242, 0.94);
  color: var(--cookie-text);
  font-size: 14px;
  box-shadow: 0 12px 30px rgba(33, 28, 23, 0.12);
  backdrop-filter: blur(8px);
}

.digital-shell.expanded .digital-stage-host {
  min-width: 0;
  min-height: 0;
  border-radius: 30px;
  background:
    radial-gradient(circle at 50% 22%, rgba(255, 255, 255, 0.86), rgba(255, 255, 255, 0) 42%),
    radial-gradient(circle at 50% 84%, rgba(236, 192, 146, 0.18), rgba(236, 192, 146, 0) 32%);
  overflow: hidden;
  cursor: default;
}

.digital-overlay-close {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 2;
  width: 44px;
  height: 44px;
  border: 1px solid var(--cookie-line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--cookie-text);
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 12px 24px rgba(19, 29, 23, 0.12);
}

.digital-console {
  position: relative;
  width: 100%;
  max-height: none;
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--cookie-line);
  border-radius: 18px;
  background: var(--cookie-panel);
  color: var(--cookie-text);
  backdrop-filter: blur(10px);
  box-shadow: 0 24px 60px rgba(19, 29, 23, 0.2);
  overflow: auto;
  pointer-events: auto;
}

.digital-stage.caramel {
  --cookie-panel: rgba(255, 248, 242, 0.96);
  --cookie-line: #e1d2c3;
  --cookie-text: #5d4030;
  --cookie-soft: #8d6d5d;
}

.digital-stage.mint {
  --cookie-panel: rgba(242, 251, 248, 0.96);
  --cookie-line: #cfe0d9;
  --cookie-text: #3b544a;
  --cookie-soft: #6a8177;
}

.digital-stage.berry {
  --cookie-panel: rgba(255, 244, 248, 0.96);
  --cookie-line: #e9d1db;
  --cookie-text: #6a4050;
  --cookie-soft: #966b7d;
}

.digital-stage.cocoa {
  --cookie-panel: rgba(248, 242, 238, 0.96);
  --cookie-line: #dccabf;
  --cookie-text: #503a30;
  --cookie-soft: #7d695f;
}

.digital-stage.speaking .digital-human-hint,
.digital-shell.expanded.speaking .digital-console {
  border-color: rgba(212, 177, 98, 0.45);
  box-shadow: 0 0 0 1px rgba(212, 177, 98, 0.14) inset;
}

.digital-status-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.muted-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--cookie-line);
  background: #f7f8f4;
  color: var(--cookie-soft);
  font-size: 13px;
}

.auto-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--cookie-line);
  background: #f7f8f4;
  color: var(--cookie-soft);
  font-size: 13px;
  border-radius: 999px;
}


.digital-speech {
  display: grid;
  gap: 8px;
}

.digital-label {
  font-size: 14px;
  color: var(--cookie-soft);
}

.digital-line {
  min-height: 84px;
  color: var(--cookie-text);
  font-size: 16px;
  line-height: 1.75;
}

.digital-note {
  color: var(--cookie-soft);
  font-size: 13px;
}

.voice-strip {
  display: grid;
  gap: 8px;
}

.voice-select {
  min-height: 40px;
  font-size: 13px;
  color: var(--cookie-text);
  border-color: var(--cookie-line);
  background: rgba(255, 255, 255, 0.92);
}

.voice-note {
  margin: 0;
  color: var(--cookie-soft);
  font-size: 13px;
}

.status-mini {
  display: grid;
  gap: 8px;
}

.status-line {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 14px;
}

.status-line span,
.status-line strong {
  color: var(--cookie-soft);
}

.skin-strip {
  display: grid;
  gap: 8px;
}

.skin-title {
  font-size: 13px;
  color: var(--cookie-soft);
}

.nav-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.nav-button,
.primary-button,
.ghost-button,
.chip-button {
  min-height: 40px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: 0.2s ease;
}

.nav-button {
  padding: 0 16px;
  color: #f5f5f0;
  background: rgba(245, 245, 240, 0.08);
  border-color: rgba(245, 245, 240, 0.14);
}

.nav-button.active,
.nav-button:hover {
  background: rgba(212, 177, 98, 0.18);
  border-color: rgba(212, 177, 98, 0.45);
}

.page-body {
  padding: 24px 0 420px;
}

.page-section,
.page-subsection {
  display: grid;
  gap: 20px;
}

.page-subsection {
  margin-top: 8px;
}

.section-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
}

.section-head.compact {
  align-items: center;
}

.stats-grid,
.dual-grid,
.triple-grid,
.attraction-grid {
  display: grid;
  gap: 16px;
}

.stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dual-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.triple-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.attraction-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.content-panel,
.stat-card,
.attraction-card,
.source-card,
.record-card,
.knowledge-card {
  border: 1px solid #d7ddd2;
  background: #ffffff;
}

.content-panel,
.stat-card {
  padding: 18px;
}

.attraction-card,
.record-card,
.knowledge-card,
.source-card {
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-tip,
.muted-text {
  color: #5d6b61;
  font-size: 14px;
  line-height: 1.6;
}

.body-text,
.answer-text {
  color: #223228;
  line-height: 1.7;
}

.answer-text {
  font-size: 16px;
}

.stat-label {
  display: block;
  margin-bottom: 10px;
  color: #5d6b61;
  font-size: 14px;
}

.stat-value {
  display: block;
  margin-bottom: 8px;
  font-size: 28px;
  color: #18261d;
}

.chip-row,
.tag-row,
.action-row,
.scope-list,
.source-list,
.record-list,
.knowledge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.action-row.compact {
  justify-content: center;
}

.compact-gap {
  gap: 8px;
}

.chip-button,
.scope-chip,
.tag-chip,
.score-badge {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  font-size: 13px;
}

.chip-button,
.scope-chip,
.tag-chip {
  border: 1px solid #d7ddd2;
  background: #f5f7f3;
  color: #223228;
}

.chip-button.active,
.ghost-button.active {
  border-color: #8aa070;
  background: #ebf2e2;
}

.score-badge {
  border: 1px solid #d8c086;
  background: #faf3df;
  color: #6f5824;
}

.step-list,
.plain-list {
  margin: 0;
  padding-left: 20px;
  color: #223228;
  line-height: 1.8;
}

.field-group,
.feedback-block,
.tips-block,
.follow-up {
  display: grid;
  gap: 12px;
}

.field-title,
.field-label span {
  font-size: 14px;
  color: #415347;
}

.field-label {
  display: grid;
  gap: 8px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field-span-2 {
  grid-column: span 2;
}

.text-input {
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  border: 1px solid #cfd7ca;
  border-radius: 8px;
  background: #ffffff;
  color: #18261d;
}

.textarea {
  resize: vertical;
  min-height: 120px;
}

.small-textarea {
  min-height: 88px;
}

.primary-button {
  padding: 0 16px;
  background: #375641;
  color: #ffffff;
}

.primary-button:hover {
  background: #2f4937;
}

.primary-button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.ghost-button {
  padding: 0 16px;
  border-color: #cfd7ca;
  background: #ffffff;
  color: #223228;
}

.ghost-button:hover {
  border-color: #8aa070;
  background: #f3f7ee;
}

.ghost-button.danger:hover {
  border-color: #c27b7b;
  background: #f9ecec;
}

.notice,
.notice-inline {
  padding: 14px 16px;
  border: 1px solid #d7ddd2;
  background: #fff8eb;
  color: #7a5d1e;
}

.notice-error {
  border-color: #d7b9b9;
  background: #fff1f1;
  color: #8a4141;
}

.result-head,
.attraction-top,
.route-head,
.record-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 12px;
}

.chat-result,
.route-result {
  display: grid;
  gap: 16px;
}

.route-result :deep(.scenic-map-panel) {
  margin-top: 2px;
}

.source-list,
.record-list,
.knowledge-list {
  display: grid;
}

.route-list {
  display: grid;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.route-item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
}

.route-order {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #ebf2e2;
  color: #375641;
  font-weight: 700;
}

.route-body {
  display: grid;
  gap: 8px;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebefea;
}

.trend-list,
.ranking-list {
  display: grid;
  gap: 10px;
}

.trend-row,
.ranking-list li {
  display: grid;
  grid-template-columns: minmax(72px, 84px) minmax(0, 1fr) 36px;
  align-items: center;
  gap: 10px;
  list-style: none;
}

.trend-bar {
  width: 100%;
  height: 10px;
  background: #edf1ea;
}

.trend-fill {
  height: 100%;
  background: linear-gradient(90deg, #67834a, #bda25e);
}

.ranking-list {
  padding: 0;
  margin: 0;
}

.ranking-list li {
  grid-template-columns: minmax(0, 1fr) 48px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebefea;
}

.ranking-list.secondary {
  margin-top: 14px;
}

.record-list,
.knowledge-list {
  gap: 12px;
}

.check-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #415347;
}

.empty-state {
  display: grid;
  gap: 12px;
  place-items: start;
  min-height: 240px;
  align-content: center;
  color: #415347;
}

/* Desktop visual refresh inspired by the requested React Bits effects. */
:global(body) {
  background:
    linear-gradient(135deg, rgba(13, 30, 24, 0.96) 0%, rgba(18, 42, 33, 0.94) 34%, rgba(48, 55, 42, 0.92) 100%),
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.035) 0 1px, transparent 1px 80px);
}

.app-shell {
  --surface: rgba(247, 251, 241, 0.9);
  --surface-strong: rgba(255, 255, 255, 0.96);
  --surface-dark: rgba(15, 33, 27, 0.82);
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
    linear-gradient(180deg, rgba(255, 255, 255, 0.02), rgba(255, 255, 255, 0)),
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.026) 0 1px, transparent 1px 88px);
}

.page-strands {
  pointer-events: none;
}

.page-strands-fixed {
  position: fixed;
  inset: 0;
  z-index: 0;
  opacity: 0.52;
  mix-blend-mode: screen;
}

.hero-strands {
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.68;
  mix-blend-mode: screen;
}

.page-strands-bottom {
  position: relative;
  z-index: 0;
  height: 260px;
  margin: 34px 0 0;
  opacity: 0.78;
  mix-blend-mode: screen;
}

.hero-band {
  position: relative;
  z-index: 1;
  min-height: 560px;
  background-position: center;
  background-size: cover;
  border-bottom: 1px solid rgba(231, 193, 112, 0.2);
  box-shadow: 0 28px 80px rgba(5, 15, 12, 0.28);
}

.hero-content {
  position: relative;
  z-index: 1;
  padding: 34px 0 28px;
}

.hero-main {
  grid-template-columns: minmax(0, 1fr);
  gap: 22px;
  min-height: 0;
  align-items: start;
}

.eyebrow {
  color: rgba(232, 195, 112, 0.88);
  font-weight: 700;
}

.section-kicker {
  color: var(--gold);
  font-weight: 800;
}

h1 {
  max-width: 860px;
  font-size: clamp(46px, 5.8vw, 86px);
  line-height: 1.02;
}

.hero-title-block {
  width: 100%;
  max-width: 1120px;
  min-height: 148px;
  margin-bottom: 8px;
}

.hero-title-shine {
  letter-spacing: 0;
}

h2 {
  color: #f6f8ef;
  font-size: 32px;
}

h3,
h4 {
  color: var(--ink);
}

.hero-copy {
  max-width: 760px;
  color: rgba(248, 252, 242, 0.86);
  font-size: 20px;
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.hero-button {
  min-width: 152px;
  min-height: 56px;
  padding: 0 26px;
  font-size: 20px;
  font-weight: 700;
  box-shadow: 0 20px 40px rgba(74, 16, 122, 0.28);
}

.hero-actions .primary-button {
  border-color: rgba(205, 145, 255, 0.82);
  background: linear-gradient(135deg, #7c3aed, #a855f7);
  color: #ffffff;
}

.hero-actions .primary-button:hover {
  border-color: rgba(243, 232, 255, 0.95);
  background: linear-gradient(135deg, #6d28d9, #9333ea);
}

.hero-actions .ghost-button {
  border-color: rgba(221, 189, 255, 0.76);
  background: rgba(124, 58, 237, 0.28);
  color: #fbf7ff;
}

.hero-actions .ghost-button:hover {
  border-color: rgba(243, 232, 255, 0.92);
  background: rgba(147, 51, 234, 0.42);
  color: #ffffff;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  max-width: 1120px;
}

.hero-metric {
  display: grid;
  gap: 8px;
  min-height: 124px;
  padding: 18px;
  border: 1px solid rgba(232, 195, 112, 0.32);
  border-radius: 8px;
  background: rgba(12, 31, 25, 0.68);
  color: #f8fcf2;
  box-shadow: 0 22px 50px rgba(5, 16, 13, 0.24);
  backdrop-filter: blur(12px);
}

.hero-metric span {
  color: rgba(232, 195, 112, 0.88);
  font-size: 18px;
  font-weight: 800;
}

.hero-metric strong {
  color: #ffffff;
  font-size: 36px;
  line-height: 1.1;
}

.hero-metric p {
  color: rgba(248, 252, 242, 0.72);
  font-size: 16px;
  line-height: 1.75;
}

.nav-strip {
  gap: 8px;
  margin-top: 10px;
}

.nav-button,
.primary-button,
.ghost-button,
.chip-button {
  border-radius: 8px;
}

.nav-button {
  min-height: 50px;
  padding: 0 24px;
  font-size: 19px;
  font-weight: 700;
  background: rgba(248, 252, 242, 0.1);
  border-color: rgba(248, 252, 242, 0.2);
  color: rgba(248, 252, 242, 0.9);
  backdrop-filter: blur(10px);
}

.nav-button.active,
.nav-button:hover {
  background: rgba(232, 195, 112, 0.2);
  border-color: rgba(232, 195, 112, 0.62);
  color: #ffffff;
}

.page-body {
  position: relative;
  z-index: 1;
  padding: 34px 0 380px;
}

.page-section,
.page-subsection {
  gap: 18px;
}

.section-head {
  padding: 18px 0 4px;
}

.stats-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.dual-grid {
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
}

.triple-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.attraction-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.content-panel,
.stat-card,
.attraction-card,
.source-card,
.record-card,
.knowledge-card,
.scenic-map-panel {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(241, 247, 237, 0.92)),
    var(--surface);
  box-shadow:
    0 20px 54px rgba(3, 17, 12, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.content-panel::before,
.stat-card::before,
.attraction-card::before,
.source-card::before,
.record-card::before,
.knowledge-card::before,
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

.content-panel:hover::before,
.stat-card:hover::before,
.attraction-card:hover::before,
.source-card:hover::before,
.record-card:hover::before,
.knowledge-card:hover::before,
.hero-metric:hover::before {
  transform: translateX(120%);
}

.content-panel > *,
.stat-card > *,
.attraction-card > *,
.source-card > *,
.record-card > *,
.knowledge-card > *,
.hero-metric > * {
  position: relative;
  z-index: 1;
}

.stat-card {
  min-height: 150px;
}

.stat-label,
.panel-tip,
.muted-text {
  color: var(--muted);
}

.stat-label {
  font-size: 16px;
}

.stat-value {
  color: var(--ink);
  font-size: 38px;
}

.body-text,
.answer-text {
  color: #20342b;
}

.score-badge {
  border-color: rgba(232, 195, 112, 0.56);
  border-radius: 8px;
  background: rgba(255, 246, 222, 0.86);
  color: #73551b;
  font-weight: 800;
}

.chip-button,
.scope-chip,
.tag-chip {
  border-color: rgba(25, 58, 47, 0.14);
  border-radius: 8px;
  background: rgba(244, 248, 239, 0.9);
  color: #20342b;
}

.chip-button:hover,
.chip-button.active,
.ghost-button.active {
  border-color: rgba(136, 185, 155, 0.86);
  background: rgba(219, 238, 222, 0.86);
}

.primary-button {
  background: linear-gradient(135deg, #214f3d, #357253);
  box-shadow: 0 14px 28px rgba(17, 62, 43, 0.18);
}

.primary-button:hover {
  background: linear-gradient(135deg, #1c4435, #2f674b);
}

.ghost-button {
  border-color: rgba(31, 63, 50, 0.18);
  background: rgba(255, 255, 255, 0.78);
}

.ghost-button:hover {
  border-color: rgba(232, 195, 112, 0.66);
  background: rgba(255, 250, 237, 0.92);
}

.text-input {
  border-color: rgba(31, 63, 50, 0.16);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.62);
}

.text-input:focus {
  outline: 2px solid rgba(232, 195, 112, 0.38);
  border-color: rgba(232, 195, 112, 0.72);
}

.notice,
.notice-inline {
  border-color: rgba(232, 195, 112, 0.44);
  border-radius: 8px;
  background: rgba(255, 248, 229, 0.92);
}

.route-order {
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(232, 195, 112, 0.24), rgba(136, 185, 155, 0.24));
  color: #254a39;
}

.trend-bar {
  overflow: hidden;
  border-radius: 8px;
  background: rgba(22, 54, 42, 0.08);
}

.trend-fill {
  background: linear-gradient(90deg, var(--mint), var(--gold), var(--coral));
  box-shadow: 0 0 18px rgba(232, 195, 112, 0.38);
}

.inquiry-section {
  position: relative;
}

.inquiry-cursor-field {
  display: grid;
  gap: 18px;
}

.digital-stage {
  --cookie-panel: rgba(247, 251, 242, 0.94);
  --cookie-line: rgba(232, 195, 112, 0.34);
  --cookie-text: #1a2d25;
  --cookie-soft: #657569;
}

.digital-overlay-backdrop {
  background: rgba(8, 18, 15, 0.62);
  backdrop-filter: blur(12px);
}

.digital-shell.expanded {
  gap: 16px;
}

.digital-shell.expanded .digital-stage-host {
  border: 1px solid rgba(232, 195, 112, 0.22);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(255, 255, 255, 0.08)),
    linear-gradient(135deg, rgba(48, 91, 67, 0.26), rgba(232, 195, 112, 0.12));
}

.digital-console {
  border-color: var(--cookie-line);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(241, 248, 236, 0.9)),
    var(--cookie-panel);
}

.digital-human-hint {
  min-height: 42px;
  border-radius: 8px;
  border-color: rgba(232, 195, 112, 0.5);
  background: rgba(247, 251, 242, 0.92);
  font-weight: 800;
}

.digital-overlay-close {
  border-radius: 8px;
}

.digital-status-row {
  display: grid;
  grid-template-columns: minmax(0, auto) minmax(0, 1fr) auto;
}

.hearing-status {
  align-self: center;
  color: var(--cookie-soft);
  font-size: 13px;
}

.hearing-button {
  border-color: rgba(207, 118, 92, 0.34);
  color: #7c3c2d;
}

.voice-strip,
.status-mini,
.digital-speech {
  padding: 12px;
  border: 1px solid rgba(31, 63, 50, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.route-result :deep(.scenic-map-panel) {
  border-radius: 8px;
}

@media (max-width: 1024px) {
  .hero-main,
  .stats-grid,
  .dual-grid,
  .triple-grid,
  .attraction-grid,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .field-span-2 {
    grid-column: span 1;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .digital-stage {
    left: 12px;
    right: 12px;
    bottom: 12px;
    width: auto;
    height: 500px;
  }

  .digital-stage.expanded {
    left: 0;
    right: 0;
    bottom: 0;
  }

  .digital-human-hint {
    right: 12px;
    bottom: 152px;
    font-size: 13px;
  }

  .digital-shell.expanded {
    width: min(100vw - 24px, 720px);
    height: min(92vh, 980px);
    grid-template-columns: 1fr;
    grid-template-rows: minmax(300px, 46vh) minmax(0, 1fr);
    gap: 12px;
    margin: 12px auto;
  }

  .digital-shell.expanded .digital-stage-host {
    min-height: 300px;
  }

  .digital-console {
    max-height: none;
  }

  .hero-content,
  .page-body {
    width: min(100vw - 24px, 1180px);
  }

  .hero-content {
    padding-top: 18px;
  }

  .page-body {
    padding-bottom: 500px;
  }

  h1 {
    font-size: 30px;
  }

  h2 {
    font-size: 24px;
  }

  .section-head,
  .panel-head,
  .result-head,
  .attraction-top,
  .record-head {
    grid-template-columns: 1fr;
    display: grid;
  }

  .action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .trend-row {
    grid-template-columns: 1fr;
  }
}
</style>
