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
import { useAuth } from '../auth/AuthContext'

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
]

// AntD tag colour scheme for the various status enums. Centralised so the same
// colour shows up wherever the status appears (overview chip, status tab, etc.).
const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'green',
  ON_PROBATION: 'blue',
  ON_LEAVE: 'orange',
  ON_BUSINESS_TRIP: 'cyan',
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
  const canEdit = hasRole('HR_ADMIN', 'HR_SPECIALIST')
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
  const [loading, setLoading] = useState(true)

  const [statusModal, setStatusModal] = useState(false)
  const [newStatus, setNewStatus] = useState<EmploymentStatus | undefined>()
  const [reason, setReason] = useState('')

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
    ])
      .then(([emp, log, ids, adrs, ecs, cs, certs, hth, da, deps, eds, exs, ast, nts, rws, prs, banks, comps]) => {
        setEmployee(emp)
        setAudit(log)
        setIdentifications(ids)
        setAddresses(adrs)
        setEmergencyContacts(ecs)
        setContracts(cs)
        setCertifications(certs)
        setHealth(hth)
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
      title: 'Active',
      dataIndex: 'active',
      render: (v: boolean) => (v ? <Tag color="green">YES</Tag> : <Tag>INACTIVE</Tag>),
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
      <Descriptions.Item label="Email">{employee.email ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Phone">{employee.phone ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Hire date">{employee.hireDate}</Descriptions.Item>
      <Descriptions.Item label="Employment type">{tag(employee.employmentType)}</Descriptions.Item>
      <Descriptions.Item label="FTE %">{employee.ftePercent}</Descriptions.Item>
      <Descriptions.Item label="Department">{employee.departmentName ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Position">{employee.positionTitle ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Cost centre">{employee.costCentre ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Manager ID">{employee.managerId ?? '—'}</Descriptions.Item>
      <Descriptions.Item label="Leave group">{employee.leaveGroupId ?? 'default'}</Descriptions.Item>
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
        </Descriptions>
      ) : (
        <Empty description="No health record on file" />
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
    </Space>
  )
}
