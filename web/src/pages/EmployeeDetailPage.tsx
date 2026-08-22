import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  InputNumber,
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
import { LeaveEntitlementBreakdown } from '../components/LeaveEntitlementBreakdown'
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
  type ComponentAssignment,
  type CostAllocation,
  type PayrollLoan,
  type SalaryComponent,
} from '../api/payroll'
import {
  assignmentApi,
  type EmployeeAssignment,
} from '../api/staffingCatalog'
import { timelineApi, type TimelineEvent } from '../api/team'
import { statusOverlayApi, type StatusOverlay } from '../api/statusOverlay'
import { useAuth } from '../auth/AuthContext'
import { Roles, RoleSets } from '../auth/roleSets'
// M117 — per-employee field-change history (employment slices + status slices + audit diff)
import { ChangeHistoryPanel } from '../components/ChangeHistoryPanel'
import { countryName } from '../config/countries'
import { HIDDEN_PROFILE_TABS } from '../config/hiddenProfileTabs'
import { leaveApi, type LeaveBalance, type LeaveType } from '../api/leave'
import { leaveGroupsApi, type LeaveGroup } from '../api/leaveGroups'
import { payrollGroupsApi, type PayrollGroup } from '../api/payrollGroups'
// M169 — employee document management
import {
  employeeDocumentsApi,
  DOCUMENT_TYPE_LABELS,
  type EmployeeDocument,
} from '../api/employeeDocuments'

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
  /**
   * Setting salary is narrower than editing the employee: the server allows
   * only SYSTEM_ADMIN and HR_ADMIN (SecurityRoles.WRITE_HR_ADMIN_ONLY on
   * POST /payroll/compensation). An HR_SPECIALIST may edit the person but not
   * their pay, so this must not reuse canEdit — that would show a button that
   * 403s, and imply an authority they do not have.
   */
  const canSetSalary = hasRole(Roles.SYSTEM_ADMIN, Roles.HR_ADMIN)
  const canAudit = hasRole('SYSTEM_ADMIN', 'HR_ADMIN', 'AUDITOR')
  /** Same gate as the server on POST /employees/{id}/login. */
  const canCreateLogin = hasRole(Roles.SYSTEM_ADMIN, Roles.HR_ADMIN)
  const canSeeDisciplinary = hasRole('HR_ADMIN', 'HR_SPECIALIST', 'SYSTEM_ADMIN', 'AUDITOR')
  const canSeeHealth = hasRole('HR_ADMIN', 'SYSTEM_ADMIN', 'OCCUPATIONAL_HEALTH')

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [creatingLogin, setCreatingLogin] = useState(false)
  // Which inner tab each group shows. Only set when something needs to steer
  // it — an empty panel pointing at the tab that fills it.
  const [subTab, setSubTab] = useState<Record<string, string>>({})
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
  const [empDocuments, setEmpDocuments] = useState<EmployeeDocument[]>([])
  const [loading, setLoading] = useState(true)

  // M349, M352, M355: salary components, loans, cost allocations
  const [componentAssignments, setComponentAssignments] = useState<ComponentAssignment[]>([])
  const [costAllocations, setCostAllocations] = useState<CostAllocation[]>([])
  const [loans, setLoans] = useState<PayrollLoan[]>([])
  const [components, setComponents] = useState<SalaryComponent[]>([])
  const [leaveBalances, setLeaveBalances] = useState<LeaveBalance[]>([])
  const [leaveTypes, setLeaveTypes] = useState<LeaveType[]>([])
  const [salaryModalOpen, setSalaryModalOpen] = useState(false)
  const [salaryAmount, setSalaryAmount] = useState<number | undefined>(undefined)
  const [salaryFrom, setSalaryFrom] = useState('')
  const [salaryReason, setSalaryReason] = useState('')
  const [assignmentModalOpen, setAssignmentModalOpen] = useState(false)
  const [selectedComponentId, setSelectedComponentId] = useState<string>()
  const [amountOverride, setAmountOverride] = useState<number>()
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [costAllocationModalOpen, setCostAllocationModalOpen] = useState(false)
  const [costAllocationRows, setCostAllocationRows] = useState<Array<{ costCenterCode: string; allocationPct: number }>>([{ costCenterCode: '', allocationPct: 100 }])
  const [costAllocationEffectiveFrom, setCostAllocationEffectiveFrom] = useState('')

  const [statusModal, setStatusModal] = useState(false)
  const [newStatus, setNewStatus] = useState<EmploymentStatus | undefined>()
  const [reason, setReason] = useState('')
  // The profile printed a raw UUID for every reference it held: the line
  // manager, the prior employee row, three approvers and two groups. Nobody can
  // read a UUID. Both maps resolve id → display name; anything unresolved falls
  // back to the id, so a reference the caller is not scoped to see (ABAC hides
  // it as a 404) still renders rather than vanishing.
  const [refNames, setRefNames] = useState<Record<string, string>>({})
  const [groupNames, setGroupNames] = useState<Record<string, string>>({})

  const [rehireModal, setRehireModal] = useState(false)
  const [rehireDate, setRehireDate] = useState<string>('')
  const [rehireReason, setRehireReason] = useState('')

  // Single bulk loader — fires all reads in parallel, collects results into
  // state slots. Failures on optional tabs (e.g. health for non-OH viewers)
  // are swallowed so a 404 / 403 doesn't block the rest of the page.
  //
  // A tab hidden by HIDDEN_PROFILE_TABS skips its read rather than fetching
  // rows nothing will render — the profile already makes ~30 calls per open.
  // The slot stays in place because the results are destructured by position.
  const shown = (tab: string) => !HIDDEN_PROFILE_TABS.has(tab)
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
      canSeeHealth && shown('health')
        ? credentialsApi.getHealth(id).catch(() => null) : Promise.resolve(null),
      // M137 — vaccinations: same role gate as health
      canSeeHealth && shown('vaccinations')
        ? credentialsApi.listVaccinations(id).catch(() => []) : Promise.resolve([] as Vaccination[]),
      canSeeDisciplinary ? disciplinaryApi.listForEmployee(id).catch(() => []) : Promise.resolve([]),
      shown('dependents') ? profileTabsApi.listDependents(id).catch(() => []) : Promise.resolve([]),
      shown('education') ? profileTabsApi.listEducation(id).catch(() => []) : Promise.resolve([]),
      shown('experience') ? profileTabsApi.listExperience(id).catch(() => []) : Promise.resolve([]),
      assetsNotesRewardsApi.listAssets(id).catch(() => []),
      assetsNotesRewardsApi.listNotes(id).catch(() => []),
      assetsNotesRewardsApi.listRewards(id).catch(() => []),
      probationReviewsApi.listForEmployee(id).catch(() => []),
      payrollApi.bankAccounts(id).catch(() => []),
      payrollApi.compensationHistory(id).catch(() => []),
      assignmentApi.list(id).catch(() => []),
      timelineApi.forEmployee(id).catch(() => []),
      statusOverlayApi.list(id).catch(() => []),
      employeeDocumentsApi.list(id).catch(() => []),
      payrollApi.componentAssignments(id, true).catch(() => []),
      payrollApi.costAllocations(id).catch(() => []),
      payrollApi.loans({ employeeId: id }).catch(() => []),
      payrollApi.components().catch(() => []),
      // Absence balance and salary belong on the person, not on a screen the
      // user has to go and find — both fail soft so one outage cannot blank
      // the whole profile.
      leaveApi.balances({ employeeId: id }).catch(() => []),
      leaveApi.types(true).catch(() => []),
      // Both lists are small and are read only to turn the group ids on the
      // employment tab into the names people actually use. Appended at the end
      // so the positional destructure below keeps its existing slots.
      leaveGroupsApi.list().catch(() => [] as LeaveGroup[]),
      // activeOnly=false: an employee can still sit on a group that was since
      // deactivated, and that assignment should read as a name, not a UUID.
      payrollGroupsApi.list(false).catch(() => [] as PayrollGroup[]),
    ])
      .then(([emp, log, ids, adrs, ecs, cs, certs, hth, vacs, da, deps, eds, exs, ast, nts, rws, prs, banks, comps, asgs, tline, ovls, docs, compAsgs, costAllocs, lns, cmps, bals, ltypes, lgroups, pgroups]) => {
        setEmployee(emp)
        setGroupNames({
          ...Object.fromEntries(lgroups.map((g) => [g.id, g.name])),
          ...Object.fromEntries(pgroups.map((g) => [g.id, g.name])),
        })
        setLeaveBalances(bals)
        setLeaveTypes(ltypes)
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
        setEmpDocuments(docs as EmployeeDocument[])
        setComponentAssignments(compAsgs as ComponentAssignment[])
        setCostAllocations(costAllocs as CostAllocation[])
        setLoans(lns as PayrollLoan[])
        setComponents(cmps as SalaryComponent[])
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  /**
   * Resolve the handful of employee references on the profile to names.
   *
   * There is no bulk name-lookup endpoint, so this fetches each referenced id
   * once — at most five, usually one or two, and only the ones not already
   * cached. A failed lookup caches the id as its own "name" so a reference the
   * caller cannot see is not retried on every render.
   */
  useEffect(() => {
    if (!employee) return
    const referenced = [
      employee.managerId,
      employee.previousEmployeeId,
      employee.timesheetApproverId,
      employee.expenseApproverId,
      employee.hrTimesheetVerifierId,
    ].filter((v): v is string => !!v)
    const missing = [...new Set(referenced)].filter((r) => !(r in refNames))
    if (missing.length === 0) return
    Promise.all(
      missing.map((r) =>
        employeesApi
          .get(r)
          .then(({ firstName, lastName, employeeNo }) =>
            [r, `${firstName} ${lastName} (${employeeNo})`] as const)
          .catch(() => [r, r] as const),
      ),
    ).then((pairs) => setRefNames((prev) => ({ ...prev, ...Object.fromEntries(pairs) })))
  }, [employee, refNames])

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

  const personalFacts = (
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
      <Descriptions.Item label="Date of birth">{employee.birthDate ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="National ID">{employee.nationalId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Personal email">{employee.email ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Personal phone">{employee.phone ?? '—'}</Descriptions.Item>
      {/* M133 — Section 3 contact fields */}
      <Descriptions.Item label="Alt. phone">{employee.altPhone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Work email">{employee.workEmail ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Work phone">{employee.workPhone ?? '—'}</Descriptions.Item>
      {/* V329 — birth place, split into country / city / address */}
      <Descriptions.Item label="Country of birth">
        {countryName(employee.birthCountry) || '—'}
      </Descriptions.Item>
      <Descriptions.Item label="City of birth">{employee.birthCity ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Address of birth">{employee.birthAddress ?? '—'}</Descriptions.Item>
      {/* M150 — workforce-register master data */}
      <Descriptions.Item label="External HR ID">{employee.externalHrId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Full name (local)">{employee.fullNameLocal ?? '—'}</Descriptions.Item>
      {/*
        Badge QR removed. It rendered as a broken image for every user since it
        went in: the <img> requests /api/employees/{id}/qr directly, and the
        Bearer token is added by the axios interceptor in api/client.ts, which
        a browser-issued image request never passes through — so the endpoint's
        isAuthenticated() check 401s every time. This edition also hides
        /attendance/devices, so there is no terminal to scan a badge with. The
        endpoint and EmployeeQrCodeService are untouched; rendering it needs the
        PNG fetched through the api client and shown as an object URL.
      */}
      <Descriptions.Item label="Created">
        {new Date(employee.createdAt).toLocaleString()} by {employee.createdBy ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Updated">
        {new Date(employee.updatedAt).toLocaleString()} by {employee.updatedBy ?? '—'}
      </Descriptions.Item>
    </Descriptions>
  )

  /** Employee reference → a clickable name, falling back to the raw id. */
  const personRef = (refId?: string | null) =>
    refId ? <Link to={`/employees/${refId}`}>{refNames[refId] ?? refId}</Link> : null

  /** Group reference → its name; null means the tenant default applies. */
  const groupRef = (groupId?: string | null) =>
    groupId
      ? (groupNames[groupId] ?? groupId)
      : <span style={{ opacity: 0.5 }}>default</span>

  /**
   * An employee with no login cannot file a timesheet, and a month with no
   * timesheet cannot be paid — so this is worth surfacing on the profile
   * rather than leaving it to whoever administers the identity server.
   */
  const createLogin = () => {
    setCreatingLogin(true)
    employeesApi
      .createLogin(employee!.id)
      .then((updated) => {
        setEmployee(updated)
        message.success(
          `Login ${updated.username} created. The employee sets their own password at first`
          + ' sign-in — send them a password reset to get them started.',
        )
      })
      .catch((e) => message.error(e?.response?.data?.message ?? 'Could not create the login'))
      .finally(() => setCreatingLogin(false))
  }

  const employmentFacts = (
    <Descriptions column={2} bordered size="small">
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
      <Descriptions.Item label="Manager">{personRef(employee.managerId) ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Leave group">{groupRef(employee.leaveGroupId)}</Descriptions.Item>
      <Descriptions.Item label="Payroll group">
        {groupRef(employee.payrollGroupId)}
      </Descriptions.Item>
      {/*
        Matrix manager and functional manager removed. Both are documented in
        EmployeeRequest as informational and "not consumed by workflow engine",
        neither is on the create form, and neither has a column in the customer's
        workbook — so they printed a UUID nothing routes on. The fields stay on
        the API and the record; only the profile stops showing them.
      */}
      <Descriptions.Item label="Rehire eligible">
        {employee.rehireEligible === false
          ? <Tag color="red">NO</Tag>
          : <Tag color="green">YES</Tag>}
      </Descriptions.Item>
      <Descriptions.Item label="Previous employee">
        {personRef(employee.previousEmployeeId) ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Position (local)">
        {employee.positionTitleLocal ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Occupation classification">
        {employee.occupationClassification ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Position classification">
        {employee.positionClassification ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Work type">
        {employee.workType ? <Tag>{employee.workType}</Tag> : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Project">{employee.projectName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Professional experience">
        {employee.professionalExperienceYears != null
          ? `${employee.professionalExperienceYears} yrs`
          : '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Job description">
        {employee.jobDescriptionStatus ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Sign-in account">
        {employee.username
          ? <Tag color="green">{employee.username}</Tag>
          : (
            <Space size="small">
              <span style={{ opacity: 0.65 }}>No login — cannot file a timesheet</span>
              {canCreateLogin && (
                <Button size="small" loading={creatingLogin} onClick={createLogin}>
                  Create login
                </Button>
              )}
            </Space>
          )}
      </Descriptions.Item>
    </Descriptions>
  )

  const scheduleFacts = (
    <Descriptions column={2} bordered size="small">
      <Descriptions.Item label="Work schedule">
        {employee.workScheduleText ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Work time">{employee.workTimeText ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Lunch time">{employee.lunchTimeText ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Offshore schedule">
        {employee.offshoreWorkScheduleText ?? '—'}
      </Descriptions.Item>
      <Descriptions.Item label="Summarized period">
        {employee.summarizedPeriodMethod ?? '—'}
      </Descriptions.Item>
    </Descriptions>
  )

  const approvalsFacts = (
    <Descriptions column={2} bordered size="small">
      {/* Null approver = routes to the line manager; say so rather than showing a dash. */}
      <Descriptions.Item label="Timesheet approver">
        {personRef(employee.timesheetApproverId)
          ?? <span style={{ opacity: 0.5 }}>= line manager</span>}
      </Descriptions.Item>
      <Descriptions.Item label="Expense approver">
        {personRef(employee.expenseApproverId)
          ?? <span style={{ opacity: 0.5 }}>= line manager</span>}
      </Descriptions.Item>
      <Descriptions.Item label="HR timesheet verifier">
        {personRef(employee.hrTimesheetVerifierId) ?? '—'}
      </Descriptions.Item>
    </Descriptions>
  )

  const recruitmentFacts = (
    <Descriptions column={2} bordered size="small">
      <Descriptions.Item label="Source of hire">{employee.sourceOfHire ?? '—'}</Descriptions.Item>
    </Descriptions>
  )


  // ── M169: documents columns ──────────────────────────────────────────────
  const docColumns: ColumnsType<EmployeeDocument> = [
    {
      title: 'Type',
      dataIndex: 'documentType',
      render: (v: string) => DOCUMENT_TYPE_LABELS[v as keyof typeof DOCUMENT_TYPE_LABELS] ?? v,
    },
    { title: 'Title', dataIndex: 'title', render: (v?: string | null) => v ?? '—' },
    { title: 'Expiry', dataIndex: 'expiryDate', render: (v?: string | null) => v ?? '—' },
    {
      title: 'Restricted',
      dataIndex: 'restricted',
      render: (v: boolean) => v ? <Tag color="red">Restricted</Tag> : '—',
    },
    {
      title: 'Attachment',
      dataIndex: 'attachmentId',
      render: (v?: string | null) => v ? <Tag color="blue">Attached</Tag> : '—',
    },
    { title: 'Added by', dataIndex: 'createdBy', render: (v?: string | null) => v ?? '—' },
    { title: 'Added', dataIndex: 'createdAt', render: (v: string) => new Date(v).toLocaleDateString() },
  ]

  const tabItems = [
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
      label: `Compensation`,
      children: (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Card
            title="Compensation history"
            size="small"
            extra={
              canSetSalary && (
                <Button size="small" type="primary" onClick={() => setSalaryModalOpen(true)}>
                  Set monthly salary
                </Button>
              )
            }
          >
            <Table
              rowKey="id"
              size="small"
              columns={compensationColumns}
              dataSource={compensations}
              pagination={false}
              locale={{ emptyText: <Empty description="No compensation records on file" /> }}
            />
          </Card>
          <Card
            title="Salary components"
            size="small"
            extra={
              canEdit && (
                <Button size="small" onClick={() => setAssignmentModalOpen(true)}>
                  Add assignment
                </Button>
              )
            }
          >
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={componentAssignments}
              columns={[
                { title: 'Component', dataIndex: 'componentName' },
                { title: 'Code', dataIndex: 'componentCode', width: 100 },
                { title: 'Kind', dataIndex: 'componentKind', width: 100, render: (v: string) => <Tag>{v}</Tag> },
                { title: 'Amount override', dataIndex: 'amountOverride', width: 130, align: 'right', render: (v?: number) => v ?? '—' },
                { title: 'Effective from', dataIndex: 'effectiveFrom', width: 130 },
                { title: 'Effective to', dataIndex: 'effectiveTo', width: 130, render: (v?: string) => v ?? 'current' },
              ]}
              locale={{ emptyText: <Empty description="No component assignments" /> }}
            />
          </Card>
          <Card
            title="Cost center allocations"
            size="small"
            extra={
              canEdit && (
                <Button size="small" onClick={() => setCostAllocationModalOpen(true)}>
                  Update allocations
                </Button>
              )
            }
          >
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={costAllocations}
              columns={[
                { title: 'Cost center', dataIndex: 'costCenterCode' },
                { title: 'Allocation %', dataIndex: 'allocationPct', width: 120, align: 'right', render: (v: number) => `${v}%` },
                { title: 'Effective from', dataIndex: 'effectiveFrom', width: 130 },
                { title: 'Effective to', dataIndex: 'effectiveTo', width: 130, render: (v?: string) => v ?? 'current' },
              ]}
              locale={{ emptyText: <Empty description="No cost allocations" /> }}
            />
          </Card>
          <Card
            title="Payroll loans"
            size="small"
          >
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={loans}
              columns={[
                { title: 'Principal', dataIndex: 'principalAmount', width: 120, align: 'right', render: (v?: number) => v ?? '****' },
                { title: 'Monthly installment', dataIndex: 'monthlyInstallment', width: 150, align: 'right', render: (v?: number) => v ?? '****' },
                { title: 'Outstanding', dataIndex: 'outstandingBalance', width: 130, align: 'right', render: (v?: number) => v ?? '****' },
                { title: 'Status', dataIndex: 'status', width: 120, render: (s: string) => <Tag>{s}</Tag> },
                { title: 'Start deduction', render: (_, l) => `${l.startDeductionYear}-${String(l.startDeductionMonth).padStart(2, '0')}`, width: 130 },
              ]}
              locale={{ emptyText: <Empty description="No loans" /> }}
            />
          </Card>
        </Space>
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

  // The absence balance belongs with the person. Reading it used to mean
  // leaving the profile for Leave & Absence > Leave Balances and finding the
  // same employee again.
  tabItems.push({
    key: 'absenceBalance',
    label: `Absence balance (${leaveBalances.length})`,
    children: (
      <Table
        rowKey="id"
        size="small"
        dataSource={leaveBalances}
        pagination={false}
        locale={{
          // A dead end before. The balance is produced by entitlement, which
          // is set on the next tab along — and Edit does not carry a Time &
          // absence tab, so people went looking for it there and found
          // nothing. Say where it comes from, and take them.
          emptyText: (
            <Empty description="No leave balance for this year yet">
              {canEdit && (
                <Button
                  type="primary"
                  ghost
                  onClick={() => setSubTab((cur) => ({ ...cur, timeAbsence: 'leaveEntitlement' }))}
                >
                  Set up entitlement
                </Button>
              )}
            </Empty>
          ),
        }}
        columns={[
          {
            title: 'Leave type',
            dataIndex: 'leaveTypeId',
            render: (v: string) => leaveTypes.find((t) => t.id === v)?.name ?? v,
          },
          { title: 'Year', dataIndex: 'year', width: 80 },
          { title: 'Entitled', dataIndex: 'entitlementDays', width: 100 },
          { title: 'Carried fwd', dataIndex: 'carriedForwardDays', width: 120 },
          { title: 'Adjustments', dataIndex: 'adjustmentDays', width: 115 },
          { title: 'Used', dataIndex: 'usedDays', width: 80 },
          { title: 'Reserved', dataIndex: 'reservedDays', width: 100 },
          {
            title: 'Remaining',
            dataIndex: 'remainingDays',
            width: 110,
            render: (v: number) => <strong>{v}</strong>,
          },
        ]}
      />
    ),
  })

  // M151 — itemised annual leave entitlement. Sits next to Compensation
  // because it answers the same kind of question: what is this number made of.
  tabItems.push({
    key: 'leaveEntitlement',
    label: 'Leave entitlement',
    children: <LeaveEntitlementBreakdown employeeId={id!} canEdit={canEdit} />,
  })

  // M169 — employee document management (all HR readers).
  tabItems.push({
    key: 'documents',
    label: `Documents (${empDocuments.length})`,
    children: (
      <Table
        rowKey="id"
        columns={docColumns}
        dataSource={empDocuments}
        pagination={false}
        locale={{ emptyText: <Empty description="No documents on file" /> }}
      />
    ),
  })

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

  /**
   * PRD Appendix A — the profile shows the same groups the create form uses,
   * instead of the 26 flat tabs it had grown into. Nothing is removed: each
   * group holds the facts for that part of the record plus the detail tables
   * that belong to it, as a second row of tabs.
   *
   * Built from `tabItems` by key rather than by rewriting each tab, so the
   * permission conditions that decide whether a tab exists at all still apply
   * — a group whose members are all withheld simply does not appear.
   */
  const pick = (...keys: string[]) =>
    keys
      .filter((k) => !HIDDEN_PROFILE_TABS.has(k))
      .map((k) => tabItems.find((t) => t.key === k))
      .filter(Boolean) as typeof tabItems

  const group = (facts: React.ReactNode, keys: string[], groupKey?: string) => {
    const members = pick(...keys)
    if (!facts && members.length === 0) return null
    if (!facts && members.length === 1) return members[0].children
    return (
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {facts}
        {members.length > 0 && (
          <Tabs
            size="small"
            items={members}
            // Uncontrolled unless a group is named — only the one that needs
            // steering pays for the state.
            activeKey={groupKey ? (subTab[groupKey] ?? members[0].key) : undefined}
            onChange={groupKey
              ? (k) => setSubTab((cur) => ({ ...cur, [groupKey]: k }))
              : undefined}
          />
        )}
      </Space>
    )
  }

  const groupedTabs = [
    { key: 'personal', label: 'Personal & contact',
      children: group(personalFacts, ['identifications', 'addresses', 'emergency', 'dependents',
        'education', 'experience', 'certifications', 'health', 'vaccinations']) },
    { key: 'employment', label: 'Employment & job',
      children: group(employmentFacts, ['assignments', 'overlays', 'probationReviews',
        'assets', 'notes', 'rewards', 'disciplinary']) },
    { key: 'contract', label: 'Contract', children: group(null, ['contracts']) },
    { key: 'compensation', label: 'Compensation', children: group(null, ['compensation', 'banking']) },
    // Not one of the PRD's ten, but asked for separately: the balance belongs
    // with the person rather than on a screen they have to go and find.
    { key: 'timeAbsence', label: 'Time & absence',
      children: group(null, ['absenceBalance', 'leaveEntitlement'], 'timeAbsence') },
    { key: 'schedule', label: 'Work schedule', children: group(scheduleFacts, []) },
    { key: 'approvals', label: 'Approvals', children: group(approvalsFacts, []) },
    { key: 'recruitment', label: 'Recruitment', children: group(recruitmentFacts, []) },
    { key: 'documents', label: 'Documents', children: group(null, ['documents']) },
    { key: 'history', label: 'History & audit',
      children: group(null, ['timeline', 'changeHistory', 'audit']) },
  ].filter((t) => t.children != null)

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
        <Tabs items={groupedTabs} defaultActiveKey="personal" />
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

      {/* Set monthly salary, without leaving the person for Payroll. */}
      <Modal
        title="Set monthly salary"
        open={salaryModalOpen}
        okText="Save"
        onCancel={() => {
          setSalaryModalOpen(false)
          setSalaryAmount(undefined)
          setSalaryFrom('')
          setSalaryReason('')
        }}
        onOk={async () => {
          if (salaryAmount == null || !salaryFrom) {
            message.error('Amount and effective date are required')
            return
          }
          try {
            // Effective-dated: this appends to the salary history rather than
            // overwriting the current figure, so a raise keeps its trail.
            await payrollApi.setCompensation({
              employeeId: employee.id,
              monthlyBaseSalary: salaryAmount,
              currency: 'AZN',
              effectiveFrom: salaryFrom,
              reason: salaryReason || undefined,
            })
            message.success('Salary saved')
            setSalaryModalOpen(false)
            setSalaryAmount(undefined)
            setSalaryFrom('')
            setSalaryReason('')
            load()
          } catch (err) {
            message.error(
              (err as { response?: { data?: { message?: string } } }).response?.data?.message
                ?? 'Could not save the salary',
            )
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>
            <div style={{ marginBottom: 4 }}>Monthly base salary (AZN)</div>
            <InputNumber
              style={{ width: '100%' }}
              min={0}
              step={100}
              value={salaryAmount}
              onChange={(v: number | null) => setSalaryAmount(v ?? undefined)}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Effective from</div>
            <Input type="date" value={salaryFrom} onChange={(e) => setSalaryFrom(e.target.value)} />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>Reason (optional)</div>
            <Input
              value={salaryReason}
              onChange={(e) => setSalaryReason(e.target.value)}
              placeholder="e.g. annual review, promotion"
            />
          </div>
        </Space>
      </Modal>

      {/* M349 — Component assignment modal */}
      <Modal
        title="Add component assignment"
        open={assignmentModalOpen}
        onCancel={() => {
          setAssignmentModalOpen(false)
          setSelectedComponentId(undefined)
          setAmountOverride(undefined)
          setEffectiveFrom('')
        }}
        onOk={async () => {
          if (!selectedComponentId || !effectiveFrom) {
            message.error('Component and effective date are required')
            return
          }
          try {
            await payrollApi.createComponentAssignment(employee.id, {
              componentId: selectedComponentId,
              effectiveFrom,
              amountOverride,
            })
            message.success('Component assigned')
            setAssignmentModalOpen(false)
            setSelectedComponentId(undefined)
            setAmountOverride(undefined)
            setEffectiveFrom('')
            load()
          } catch (err) {
            message.error((err as any).response?.data?.message ?? 'Assignment failed')
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>Component:</Typography.Text>
          <Select
            style={{ width: '100%' }}
            placeholder="Select component"
            value={selectedComponentId}
            onChange={setSelectedComponentId}
            options={components.map((c) => ({
              value: c.id,
              label: `${c.code} — ${c.name} (${c.kind})`,
            }))}
          />
          <Typography.Text>Amount override (optional):</Typography.Text>
          <Input
            type="number"
            value={amountOverride}
            onChange={(e) => setAmountOverride(Number(e.target.value))}
            placeholder="Leave blank to use component default"
          />
          <Typography.Text>Effective from:</Typography.Text>
          <Input
            type="date"
            value={effectiveFrom}
            onChange={(e) => setEffectiveFrom(e.target.value)}
          />
        </Space>
      </Modal>

      {/* M355 — Cost allocation modal */}
      <Modal
        title="Update cost center allocations"
        open={costAllocationModalOpen}
        onCancel={() => {
          setCostAllocationModalOpen(false)
          setCostAllocationRows([{ costCenterCode: '', allocationPct: 100 }])
          setCostAllocationEffectiveFrom('')
        }}
        onOk={async () => {
          const total = costAllocationRows.reduce((sum, r) => sum + (r.allocationPct || 0), 0)
          if (total !== 100) {
            message.error('Allocation percentages must sum to 100%')
            return
          }
          if (!costAllocationEffectiveFrom) {
            message.error('Effective date is required')
            return
          }
          try {
            await payrollApi.setCostAllocations(employee.id, {
              allocations: costAllocationRows,
              effectiveFrom: costAllocationEffectiveFrom,
            })
            message.success('Allocations updated')
            setCostAllocationModalOpen(false)
            setCostAllocationRows([{ costCenterCode: '', allocationPct: 100 }])
            setCostAllocationEffectiveFrom('')
            load()
          } catch (err) {
            message.error((err as any).response?.data?.message ?? 'Update failed')
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Typography.Text>Cost center allocations:</Typography.Text>
          {costAllocationRows.map((row, idx) => (
            <Space key={idx} style={{ width: '100%' }}>
              <Input
                placeholder="Cost center code"
                value={row.costCenterCode}
                onChange={(e) => {
                  const updated = [...costAllocationRows]
                  updated[idx].costCenterCode = e.target.value
                  setCostAllocationRows(updated)
                }}
              />
              <Input
                type="number"
                placeholder="%"
                value={row.allocationPct}
                onChange={(e) => {
                  const updated = [...costAllocationRows]
                  updated[idx].allocationPct = Number(e.target.value)
                  setCostAllocationRows(updated)
                }}
                style={{ width: 80 }}
              />
              {idx > 0 && (
                <Button
                  size="small"
                  onClick={() => setCostAllocationRows(costAllocationRows.filter((_, i) => i !== idx))}
                >
                  Remove
                </Button>
              )}
            </Space>
          ))}
          <Button
            size="small"
            onClick={() => setCostAllocationRows([...costAllocationRows, { costCenterCode: '', allocationPct: 0 }])}
          >
            Add row
          </Button>
          <Typography.Text>
            Total: {costAllocationRows.reduce((sum, r) => sum + (r.allocationPct || 0), 0)}%
          </Typography.Text>
          <Typography.Text>Effective from:</Typography.Text>
          <Input
            type="date"
            value={costAllocationEffectiveFrom}
            onChange={(e) => setCostAllocationEffectiveFrom(e.target.value)}
          />
        </Space>
      </Modal>
    </Space>
  )
}
