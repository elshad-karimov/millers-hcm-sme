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
    ])
      .then(([emp, log, ids, adrs, ecs, cs, certs, hth, da]) => {
        setEmployee(emp)
        setAudit(log)
        setIdentifications(ids)
        setAddresses(adrs)
        setEmergencyContacts(ecs)
        setContracts(cs)
        setCertifications(certs)
        setHealth(hth)
        setDisciplinary(da)
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
