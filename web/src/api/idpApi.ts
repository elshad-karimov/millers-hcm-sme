import api from './axios';

export interface SkillGapRequest {
  competencyId?: string;
  skillName: string;
  currentLevel?: number;
  targetLevel?: number;
  notes?: string;
}

export interface ActivityRequest {
  title: string;
  activityType?: string;
  courseId?: string;
  dueDate?: string;
  notes?: string;
}

export interface IdpRequest {
  targetRole: string;
  targetDate?: string;
  skillGaps?: SkillGapRequest[];
  activities?: ActivityRequest[];
}

export interface SkillGapDto {
  id: string;
  competencyId?: string;
  skillName: string;
  currentLevel?: number;
  targetLevel?: number;
  notes?: string;
}

export interface ActivityDto {
  id: string;
  title: string;
  activityType: string;
  courseId?: string;
  dueDate?: string;
  status: string;
  completedAt?: string;
  notes?: string;
}

export interface IdpResponse {
  id: string;
  employeeId: string;
  targetRole: string;
  targetDate?: string;
  status: string;
  managerComment?: string;
  createdAt: string;
  createdBy?: string;
  updatedAt: string;
  skillGaps: SkillGapDto[];
  activities: ActivityDto[];
}

export const idpApi = {
  listForEmployee: (employeeId: string) =>
    api.get<IdpResponse[]>(`/api/employees/${employeeId}/idp`).then(r => r.data),

  createForEmployee: (employeeId: string, req: IdpRequest) =>
    api.post<IdpResponse>(`/api/employees/${employeeId}/idp`, req).then(r => r.data),

  get: (idpId: string) =>
    api.get<IdpResponse>(`/api/idp/${idpId}`).then(r => r.data),

  activate: (idpId: string) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/activate`).then(r => r.data),

  complete: (idpId: string) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/complete`).then(r => r.data),

  cancel: (idpId: string) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/cancel`).then(r => r.data),

  managerComment: (idpId: string, comment: string) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/manager-comment`, { comment }).then(r => r.data),

  addSkillGap: (idpId: string, req: SkillGapRequest) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/skill-gaps`, req).then(r => r.data),

  removeSkillGap: (idpId: string, gapId: string) =>
    api.delete<IdpResponse>(`/api/idp/${idpId}/skill-gaps/${gapId}`).then(r => r.data),

  addActivity: (idpId: string, req: ActivityRequest) =>
    api.post<IdpResponse>(`/api/idp/${idpId}/activities`, req).then(r => r.data),

  updateActivityStatus: (idpId: string, activityId: string, status: string) =>
    api.patch<IdpResponse>(`/api/idp/${idpId}/activities/${activityId}/status`, { status }).then(r => r.data),
};
