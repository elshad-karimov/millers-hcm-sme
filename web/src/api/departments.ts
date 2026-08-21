import { api } from './client'

/**
 * The department list, as master data.
 *
 * A flat view over the org structure: the same rows Org Structure shows as a
 * tree, without the draft/approve/activate cycle that made adding one a
 * five-step job. The employee screen's Department picker reads from here.
 */
export interface Department {
  id: string
  code: string
  name: string
}

export const departmentsApi = {
  list: () => api.get<Department[]>('/departments').then((r) => r.data),

  create: (payload: { code: string; name: string }) =>
    api.post<Department>('/departments', payload).then((r) => r.data),

  rename: (id: string, payload: { code: string; name: string }) =>
    api.put<Department>(`/departments/${id}`, payload).then((r) => r.data),

  remove: (id: string) => api.delete(`/departments/${id}`).then((r) => r.data),
}
