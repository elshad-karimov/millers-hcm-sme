import { api } from './client'

// ============================================================================
// Enums (match backend exactly)
// ============================================================================

export type IncidentType =
  | 'INJURY'
  | 'NEAR_MISS'
  | 'UNSAFE_CONDITION'
  | 'PROPERTY_DAMAGE'
  | 'VEHICLE'
  | 'CHEMICAL'
  | 'FIRE'
  | 'EQUIPMENT'
  | 'VIOLENCE'
  | 'ENVIRONMENTAL'

export type IncidentSeverity = 'MINOR' | 'MODERATE' | 'SERIOUS' | 'CRITICAL'

export type IncidentStatus = 'OPEN' | 'UNDER_INVESTIGATION' | 'CLOSED'

export type FindingStatus = 'OK' | 'NON_COMPLIANT'

export type InspectionStatus = 'SCHEDULED' | 'COMPLETED'

export type RiskAssessmentStatus = 'DRAFT' | 'APPROVED' | 'ARCHIVED'

export type RiskBand = 'LOW' | 'MEDIUM' | 'HIGH'

export type CorrectiveActionStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'OVERDUE'

export type CorrectiveActionPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export type ReturnToWorkStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED'

export type PpeType =
  | 'HELMET'
  | 'GLOVES'
  | 'SHOES'
  | 'VEST'
  | 'GOGGLES'
  | 'MASK'
  | 'EAR_PROTECTION'
  | 'CLOTHING'
  | 'OTHER'

// ============================================================================
// Incidents
// ============================================================================

export interface WitnessDto {
  name: string
  contact?: string
  statement?: string
}

export interface ReportIncidentRequest {
  incidentDate: string
  incidentTime: string
  workLocationId?: string
  orgUnitId?: string
  incidentType: IncidentType
  severity: IncidentSeverity
  description: string
  immediateAction?: string
  involvedEmployeeIds?: string[]
  witnesses?: WitnessDto[]
}

export interface UpdateIncidentRequest {
  description?: string
  immediateAction?: string
  status?: IncidentStatus
  involvedEmployeeIds?: string[]
  witnesses?: WitnessDto[]
}

export interface CloseIncidentRequest {
  resolution: string
}

export interface IncidentResponse {
  id: string
  tenantId: string
  incidentNo: string
  incidentDate: string
  incidentTime: string
  workLocationId?: string
  orgUnitId?: string
  incidentType: IncidentType
  severity: IncidentSeverity
  reportedByEmployeeId: string
  reportedByEmployeeName?: string
  description: string
  immediateAction?: string
  investigationRequired?: boolean
  status: IncidentStatus
  closedAt?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

export interface IncidentInvolvedResponse {
  id: string
  incidentId: string
  employeeId: string
  employeeName?: string
}

export interface IncidentWitnessResponse {
  id: string
  incidentId: string
  name: string
  contact?: string
  statement?: string
}

// ============================================================================
// Injury Reports
// ============================================================================

export interface CreateInjuryReportRequest {
  incidentId: string
  employeeId: string
  injuryType: string
  bodyPart: string
  severity: IncidentSeverity
  medicalTreatment?: boolean
  firstAid?: boolean
  hospital?: boolean
  lostTimeDays?: number
  restrictedDuty?: boolean
  insuranceClaimRef?: string
  notes?: string
}

export interface UpdateInjuryReportRequest {
  injuryType?: string
  bodyPart?: string
  severity?: IncidentSeverity
  medicalTreatment?: boolean
  firstAid?: boolean
  hospital?: boolean
  lostTimeDays?: number
  restrictedDuty?: boolean
  insuranceClaimRef?: string
  notes?: string
}

export interface InjuryReportResponse {
  id: string
  tenantId: string
  incidentId: string
  employeeId: string
  employeeName?: string
  injuryType: string
  bodyPart: string
  severity: IncidentSeverity
  medicalTreatment?: boolean
  firstAid?: boolean
  hospital?: boolean
  lostTimeDays?: number
  restrictedDuty?: boolean
  insuranceClaimRef?: string
  notes?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

// ============================================================================
// Return to Work Plans
// ============================================================================

export interface CreateReturnToWorkRequest {
  injuryReportId: string
  employeeId: string
  medicalClearanceDate: string
  restrictions?: string
  modifiedSchedule?: string
}

export interface UpdateReturnToWorkRequest {
  medicalClearanceDate?: string
  restrictions?: string
  modifiedSchedule?: string
  status?: ReturnToWorkStatus
}

export interface SetApprovalRequest {
  approved: boolean
}

export interface ReturnToWorkResponse {
  id: string
  tenantId: string
  injuryReportId: string
  employeeId: string
  employeeName?: string
  medicalClearanceDate: string
  restrictions?: string
  modifiedSchedule?: string
  managerApproved?: boolean
  hrApproved?: boolean
  status: ReturnToWorkStatus
  closedAt?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

// ============================================================================
// Risk Assessments
// ============================================================================

export interface CreateRiskAssessmentRequest {
  workLocationId?: string
  orgUnitId?: string
  jobTask: string
  hazard: string
  likelihood: number
  impact: number
  controlMeasures?: string
  responsibleUsername?: string
  reviewDate?: string
}

export interface UpdateRiskAssessmentRequest {
  jobTask?: string
  hazard?: string
  likelihood?: number
  impact?: number
  controlMeasures?: string
  responsibleUsername?: string
  reviewDate?: string
}

export interface RiskAssessmentResponse {
  id: string
  tenantId: string
  workLocationId?: string
  orgUnitId?: string
  jobTask: string
  hazard: string
  likelihood: number
  impact: number
  riskScore: number
  riskBand: RiskBand
  controlMeasures?: string
  responsibleUsername?: string
  reviewDate?: string
  status: RiskAssessmentStatus
  approvedBy?: string
  approvedAt?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

// ============================================================================
// Safety Inspections
// ============================================================================

export interface FindingDto {
  itemLabel: string
  findingStatus: FindingStatus
  notes?: string
  correctiveActionId?: string
}

export interface CreateSafetyInspectionRequest {
  workLocationId?: string
  inspectionDate: string
  inspectorUsername: string
  title: string
  notes?: string
  findings?: FindingDto[]
}

export interface UpdateSafetyInspectionRequest {
  title?: string
  notes?: string
  findings?: FindingDto[]
}

export interface SafetyInspectionResponse {
  id: string
  tenantId: string
  workLocationId?: string
  inspectionDate: string
  inspectorUsername: string
  title: string
  overallScore?: number
  status: InspectionStatus
  notes?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

export interface InspectionFindingResponse {
  id: string
  inspectionId: string
  itemLabel: string
  findingStatus: FindingStatus
  notes?: string
  correctiveActionId?: string
}

// ============================================================================
// Corrective Actions
// ============================================================================

export interface CreateCorrectiveActionRequest {
  incidentId?: string
  inspectionId?: string
  riskAssessmentId?: string
  description: string
  responsibleUsername?: string
  dueDate?: string
  priority?: CorrectiveActionPriority
}

export interface UpdateCorrectiveActionStatusRequest {
  status: CorrectiveActionStatus
  evidenceAttachmentId?: string
}

export interface CorrectiveActionResponse {
  id: string
  tenantId: string
  incidentId?: string
  inspectionId?: string
  riskAssessmentId?: string
  description: string
  responsibleUsername?: string
  dueDate?: string
  priority?: CorrectiveActionPriority
  status: CorrectiveActionStatus
  evidenceAttachmentId?: string
  closedAt?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

// ============================================================================
// PPE
// ============================================================================

export interface PpeItemResponse {
  id: string
  tenantId: string
  code: string
  name: string
  ppeType: PpeType
  defaultExpiryMonths?: number
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

export interface IssuePpeRequest {
  employeeId: string
  ppeItemId: string
  issuedAt: string
  expiryDate?: string
  conditionAtIssue?: string
  notes?: string
}

export interface ReturnPpeRequest {
  returnedAt: string
  conditionAtReturn?: string
}

export interface PpeAssignmentResponse {
  id: string
  tenantId: string
  employeeId: string
  employeeName?: string
  ppeItemId: string
  ppeItemName?: string
  issuedAt: string
  expiryDate?: string
  returnedAt?: string
  conditionAtIssue?: string
  conditionAtReturn?: string
  notes?: string
  createdBy: string
  createdAt: string
  updatedBy?: string
  updatedAt?: string
}

// ============================================================================
// API Clients
// ============================================================================

export const incidentsApi = {
  list: (status?: IncidentStatus, reportedByEmployeeId?: string) =>
    api
      .get<IncidentResponse[]>('/ehs/incidents', { params: { status, reportedByEmployeeId } })
      .then((r) => r.data),
  get: (id: string) => api.get<IncidentResponse>(`/ehs/incidents/${id}`).then((r) => r.data),
  report: (req: ReportIncidentRequest) =>
    api.post<IncidentResponse>('/ehs/incidents', req).then((r) => r.data),
  update: (id: string, req: UpdateIncidentRequest) =>
    api.put<IncidentResponse>(`/ehs/incidents/${id}`, req).then((r) => r.data),
  close: (id: string, req: CloseIncidentRequest) =>
    api.post<IncidentResponse>(`/ehs/incidents/${id}/close`, req).then((r) => r.data),
  getInvolved: (id: string) =>
    api.get<IncidentInvolvedResponse[]>(`/ehs/incidents/${id}/involved`).then((r) => r.data),
  getWitnesses: (id: string) =>
    api.get<IncidentWitnessResponse[]>(`/ehs/incidents/${id}/witnesses`).then((r) => r.data),
}

export const injuryReportsApi = {
  list: (incidentId?: string, employeeId?: string) =>
    api
      .get<InjuryReportResponse[]>('/ehs/injuries', { params: { incidentId, employeeId } })
      .then((r) => r.data),
  get: (id: string) => api.get<InjuryReportResponse>(`/ehs/injuries/${id}`).then((r) => r.data),
  create: (req: CreateInjuryReportRequest) =>
    api.post<InjuryReportResponse>('/ehs/injuries', req).then((r) => r.data),
  update: (id: string, req: UpdateInjuryReportRequest) =>
    api.put<InjuryReportResponse>(`/ehs/injuries/${id}`, req).then((r) => r.data),
}

export const returnToWorkApi = {
  list: (status?: ReturnToWorkStatus, employeeId?: string) =>
    api
      .get<ReturnToWorkResponse[]>('/ehs/return-to-work', { params: { status, employeeId } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<ReturnToWorkResponse>(`/ehs/return-to-work/${id}`).then((r) => r.data),
  create: (req: CreateReturnToWorkRequest) =>
    api.post<ReturnToWorkResponse>('/ehs/return-to-work', req).then((r) => r.data),
  update: (id: string, req: UpdateReturnToWorkRequest) =>
    api.put<ReturnToWorkResponse>(`/ehs/return-to-work/${id}`, req).then((r) => r.data),
  setManagerApproval: (id: string, req: SetApprovalRequest) =>
    api
      .post<ReturnToWorkResponse>(`/ehs/return-to-work/${id}/manager-approval`, req)
      .then((r) => r.data),
  setHrApproval: (id: string, req: SetApprovalRequest) =>
    api
      .post<ReturnToWorkResponse>(`/ehs/return-to-work/${id}/hr-approval`, req)
      .then((r) => r.data),
}

export const riskAssessmentsApi = {
  list: (status?: RiskAssessmentStatus, minRiskScore?: number) =>
    api
      .get<RiskAssessmentResponse[]>('/ehs/risk-assessments', {
        params: { status, minRiskScore },
      })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<RiskAssessmentResponse>(`/ehs/risk-assessments/${id}`).then((r) => r.data),
  create: (req: CreateRiskAssessmentRequest) =>
    api.post<RiskAssessmentResponse>('/ehs/risk-assessments', req).then((r) => r.data),
  update: (id: string, req: UpdateRiskAssessmentRequest) =>
    api.put<RiskAssessmentResponse>(`/ehs/risk-assessments/${id}`, req).then((r) => r.data),
  approve: (id: string) =>
    api.post<RiskAssessmentResponse>(`/ehs/risk-assessments/${id}/approve`).then((r) => r.data),
}

export const inspectionsApi = {
  list: (status?: InspectionStatus) =>
    api
      .get<SafetyInspectionResponse[]>('/ehs/inspections', { params: { status } })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<SafetyInspectionResponse>(`/ehs/inspections/${id}`).then((r) => r.data),
  create: (req: CreateSafetyInspectionRequest) =>
    api.post<SafetyInspectionResponse>('/ehs/inspections', req).then((r) => r.data),
  update: (id: string, req: UpdateSafetyInspectionRequest) =>
    api.put<SafetyInspectionResponse>(`/ehs/inspections/${id}`, req).then((r) => r.data),
  complete: (id: string) =>
    api.post<SafetyInspectionResponse>(`/ehs/inspections/${id}/complete`).then((r) => r.data),
  getFindings: (id: string) =>
    api.get<InspectionFindingResponse[]>(`/ehs/inspections/${id}/findings`).then((r) => r.data),
}

export const correctiveActionsApi = {
  list: (status?: CorrectiveActionStatus, responsibleUsername?: string) =>
    api
      .get<CorrectiveActionResponse[]>('/ehs/corrective-actions', {
        params: { status, responsibleUsername },
      })
      .then((r) => r.data),
  get: (id: string) =>
    api.get<CorrectiveActionResponse>(`/ehs/corrective-actions/${id}`).then((r) => r.data),
  create: (req: CreateCorrectiveActionRequest) =>
    api.post<CorrectiveActionResponse>('/ehs/corrective-actions', req).then((r) => r.data),
  updateStatus: (id: string, req: UpdateCorrectiveActionStatusRequest) =>
    api
      .put<CorrectiveActionResponse>(`/ehs/corrective-actions/${id}/status`, req)
      .then((r) => r.data),
}

export const ppeItemsApi = {
  list: (activeOnly?: boolean) =>
    api
      .get<PpeItemResponse[]>('/ehs/ppe-items', { params: { activeOnly } })
      .then((r) => r.data),
  get: (id: string) => api.get<PpeItemResponse>(`/ehs/ppe-items/${id}`).then((r) => r.data),
}

export const ppeAssignmentsApi = {
  list: (employeeId?: string) =>
    api
      .get<PpeAssignmentResponse[]>('/ehs/ppe-assignments', { params: { employeeId } })
      .then((r) => r.data),
  listExpiring: () =>
    api.get<PpeAssignmentResponse[]>('/ehs/ppe-assignments/expiring').then((r) => r.data),
  get: (id: string) =>
    api.get<PpeAssignmentResponse>(`/ehs/ppe-assignments/${id}`).then((r) => r.data),
  issue: (req: IssuePpeRequest) =>
    api.post<PpeAssignmentResponse>('/ehs/ppe-assignments', req).then((r) => r.data),
  returnPpe: (id: string, req: ReturnPpeRequest) =>
    api
      .post<PpeAssignmentResponse>(`/ehs/ppe-assignments/${id}/return`, req)
      .then((r) => r.data),
}

export const selfPpeApi = {
  myAssignments: () =>
    api.get<PpeAssignmentResponse[]>('/self/ehs/ppe-assignments').then((r) => r.data),
}

// ============================================================================
// Helper constants for UI
// ============================================================================

export const INCIDENT_TYPE_OPTIONS: { value: IncidentType; label: string }[] = [
  { value: 'INJURY', label: 'Injury' },
  { value: 'NEAR_MISS', label: 'Near miss' },
  { value: 'UNSAFE_CONDITION', label: 'Unsafe condition' },
  { value: 'PROPERTY_DAMAGE', label: 'Property damage' },
  { value: 'VEHICLE', label: 'Vehicle incident' },
  { value: 'CHEMICAL', label: 'Chemical spill/exposure' },
  { value: 'FIRE', label: 'Fire' },
  { value: 'EQUIPMENT', label: 'Equipment failure' },
  { value: 'VIOLENCE', label: 'Violence/threat' },
  { value: 'ENVIRONMENTAL', label: 'Environmental' },
]

export const INCIDENT_SEVERITY_OPTIONS: { value: IncidentSeverity; label: string }[] = [
  { value: 'MINOR', label: 'Minor' },
  { value: 'MODERATE', label: 'Moderate' },
  { value: 'SERIOUS', label: 'Serious' },
  { value: 'CRITICAL', label: 'Critical' },
]

export const INCIDENT_STATUS_OPTIONS: { value: IncidentStatus; label: string }[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'UNDER_INVESTIGATION', label: 'Under investigation' },
  { value: 'CLOSED', label: 'Closed' },
]

export const FINDING_STATUS_OPTIONS: { value: FindingStatus; label: string }[] = [
  { value: 'OK', label: 'OK' },
  { value: 'NON_COMPLIANT', label: 'Non-compliant' },
]

export const CORRECTIVE_ACTION_PRIORITY_OPTIONS: {
  value: CorrectiveActionPriority
  label: string
}[] = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
]

export const CORRECTIVE_ACTION_STATUS_OPTIONS: {
  value: CorrectiveActionStatus
  label: string
}[] = [
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'OVERDUE', label: 'Overdue' },
]

export const RETURN_TO_WORK_STATUS_OPTIONS: { value: ReturnToWorkStatus; label: string }[] = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'COMPLETED', label: 'Completed' },
]

export const RISK_ASSESSMENT_STATUS_OPTIONS: { value: RiskAssessmentStatus; label: string }[] = [
  { value: 'DRAFT', label: 'Draft' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export const PPE_TYPE_OPTIONS: { value: PpeType; label: string }[] = [
  { value: 'HELMET', label: 'Helmet' },
  { value: 'GLOVES', label: 'Gloves' },
  { value: 'SHOES', label: 'Safety shoes' },
  { value: 'VEST', label: 'Safety vest' },
  { value: 'GOGGLES', label: 'Safety goggles' },
  { value: 'MASK', label: 'Mask/respirator' },
  { value: 'EAR_PROTECTION', label: 'Ear protection' },
  { value: 'CLOTHING', label: 'Protective clothing' },
  { value: 'OTHER', label: 'Other' },
]

// Tag colors
export const INCIDENT_SEVERITY_COLOR: Record<IncidentSeverity, string> = {
  MINOR: 'green',
  MODERATE: 'orange',
  SERIOUS: 'red',
  CRITICAL: 'purple',
}

export const INCIDENT_STATUS_COLOR: Record<IncidentStatus, string> = {
  OPEN: 'blue',
  UNDER_INVESTIGATION: 'orange',
  CLOSED: 'default',
}

export const RISK_BAND_COLOR: Record<RiskBand, string> = {
  LOW: 'green',
  MEDIUM: 'orange',
  HIGH: 'red',
}

export const CORRECTIVE_ACTION_PRIORITY_COLOR: Record<CorrectiveActionPriority, string> = {
  LOW: 'default',
  MEDIUM: 'orange',
  HIGH: 'red',
}

export const CORRECTIVE_ACTION_STATUS_COLOR: Record<CorrectiveActionStatus, string> = {
  OPEN: 'blue',
  IN_PROGRESS: 'cyan',
  COMPLETED: 'green',
  OVERDUE: 'red',
}
