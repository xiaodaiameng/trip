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
const assistantName = '小灵'
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
  password: '',
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
    description: '覆盖景点名称、亮点、开放时间和地图点位',
  },
  {
    label: '导览能力',
    value: `${overview.value?.mvpScope.length ?? 0} 项`,
    description: '围绕景点讲解、路线规划和游客服务展开',
  },
  {
    label: '当前后端',
    value: connectionLabel.value,
    description: '支撑景点资料、路线推荐和反馈记录读取',
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
    description: '围绕大佛、演艺、门票、路线等游览场景',
  },
  {
    label: '导览状态',
    value: connectionLabel.value,
    description: '景点讲解、路线推荐和语音播报按当前状态提供服务',
  },
])

const digitalActivityLabel = computed(() => {
  if (speaking.value) {
    return '小灵播报中'
  }
  if (listening.value) {
    return '小灵正在听'
  }
  return digitalOverlayOpen.value ? '小灵在线' : '小灵待机'
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
    if (adminProfile.value?.token) {
      localStorage.setItem('scenic-admin-token', adminProfile.value.token)
    }
  } catch {
    localStorage.removeItem('scenic-admin-profile')
    localStorage.removeItem('scenic-admin-token')
  }
}

function persistAdminSession(profile: LoginResponse | null) {
  if (!profile) {
    localStorage.removeItem('scenic-admin-profile')
    localStorage.removeItem('scenic-admin-token')
    return
  }
  localStorage.setItem('scenic-admin-profile', JSON.stringify(profile))
  localStorage.setItem('scenic-admin-token', profile.token)
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
    const message = error instanceof Error ? error.message : '加载后台数据失败'
    adminDataError.value = message
    if (message.includes('管理员登录')) {
      adminMessage.value = message
      adminProfile.value = null
      persistAdminSession(null)
    }
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
  localStorage.removeItem('scenic-admin-token')
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
            <p class="eyebrow">灵境智游</p>
            <h1 class="hero-title-block">
              <GradientTextTitle
                text="LingVista灵言"
                :colors="['#f5eefe', '#c084fc', '#8b5cf6', '#f5eefe']"
                :animation-speed="5"
                direction="horizontal"
              />
            </h1>
            <p class="hero-copy">
              古人有云，今人有灵。
            </p>
            <div class="hero-actions">
              <button type="button" class="primary-button hero-button" @click="openWeiwei">
                打开小灵
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
            打开小灵
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
            <p class="digital-label">灵境智游数字导览官</p>
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
            <h2><ScrollFloatText text="景点、路线、运营数据集中展示" /></h2>
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
              <h3>游览推荐</h3>
              <span class="panel-tip">围绕灵山胜境的游览体验提供快速指引</span>
            </div>
            <ol class="step-list">
              <li>先了解热门景点分布，再按兴趣选择适合自己的游览方向。</li>
              <li>结合问答咨询获取景点亮点、开放时间和服务信息。</li>
              <li>根据游玩时长与同行类型生成更贴合需求的参观路线。</li>
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
              <h2><ScrollFloatText text="景点咨询、资料溯源、游客反馈" /></h2>
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
              <span class="panel-tip">展示命中资料来源和后续游览建议</span>
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
              <p>输入一个和灵山胜境有关的问题，系统会优先依据本地景区资料生成导览建议。</p>
            </div>
          </section>
          </div>
        </TextCursorTrail>
      </section>

      <section v-else-if="activeView === 'route'" class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">路线推荐</p>
            <h2><ScrollFloatText text="按兴趣、时长和同行类型规划路线" /></h2>
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
              <p>选好偏好后点击“生成路线”，系统会输出一条适合当前条件的游览方案。</p>
            </div>
          </section>
        </div>
      </section>

      <section v-else-if="activeView === 'dashboard'" class="page-section">
        <div class="section-head">
          <div>
            <p class="section-kicker">后台看板</p>
            <h2><ScrollFloatText text="看清咨询量、满意度和运营热点" /></h2>
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
            <span class="muted-text">账号和密码请使用当前后端环境配置</span>
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
            <h2><ScrollFloatText text="景区知识维护与后台管理放在同一视图" /></h2>
          </div>
        </div>

        <section v-if="!adminProfile" class="content-panel">
          <div class="empty-state">
            <h3>请先登录后台</h3>
            <p>知识库维护依赖后台演示会话。你可以先进入“后台看板”完成登录，再回来编辑内容。</p>
            <div class="action-row">
              <button type="button" class="primary-button" @click="selectView('dashboard')">去后台看板登录</button>
              <a
                class="ghost-button knowledge-download-button"
                href="/downloads/scenic-questionnaire-data-package.zip"
                download="景区问卷数据资料包汇总.zip"
              >
                下载资料包
              </a>
            </div>
          </div>
        </section>

        <template v-else>
          <div class="dual-grid">
            <section class="content-panel">
              <div class="panel-head">
                <h3>{{ knowledgeForm.id == null ? '新增知识条目' : '编辑知识条目' }}</h3>
                <span class="panel-tip">内容、来源、关键词需贴合景区导览业务</span>
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
                  <input v-model="knowledgeForm.keywords" class="text-input" placeholder="多个关键词请用逗号分隔，例如：大佛，演艺，门票" />
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
                    placeholder="请填写完整的景区知识内容，例如开放时间、讲解词或服务说明"
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
                <div class="panel-head-copy">
                  <h3>现有知识条目</h3>
                  <span class="panel-tip">点击即可编辑或删除</span>
                </div>
                <a
                  class="ghost-button knowledge-download-button"
                  href="/downloads/scenic-questionnaire-data-package.zip"
                  download="景区问卷数据资料包汇总.zip"
                >
                  景区问卷数据资料包汇总
                </a>
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

<style scoped src="./app-view.css"></style>
