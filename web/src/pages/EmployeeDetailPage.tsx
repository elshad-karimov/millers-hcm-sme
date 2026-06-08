import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  employeesApi,
  type AuditEntry,
  type Employee,
  type EmploymentStatus,
} from '../api/employees'
import {
  personalDetailsApi,
  type Address,
  type EmergencyContact,
  type Identification,
} from '../api/personalDetails'
import { contractsApi, type Contract } from '../api/contracts'
import {
  credentialsApi,
  type Certification,
  type Health,
  type Vaccination,
} from '../api/credentials'
import { disciplinaryApi, type DisciplinaryAction } from '../api/disciplinary'
import {
  profileTabsApi,
  type Dependent,
  type Education,
  type WorkExperience,
} from '../api/profileTabs'
import {
  assetsNotesRewardsApi,
  type Asset,
  type Note,
  type Reward,
} from '../api/assetsNotesRewards'
import { probationReviewsApi, type ProbationReview } from '../api/probationReviews'
import {
  payrollApi,
  type BankAccountResponse,
  type CompensationResponse,
} from '../api/payroll'
import {
  assignmentApi,
  type EmployeeAssignment,
} from '../api/staffingCatalog'
import { timelineApi, type TimelineEvent } from '../api/team'
import { statusOverlayApi, type StatusOverlay } from '../api/statusOverlay'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'
// M117 — per-employee field-change history (employment slices + status slices + audit diff)
import { ChangeHistoryPanel } from '../components/ChangeHistoryPanel'

const STATUS_OPTIONS: EmploymentStatus[] = [
  'ACTIVE',
  'ON_PROBATION',
  'ON_LEAVE',
  'ON_BUSINESS_TRIP',
  'SUSPENDED',
  'TERMINATED',
  'RETIRED',
  'CONTRACTOR',
  'INTERN',
  // M78 / P2-14 — new statuses
  'MATERNITY_LEAVE',
  'MILITARY_SERVICE',
  'EDUCATIONAL_LEAVE',
  'GARDEN_LEAVE',
  'NON_ACTIVE',
]

// AntD tag colour scheme for the various status enums. Centralised so the same
// colour shows up wherever the status appears (overview chip, status tab, etc.).
const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'green',
  ON_PROBATION: 'blue',
  ON_LEAVE: 'orange',
  ON_BUSINESS_TRIP: 'cyan',
  MATERNITY_LEAVE: 'pink',
  MILITARY_SERVICE: 'volcano',
  EDUCATIONAL_LEAVE: 'geekblue',
  GARDEN_LEAVE: 'gold',
  NON_ACTIVE: 'default',
  SUSPENDED: 'red',
  TERMINATED: 'default',
  RETIRED: 'default',
  CONTRACTOR: 'purple',
  INTERN: 'geekblue',
  VERIFIED: 'green',
  UNVERIFIED: 'orange',
  REJECTED: 'red',
  DRAFT: 'default',
  PENDING: 'orange',
  APPROVED: 'blue',
  ISSUED: 'red',
  APPEALED: 'gold',
  CLOSED: 'default',
  EXPIRED: 'default',
  RENEWED: 'default',
}

const tag = (value?: string | null) =>
  value ? <Tag color={STATUS_COLORS[value] ?? 'default'}>{value.replace(/_/g, ' ')}</Tag> : '—'

export function EmployeeDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canEdit = hasRole(...RoleSets.HR_TEAM_WRITE)
  const canAudit = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR')
  const canSeeDisciplinary = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'AUDITOR')
  const canSeeHealth = hasRole('HR_ADMIN', 'SYSTEM_ADMIN', 'OCCUPATIONAL_HEALTH')

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [audit, setAudit] = useState<AuditEntry[]>([])
  const [identifications, setIdentifications] = useState<Identification[]>([])
  const [addresses, setAddresses] = useState<Address[]>([])
  const [emergencyContacts, setEmergencyContacts] = useState<EmergencyContact[]>([])
  const [contracts, setContracts] = useState<Contract[]>([])
  const [certifications, setCertifications] = useState<Certification[]>([])
  const [health, setHealth] = useState<Health | null>(null)
  // M137 — vaccinations (same role gate as health)
  const [vaccinations, setVaccinations] = useState<Vaccination[]>([])
  const [disciplinary, setDisciplinary] = useState<DisciplinaryAction[]>([])
  const [dependents, setDependents] = useState<Dependent[]>([])
  const [educations, setEducations] = useState<Education[]>([])
  const [experiences, setExperiences] = useState<WorkExperience[]>([])
  const [assets, setAssets] = useState<Asset[]>([])
  const [notes, setNotes] = useState<Note[]>([])
  const [rewards, setRewards] = useState<Reward[]>([])
  const [probationReviews, setProbationReviews] = useState<ProbationReview[]>([])
  const [bankAccounts, setBankAccounts] = useState<BankAccountResponse[]>([])
  const [compensations, setCompensations] = useState<CompensationResponse[]>([])
  const [assignments, setAssignments] = useState<EmployeeAssignment[]>([])
  const [timeline, setTimeline] = useState<TimelineEvent[]>([])
  const [overlays, setOverlays] = useState<StatusOverlay[]>([])
  const [loading, setLoading] = useState(true)

  const [statusModal, setStatusModal] = useState(false)
  const [newStatus, setNewStatus] = useState<EmploymentStatus | undefined>()
  const [reason, setReason] = useState('')
  const [rehireModal, setRehireModal] = useState(false)
  const [rehireDate, setRehireDate] = useState<string>('')
  const [rehireReason, setRehireReason] = useState('')

  // Single bulk loader — fires all reads in parallel, collects results into
  // state slots. Failures on optional tabs (e.g. health for non-OH viewers)
  // are swallowed so a 404 / 403 doesn't block the rest of the page.
  const load = () => {
    setLoading(true)
    Promise.all([
      employeesApi.get(id),
      canAudit ? employeesApi.audit(id) : Promise.resolve([]),
      personalDetailsApi.listIdentifications(id).catch(() => []),
      personalDetailsApi.listAddresses(id).catch(() => []),
      personalDetailsApi.listEmergencyContacts(id).catch(() => []),
      contractsApi.listForEmployee(id).catch(() => []),
      credentialsApi.listCertifications(id).catch(() => []),
      canSeeHealth ? credentialsApi.getHealth(id).catch(() => null) : Promise.resolve(null),
      // M137 — vaccinations: same role gate as health
      canSeeHealth ? credentialsApi.listVaccinations(id).catch(() => []) : Promise.resolve([] as Vaccination[]),
      canSeeDisciplinary ? disciplinaryApi.listForEmployee(id).catch(() => []) : Promise.resolve([]),
      profileTabsApi.listDependents(id).catch(() => []),
      profileTabsApi.listEducation(id).catch(() => []),
      profileTabsApi.listExperience(id).catch(() => []),
      assetsNotesRewardsApi.listAssets(id).catch(() => []),
      assetsNotesRewardsApi.listNotes(id).catch(() => []),
      assetsNotesRewardsApi.listRewards(id).catch(() => []),
      probationReviewsApi.listForEmployee(id).catch(() => []),
      payrollApi.bankAccounts(id).catch(() => []),
      payrollApi.compensationHistory(id).catch(() => []),
      assignmentApi.list(id).catch(() => []),
      timelineApi.forEmployee(id).catch(() => []),
      statusOverlayApi.list(id).catch(() => []),
    ])
      .then(([emp, log, ids, adrs, ecs, cs, certs, hth, vacs, da, deps, eds, exs, ast, nts, rws, prs, banks, comps, asgs, tline, ovls]) => {
        setEmployee(emp)
        setAudit(log)
        setIdentifications(ids)
        setAddresses(adrs)
        setEmergencyContacts(ecs)
        setContracts(cs)
        setCertifications(certs)
        setHealth(hth)
        setVaccinations(vacs)
        setDisciplinary(da)
        setDependents(deps)
        setEducations(eds)
        setExperiences(exs)
        setAssets(ast)
        setNotes(nts)
        setRewards(rws)
        setProbationReviews(prs)
        setBankAccounts(banks)
        setCompensations(comps)
        setAssignments(asgs)
        setTimeline(tline)
        setOverlays(ovls)
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  // ── Column definitions per tab ────────────────────────────────────────────

  const idColumns: ColumnsType<Identification> = useMemo(() => [
    { title: 'Type', dataIndex: 'documentType', render: tag },
    { title: 'Number', dataIndex: 'documentNumberMasked', render: (v?: string | null) => v ?? '—' },
    { title: 'Issue date', dataIndex: 'issueDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Expiry date', dataIndex: 'expiryDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Issuing country', dataIndex: 'issuingCountry', render: (v?: string | null) => v ?? '—' },
    { title: 'Verified', dataIndex: 'verificationStatus', render: tag },
  ], [])

  const addrColumns: ColumnsType<Address> = useMemo(() => [
    { title: 'Type', dataIndex: 'addressType', render: tag },
    {
      title: 'Address',
      render: (_, r) =>
        [r.addressLine1, r.addressLine2, r.city, r.district, r.postalCode, r.country]
          .filter(Boolean)
          .join(', '),
    },
    { title: 'From', dataIndex: 'effectiveFrom' },
    { title: 'To', dataIndex: 'effectiveTo', render: (v?: string | null) => v ?? 'current' },
    {
      title: '',
      dataIndex: 'current',
      render: (v: boolean) => (v ? <Tag color="green">current</Tag> : null),
    },
  ], [])

  const ecColumns: ColumnsType<EmergencyContact> = useMemo(() => [
    { title: 'Name', dataIndex: 'name' },
    { title: 'Relationship', dataIndex: 'relationship', render: tag },
    { title: 'Phone', dataIndex: 'phone' },
    { title: 'Email', dataIndex: 'email', render: (v?: string | null) => v ?? '—' },
    {
      title: '',
      dataIndex: 'primary',
      render: (v: boolean) => (v ? <Tag color="green">PRIMARY</Tag> : null),
    },
  ], [])

  const contractColumns: ColumnsType<Contract> = useMemo(() => [
    { title: 'Contract no', dataIndex: 'contractNo' },
    { title: 'Type', dataIndex: 'contractType', render: tag },
    { title: 'Start', dataIndex: 'startDate' },
    { title: 'End', dataIndex: 'endDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Probation end', dataIndex: 'probationEndDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Status', dataIndex: 'status', render: tag },
  ], [])

  const certColumns: ColumnsType<Certification> = useMemo(() => [
    { title: 'Certification', dataIndex: 'certificationName' },
    { title: 'Authority', dataIndex: 'issuingAuthority', render: (v?: string | null) => v ?? '—' },
    { title: 'License #', dataIndex: 'licenseNumberMasked', render: (v?: string | null) => v ?? '—' },
    { title: 'Issued', dataIndex: 'issueDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Expires', dataIndex: 'expiryDate', render: (v?: string | null) => v ?? '—' },
    { title: 'Verified', dataIndex: 'verificationStatus', render: tag },
  ], [])

  const dependentColumns: ColumnsType<Dependent> = useMemo(() => [
    { title: 'Name', render: (_, r) => `${r.firstName} ${r.lastName}` },
    { title: 'Relationship', dataIndex: 'relationshipType', render: tag },
    { title: 'DOB', dataIndex: 'dateOfBirth', render: (v?: string | null) => v ?? '—' },
    { title: 'Phone', dataIndex: 'phone', render: (v?: string | null) => v ?? '—' },
    {
      title: 'Insurance',
      dataIndex: 'insuranceEligible',
      render: (v: boolean) => (v ? <Tag color="green">ELIGIBLE</Tag> : '—'),
    },
    {
      title: 'Benefits',
      dataIndex: 'benefitEligible',
      render: (v: boolean) => (v ? <Tag color="blue">ELIGIBLE</Tag> : '—'),
    },
    {
      // M135 — derived eligibility state replaces the bare active flag
      // here. Inactive OR end-date-passed renders the same "no longer"
      // chip so HR can scan at a glance.
      title: 'Currently eligible',
      dataIndex: 'currentlyEligible',
      render: (v: boolean, r) =>
        v
          ? <Tag color="green">YES</Tag>
          : (
            <Tag color="orange">
              NO{r.eligibilityEndReason ? ` · ${r.eligibilityEndReason}` : ''}
            </Tag>
          ),
    },
    {
      title: 'End date',
      dataIndex: 'eligibilityEndDate',
      render: (v?: string | null) => v ?? '—',
    },
  ], [])

  const educationColumns: ColumnsType<Education> = useMemo(() => [
    { title: 'Level', dataIndex: 'educationLevel', render: tag },
    { title: 'Institution', dataIndex: 'institutionName' },
    { title: 'Country', dataIndex: 'country', render: (v?: string | null) => v ?? '—' },
    { title: 'Degree', dataIndex: 'degree', render: (v?: string | null) => v ?? '—' },
    { title: 'Major', dataIndex: 'major', render: (v?: string | null) => v ?? '—' },
    { title: 'From', dataIndex: 'startDate', render: (v?: string | null) => v ?? '—' },
    { title: 'To', dataIndex: 'endDate', render: (v?: string | null) => v ?? 'in progress' },
    { title: 'GPA', dataIndex: 'gpa', render: (v?: number | null) => v ?? '—' },
    { title: 'Verified', dataIndex: 'verificationStatus', render: tag },
  ], [])

  const bankAccountColumns: ColumnsType<BankAccountResponse> = useMemo(() => [
    {
      title: '',
      dataIndex: 'primary',
      width: 60,
      render: (v: boolean) => (v ? <Tag color="green">PRIMARY</Tag> : null),
    },
    { title: 'Bank', dataIndex: 'bankName', render: (v?: string | null) => v ?? '—' },
    { title: 'Code', dataIndex: 'bankCode', render: (v?: string | null) => v ?? '—' },
    { title: 'IBAN', dataIndex: 'ibanMasked', render: (v?: string | null) => v ?? '—' },
    {
      title: 'Account',
      dataIndex: 'accountNumberMasked',
      render: (v?: string | null) => v ?? '—',
    },
    { title: 'SWIFT/BIC', dataIndex: 'swiftBic', render: (v?: string | null) => v ?? '—' },
    { title: 'Currency', dataIndex: 'currency' },
    {
      title: 'Split %',
      dataIndex: 'salarySplitPercent',
      render: (v: number) => `${v}%`,
    },
    {
      title: 'Active',
      dataIndex: 'active',
      render: (v: boolean) => (v ? <Tag color="green">YES</Tag> : <Tag>INACTIVE</Tag>),
    },
  ], [])

  const TIMELINE_COLORS: Record<string, string> = {
    HIRE: 'green',
    STATUS_CHANGE: 'blue',
    CONTRACT_SIGNED: 'cyan',
    CONTRACT_ENDED: 'default',
    LEAVE_REQUEST: 'orange',
    BUSINESS_TRIP: 'geekblue',
    PERMISSION: 'gold',
    DISCIPLINARY: 'red',
    PROBATION_REVIEW: 'purple',
    REWARD: 'magenta',
    TERMINATION: 'volcano',
    AUDIT_OTHER: 'default',
  }

  const overlayColumns: ColumnsType<StatusOverlay> = useMemo(() => [
    { title: 'Status', dataIndex: 'status', render: tag },
    {
      title: 'Source',
      dataIndex: 'source',
      width: 140,
      render: (v: string) => <Tag>{v.replace(/_/g, ' ')}</Tag>,
    },
    { title: 'From', dataIndex: 'effectiveFrom' },
    {
      title: 'To',
      dataIndex: 'effectiveTo',
      render: (v?: string | null) => v ?? 'current',
    },
    {
      title: '',
      dataIndex: 'effectiveTo',
      render: (v?: string | null) =>
        v == null ? <Tag color="green">open</Tag> : null,
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      render: (v?: string | null) => v ?? '—',
    },
  ], [])

  const timelineColumns: ColumnsType<TimelineEvent> = useMemo(() => [
    {
      title: 'When',
      dataIndex: 'at',
      width: 180,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: 'Kind',
      dataIndex: 'kind',
      width: 160,
      render: (v: string) => (
        <Tag color={TIMELINE_COLORS[v] ?? 'default'}>{v.replace(/_/g, ' ')}</Tag>
      ),
    },
    { title: 'Event', dataIndex: 'title' },
    {
      title: 'Detail',
      dataIndex: 'detail',
      render: (v?: string | null) => v ?? '—',
    },
    {
      title: 'Actor',
      dataIndex: 'actor',
      render: (v?: string | null) => v ?? '—',
    },
  ], [])

  const assignmentColumns: ColumnsType<EmployeeAssignment> = useMemo(() => [
    { title: 'Type', dataIndex: 'assignmentType', render: tag },
    {
      title: 'Position',
      dataIndex: 'positionId',
      render: (v: string) => (
        <Typography.Text code style={{ fontSize: 12 }}>
          {v.slice(0, 8)}…
        </Typography.Text>
      ),
    },
    {
      title: 'Allocation',
      dataIndex: 'allocationPercent',
      render: (v: number) => `${v}%`,
    },
    { title: 'From', dataIndex: 'effectiveFrom' },
    {
      title: 'To',
      dataIndex: 'effectiveTo',
      render: (v?: string | null) => v ?? 'current',
    },
    {
      title: 'Matrix manager',
      dataIndex: 'matrixManagerId',
      render: (v?: string | null) =>
        v ? <Typography.Text code style={{ fontSize: 12 }}>{v.slice(0, 8)}…</Typography.Text> : '—',
    },
    {
      title: '',
      dataIndex: 'effectiveTo',
      render: (v?: string | null) => (v == null ? <Tag color="green">open</Tag> : null),
    },
  ], [])

  const compensationColumns: ColumnsType<CompensationResponse> = useMemo(() => [
    { title: 'Effective from', dataIndex: 'effectiveFrom' },
    {
      title: 'Effective to',
      dataIndex: 'effectiveTo',
      render: (v?: string | null) => v ?? 'current',
    },
    {
      title: 'Monthly base salary',
      dataIndex: 'monthlyBaseSalary',
      render: (v: number, r) => `${v.toFixed(2)} ${r.currency ?? ''}`,
    },
    { title: 'Reason', dataIndex: 'reason', render: (v?: string | null) => v ?? '—' },
  ], [])

  const probationReviewColumns: ColumnsType<ProbationReview> = useMemo(() => [
    { title: 'Type', dataIndex: 'reviewType', render: tag },
    { title: 'Scheduled', dataIndex: 'scheduledDate' },
    {
      title: 'Completed',
      dataIndex: 'completedDate',
      render: (v?: string | null) => v ?? '—',
    },
    { title: 'Status', dataIndex: 'status', render: tag },
    {
      title: 'Outcome',
      dataIndex: 'outcome',
      render: (v?: string | null) => (v ? tag(v) : '—'),
    },
    {
      title: 'Manager rating',
      dataIndex: 'managerRating',
      render: (v?: number | null) => (v != null ? `${v}/5` : '—'),
    },
    {
      title: 'HR rating',
      dataIndex: 'hrRating',
      render: (v?: number | null) => (v != null ? `${v}/5` : '—'),
    },
  ], [])

  const assetColumns: ColumnsType<Asset> = useMemo(() => [
    { title: 'Type', dataIndex: 'assetType', render: tag },
    { title: 'Name', dataIndex: 'assetName' },
    { title: 'ID/Tag', dataIndex: 'assetIdentifier', render: (v?: string | null) => v ?? '—' },
    { title: 'Status', dataIndex: 'status', render: tag },
    { title: 'Assigned', dataIndex: 'assignedAt' },
    {
      title: 'Returned',
      dataIndex: 'returnedAt',
      render: (v?: string | null) => v ?? '—',
    },
    {
      title: 'Expected',
      dataIndex: 'expectedReturnDate',
      render: (v?: string | null) => v ?? '—',
    },
  ], [])

  const noteColumns: ColumnsType<Note> = useMemo(() => [
    {
      title: '',
      dataIndex: 'pinned',
      width: 32,
      render: (v: boolean) => (v ? '📌' : null),
    },
    { title: 'Type', dataIndex: 'noteType', render: tag },
    { title: 'Visibility', dataIndex: 'visibilityLevel', render: tag },
    { title: 'Body', dataIndex: 'noteBody', ellipsis: true },
    { title: 'By', dataIndex: 'createdBy', render: (v?: string | null) => v ?? '—' },
    {
      title: 'When',
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ], [])

  const rewardColumns: ColumnsType<Reward> = useMemo(() => [
    { title: 'Type', dataIndex: 'rewardType', render: tag },
    { title: 'Title', dataIndex: 'title' },
    { title: 'Awarded', dataIndex: 'awardedAt' },
    {
      title: 'Value',
      render: (_, r) =>
        r.awardValue != null ? `${r.awardValue} ${r.currency ?? ''}` : '—',
    },
    { title: 'By', dataIndex: 'awardedBy', render: (v?: string | null) => v ?? '—' },
  ], [])

  const experienceColumns: ColumnsType<WorkExperience> = useMemo(() => [
    { title: 'Type', dataIndex: 'experienceType', render: tag },
    { title: 'Employer', dataIndex: 'employerName' },
    { title: 'Industry', dataIndex: 'industry', render: (v?: string | null) => v ?? '—' },
    { title: 'Title', dataIndex: 'jobTitle' },
    { title: 'From', dataIndex: 'startDate' },
    { title: 'To', dataIndex: 'endDate', render: (v?: string | null) => v ?? 'current' },
    {
      title: 'Salary',
      render: (_, r) =>
        r.lastSalary != null ? `${r.lastSalary} ${r.lastSalaryCurrency ?? ''}` : '—',
    },
    { title: 'Verified', dataIndex: 'verificationStatus', render: tag },
  ], [])

  const discColumns: ColumnsType<DisciplinaryAction> = useMemo(() => [
    { title: 'No.', dataIndex: 'actionNo' },
    { title: 'Type', dataIndex: 'actionType', render: tag },
    { title: 'Incident', dataIndex: 'incidentDate' },
    { title: 'Action', dataIndex: 'actionDate' },
    { title: 'Status', dataIndex: 'status', render: tag },
    {
      title: 'Appeal',
      dataIndex: 'appealFlag',
      render: (v: boolean) => (v ? <Tag color="gold">FILED</Tag> : '—'),
    },
  ], [])

  const auditColumns: ColumnsType<AuditEntry> = useMemo(() => [
    { title: 'When', dataIndex: 'createdAt', render: (v) => new Date(v).toLocaleString() },
    { title: 'Actor', dataIndex: 'actor' },
    { title: 'Action', dataIndex: 'action', render: (v: string) => <Tag>{v}</Tag> },
    { title: 'IP', dataIndex: 'ipAddress', render: (v?: string | null) => v ?? '—' },
    {
      title: 'New value',
      dataIndex: 'newValue',
      render: (v?: string | null) =>
        v ? (
          <Typography.Text code style={{ whiteSpace: 'pre-wrap' }}>
            {v}
          </Typography.Text>
        ) : (
          '—'
        ),
    },
  ], [])

  if (loading || !employee) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  // ── Tab definitions ───────────────────────────────────────────────────────

  const overviewTab = (
    <Descriptions column={2} bordered size="small">
      <Descriptions.Item label="Employee no">{employee.employeeNo}</Descriptions.Item>
      <Descriptions.Item label="Status">{tag(employee.employmentStatus)}</Descriptions.Item>
      <Descriptions.Item label="First name">{employee.firstName}</Descriptions.Item>
      <Descriptions.Item label="Last name">{employee.lastName}</Descriptions.Item>
      <Descriptions.Item label="Middle name">{employee.middleName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Gender">{employee.gender ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Marital status">
        {employee.maritalStatus ? tag(employee.maritalStatus) : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Nationality">{employee.nationality ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Date of birth">{employee.birthDate ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="National ID">{employee.nationalId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Personal email">{employee.email ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Personal phone">{employee.phone ?? '—'}</Descriptions.Item>
      {/* M133 — Section 3 contact fields */}
      <Descriptions.Item label="Alt. phone">{employee.altPhone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Work email">{employee.workEmail ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Work phone">{employee.workPhone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Extension">{employee.extension ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Desk / seat">{employee.deskNumber ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Hire date">{employee.hireDate}</Descriptions.Item>
      {/* M134 — Section 4 employment fields */}
      <Descriptions.Item label="Seniority date">
        {employee.seniorityDate ?? <span style={{ opacity: 0.5 }}>= hire date</span>}
      </Descriptions.Item>
      <Descriptions.Item label="Employee category">
        {employee.employeeCategory ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Employment type">{tag(employee.employmentType)}</Descriptions.Item>
      <Descriptions.Item label="FTE %">{employee.ftePercent}</Descriptions.Item>
      <Descriptions.Item label="Department">{employee.departmentName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Position">{employee.positionTitle ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Cost centre">{employee.costCentre ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Manager ID">{employee.managerId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Leave group">{employee.leaveGroupId ?? 'default'}</Descriptions.Item>
      <Descriptions.Item label="Payroll group">
        {employee.payrollGroupId ?? 'default'}
      </Descriptions.Item>
      <Descriptions.Item label="Matrix manager">
        {employee.matrixManagerId ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Functional manager">
        {employee.functionalManagerId ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Rehire eligible">
        {employee.rehireEligible === false
          ? <Tag color="red">NO</Tag>
          : <Tag color="green">YES</Tag>}
      </Descriptions.Item>
      <Descriptions.Item label="Previous employee">
        {employee.previousEmployeeId
          ? <Link to={`/employees/${employee.previousEmployeeId}`}>{employee.previousEmployeeId}</Link>
          : '—'}
      </Descriptions.Item>
      {/* M132 — Section 1 cosmetic fields */}
      <Descriptions.Item label="Preferred name">{employee.preferredName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Place of birth">{employee.placeOfBirth ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Blood group">
        {employee.bloodGroup ? <Tag color="red">{employee.bloodGroup}</Tag> : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Native language">{employee.nativeLanguage ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Religion">{employee.religion ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Badge QR">
        <img
          src={`/api/employees/${employee.id}/qr?size=120`}
          alt={`QR code for ${employee.employeeNo}`}
          width={120}
          height={120}
          style={{ display: 'block', border: '1px solid #f0f0f0' }}
        />
      </Descriptions.Item>
      <Descriptions.Item label="Created">
        {new Date(employee.createdAt).toLocaleString()} by {employee.createdBy ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Updated">
        {new Date(employee.updatedAt).toLocaleString()} by {employee.updatedBy ?? '—'}
      </Descriptions.Item>
    </Descriptions>
  )

  const tabItems = [
    { key: 'overview', label: 'Overview', children: overviewTab },
    {
      key: 'overlays',
      label: `Status overlays (${overlays.length})`,
      children: (
        <Table
          rowKey="id"
          columns={overlayColumns}
          dataSource={overlays}
          pagination={false}
          locale={{ emptyText: <Empty description="No concurrent status overlays" /> }}
        />
      ),
    },
    {
      key: 'timeline',
      label: `Timeline (${timeline.length})`,
      children: (
        <Table
          rowKey={(r) => `${r.at}-${r.kind}-${r.referenceId ?? ''}-${r.title}`}
          columns={timelineColumns}
          dataSource={timeline}
          pagination={{ pageSize: 25 }}
          size="small"
          locale={{ emptyText: <Empty description="No lifecycle events on file" /> }}
        />
      ),
    },
    {
      key: 'identifications',
      label: `Identifications (${identifications.length})`,
      children: (
        <Table
          rowKey="id"
          columns={idColumns}
          dataSource={identifications}
          pagination={false}
          locale={{ emptyText: <Empty description="No identification documents" /> }}
        />
      ),
    },
    {
      key: 'addresses',
      label: `Addresses (${addresses.length})`,
      children: (
        <Table
          rowKey="id"
          columns={addrColumns}
          dataSource={addresses}
          pagination={false}
          locale={{ emptyText: <Empty description="No addresses on file" /> }}
        />
      ),
    },
    {
      key: 'emergency',
      label: `Emergency contacts (${emergencyContacts.length})`,
      children: (
        <Table
          rowKey="id"
          columns={ecColumns}
          dataSource={emergencyContacts}
          pagination={false}
          locale={{ emptyText: <Empty description="No emergency contacts on file" /> }}
        />
      ),
    },
    {
      key: 'contracts',
      label: `Contracts (${contracts.length})`,
      children: (
        <Table
          rowKey="id"
          columns={contractColumns}
          dataSource={contracts}
          pagination={false}
          locale={{ emptyText: <Empty description="No contracts on file" /> }}
        />
      ),
    },
    {
      key: 'certifications',
      label: `Certifications (${certifications.length})`,
      children: (
        <Table
          rowKey="id"
          columns={certColumns}
          dataSource={certifications}
          pagination={false}
          locale={{ emptyText: <Empty description="No external certifications on file" /> }}
        />
      ),
    },
    {
      key: 'dependents',
      label: `Dependents (${dependents.length})`,
      children: (
        <Table
          rowKey="id"
          columns={dependentColumns}
          dataSource={dependents}
          pagination={false}
          locale={{ emptyText: <Empty description="No dependents on file" /> }}
        />
      ),
    },
    {
      key: 'education',
      label: `Education (${educations.length})`,
      children: (
        <Table
          rowKey="id"
          columns={educationColumns}
          dataSource={educations}
          pagination={false}
          locale={{ emptyText: <Empty description="No education history on file" /> }}
        />
      ),
    },
    {
      key: 'experience',
      label: `Experience (${experiences.length})`,
      children: (
        <Table
          rowKey="id"
          columns={experienceColumns}
          dataSource={experiences}
          pagination={false}
          locale={{ emptyText: <Empty description="No work experience on file" /> }}
        />
      ),
    },
    {
      key: 'assets',
      label: `Assets (${assets.length})`,
      children: (
        <Table
          rowKey="id"
          columns={assetColumns}
          dataSource={assets}
          pagination={false}
          locale={{ emptyText: <Empty description="No company assets assigned" /> }}
        />
      ),
    },
    {
      key: 'notes',
      label: `Notes (${notes.length})`,
      children: (
        <Table
          rowKey="id"
          columns={noteColumns}
          dataSource={notes}
          pagination={false}
          locale={{ emptyText: <Empty description="No notes on file" /> }}
        />
      ),
    },
    {
      key: 'rewards',
      label: `Rewards (${rewards.length})`,
      children: (
        <Table
          rowKey="id"
          columns={rewardColumns}
          dataSource={rewards}
          pagination={false}
          locale={{ emptyText: <Empty description="No rewards or recognition on file" /> }}
        />
      ),
    },
    {
      key: 'assignments',
      label: `Assignments (${assignments.length})`,
      children: (
        <Table
          rowKey="id"
          columns={assignmentColumns}
          dataSource={assignments}
          pagination={false}
          locale={{ emptyText: <Empty description="No assignments on file — primary position is on Overview" /> }}
        />
      ),
    },
    {
      key: 'probationReviews',
      label: `Probation reviews (${probationReviews.length})`,
      children: (
        <Table
          rowKey="id"
          columns={probationReviewColumns}
          dataSource={probationReviews}
          pagination={false}
          locale={{ emptyText: <Empty description="No probation reviews on file" /> }}
        />
      ),
    },
    {
      key: 'banking',
      label: `Bank accounts (${bankAccounts.length})`,
      children: (
        <Table
          rowKey="id"
          columns={bankAccountColumns}
          dataSource={bankAccounts}
          pagination={false}
          locale={{ emptyText: <Empty description="No bank accounts on file" /> }}
        />
      ),
    },
    {
      key: 'compensation',
      label: `Compensation history (${compensations.length})`,
      children: (
        <Table
          rowKey="id"
          columns={compensationColumns}
          dataSource={compensations}
          pagination={false}
          locale={{ emptyText: <Empty description="No compensation records on file" /> }}
        />
      ),
    },
  ]

  // Health — only OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN can see.
  if (canSeeHealth) {
    tabItems.push({
      key: 'health',
      label: 'Health',
      children: health ? (
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="Fitness certificate date">
            {health.fitnessCertificateDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Next exam date">{health.nextExamDate ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Restrictions" span={2}>
            {health.restrictions ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Notes" span={2}>
            {health.occupationalHealthNotes ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Confidential">
            {health.confidential ? 'Yes' : 'No'}
          </Descriptions.Item>
          {/* M137 — Section 18 disability */}
          <Descriptions.Item label="Disability status">
            {health.disabilityStatus
              ? <Tag color="purple">{health.disabilityStatus}</Tag>
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Disability %">
            {health.disabilityPercent != null
              ? `${health.disabilityPercent}%`
              : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Disability note" span={2}>
            {health.disabilityNote ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Workplace accommodations" span={2}>
            {health.accommodationsNote ?? '—'}
          </Descriptions.Item>
        </Descriptions>
      ) : (
        <Empty description="No health record on file" />
      ),
    })

    // M137 — Vaccinations tab, same role gate
    tabItems.push({
      key: 'vaccinations',
      label: `Vaccinations (${vaccinations.length})`,
      children: (
        <Table
          rowKey="id"
          size="small"
          dataSource={vaccinations}
          columns={[
            { title: 'Vaccine', render: (_, r) => `${r.vaccineCode} — ${r.vaccineName}` },
            { title: 'Administered', dataIndex: 'administeredDate', width: 120 },
            { title: 'By', dataIndex: 'administeredBy', width: 160, render: (v?: string | null) => v ?? '—' },
            { title: 'Lot', dataIndex: 'lotNumber', width: 120, render: (v?: string | null) => v ?? '—' },
            { title: 'Next dose', dataIndex: 'nextDoseDate', width: 120, render: (v?: string | null) => v ?? '—' },
          ]}
          locale={{ emptyText: <Empty description="No vaccinations on file" /> }}
        />
      ),
    })
  }

  // Disciplinary — HR-mediated, restricted.
  if (canSeeDisciplinary) {
    tabItems.push({
      key: 'disciplinary',
      label: `Disciplinary (${disciplinary.length})`,
      children: (
        <Table
          rowKey="id"
          columns={discColumns}
          dataSource={disciplinary}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: <Empty description="No disciplinary actions" /> }}
        />
      ),
    })
  }

  if (canAudit) {
    // M117 — field-change history tab. Reuses the audit_log + M62 history
    // slices already in the DB; renders them as a unified timeline with
    // a JSON before/after diff drawer.
    tabItems.push({
      key: 'changeHistory',
      label: 'Change history',
      children: <ChangeHistoryPanel employeeId={id} />,
    })

    tabItems.push({
      key: 'audit',
      label: `Audit (${audit.length})`,
      children: (
        <Table
          rowKey="id"
          columns={auditColumns}
          dataSource={audit}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: <Empty description="No audit history" /> }}
        />
      ),
    })
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <Link to="/employees">Employees</Link> /{' '}
            <strong>
              {employee.firstName} {employee.lastName}
            </strong>
            <Tag color="blue">{employee.employeeNo}</Tag>
            {tag(employee.employmentStatus)}
          </Space>
        }
        extra={
          canEdit && (
            <Space>
              {(employee.employmentStatus === 'TERMINATED' || employee.employmentStatus === 'RETIRED')
                && employee.rehireEligible !== false && (
                  <Button onClick={() => setRehireModal(true)} type="dashed">
                    Rehire…
                  </Button>
                )}
              <Button onClick={() => setStatusModal(true)}>Change status</Button>
              <Button type="primary" onClick={() => navigate(`/employees/${id}/edit`)}>
                Edit
              </Button>
            </Space>
          )
        }
      >
        <Tabs items={tabItems} defaultActiveKey="overview" />
      </Card>

      <Modal
        title="Change employment status"
        open={statusModal}
        onCancel={() => setStatusModal(false)}
        onOk={async () => {
          if (!newStatus) return
          try {
            await employeesApi.changeStatus(employee.id, newStatus, reason || undefined)
            message.success('Status updated')
            setStatusModal(false)
            setNewStatus(undefined)
            setReason('')
            load()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Status change failed',
            )
          }
        }}
        okButtonProps={{ disabled: !newStatus }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Select
            placeholder="New status"
            style={{ width: '100%' }}
            value={newStatus}
            options={STATUS_OPTIONS.filter((s) => s !== employee.employmentStatus).map((s) => ({
              value: s,
              label: s.replace(/_/g, ' '),
            }))}
            onChange={setNewStatus}
          />
          <Input.TextArea
            placeholder="Reason (optional)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
          />
        </Space>
      </Modal>

      <Modal
        title={`Rehire ${employee.firstName} ${employee.lastName}`}
        open={rehireModal}
        onCancel={() => setRehireModal(false)}
        onOk={async () => {
          if (!rehireDate) {
            message.error('Pick a new hire date')
            return
          }
          try {
            const { rehireApi } = await import('../api/statusOverlay')
            const created = await rehireApi.rehire({
              previousEmployeeId: employee.id,
              newHireDate: rehireDate,
              reason: rehireReason || undefined,
            })
            message.success(`Rehired as ${created.employeeNo}`)
            setRehireModal(false)
            setRehireDate('')
            setRehireReason('')
            navigate(`/employees/${created.id}`)
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
                'Rehire failed',
            )
          }
        }}
        okText="Rehire"
        okButtonProps={{ disabled: !rehireDate }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Creates a new employee row referencing this one as the predecessor.
            PII is copied; tenure starts from the new hire date.
          </Typography.Paragraph>
          <Input
            type="date"
            placeholder="New hire date"
            value={rehireDate}
            onChange={(e) => setRehireDate(e.target.value)}
          />
          <Input.TextArea
            placeholder="Reason (optional but recommended)"
            value={rehireReason}
            onChange={(e) => setRehireReason(e.target.value)}
            rows={3}
          />
        </Space>
      </Modal>
    </Space>
  )
}
