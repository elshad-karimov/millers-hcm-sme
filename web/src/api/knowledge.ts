import { api } from './client'

export interface KnowledgeArticle {
  id: string
  articleNo: string
  code: string
  title: string
  summary: string
  category: string
  tags: string
  body: string
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  version: number
  viewCount: number
  helpfulVotes: number
  notHelpfulVotes: number
  createdAt: string
  createdBy: string
  updatedAt: string
  updatedBy: string
}

export interface CreateArticleRequest {
  code: string
  title: string
  summary: string
  category: string
  tags: string
  body: string
}

export const knowledgeApi = {
  listPublished: () => api.get<KnowledgeArticle[]>('/helpdesk/knowledge/published'),
  listAll: () => api.get<KnowledgeArticle[]>('/helpdesk/knowledge'),
  search: (keyword: string) => api.get<KnowledgeArticle[]>(`/helpdesk/knowledge/search?keyword=${encodeURIComponent(keyword)}`),
  get: (id: string) => api.get<KnowledgeArticle>(`/helpdesk/knowledge/${id}`),
  vote: (id: string, helpful: boolean) => api.post(`/helpdesk/knowledge/${id}/vote?helpful=${helpful}`),
  create: (data: CreateArticleRequest) => api.post<KnowledgeArticle>('/helpdesk/knowledge', data),
  update: (id: string, data: CreateArticleRequest) => api.put<KnowledgeArticle>(`/helpdesk/knowledge/${id}`, data),
  publish: (id: string) => api.post<KnowledgeArticle>(`/helpdesk/knowledge/${id}/publish`),
  archive: (id: string) => api.post<KnowledgeArticle>(`/helpdesk/knowledge/${id}/archive`),
}

export const CATEGORIES = [
  'HR Policy',
  'Leave',
  'Payroll',
  'Benefits',
  'IT Support',
  'Recruitment',
  'Performance',
  'Training',
  'Compliance',
  'General'
] as const
