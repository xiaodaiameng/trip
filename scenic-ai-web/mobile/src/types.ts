export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface Overview {
  projectName: string
  scenicName: string
  welcomeMessage: string
  mvpScope: string[]
  hotQuestions: string[]
}

export interface Attraction {
  id: number
  code: string
  name: string
  area: string
  theme: string
  intro: string
  highlight: string
  details: string
  openHours: string
  suggestedDurationMinutes: number
  popularityScore: number
  walkingIntensity: number
  latitude?: number | null
  longitude?: number | null
  tags: string[]
  features: string[]
}

export interface ChatSourceCard {
  title: string
  excerpt: string
  source: string
}

export interface ChatResponse {
  recordId: number
  question: string
  answer: string
  emotion: string
  sources: ChatSourceCard[]
  suggestions: string[]
}

export interface RouteStop {
  order: number
  attractionName: string
  highlight: string
  duration: string
  reason: string
}

export interface RoutePlanResponse {
  title: string
  totalDurationHours: number
  stops: RouteStop[]
  tips: string[]
}

export interface LoginResponse {
  token: string
  displayName: string
  roles: string[]
}

export interface DashboardMetricPoint {
  label: string
  value: number
}

export interface DashboardNameValue {
  name: string
  value: number
}

export interface DashboardResponse {
  conversationCount: number
  feedbackCount: number
  avgResponseMillis: number
  satisfactionRate: number
  weeklyTrend: DashboardMetricPoint[]
  emotionDistribution: DashboardNameValue[]
  topAttractions: DashboardNameValue[]
  hotQuestions: DashboardNameValue[]
}

export interface ConversationRecord {
  id: number
  sessionId: string
  question: string
  answer: string
  emotion: string
  matchedSource: string
  sourceType: string
  responseMillis: number
  helpful: boolean | null
  createdAt: string
}

export interface TtsVoiceResponse {
  name: string
  culture: string
  gender: string
}
