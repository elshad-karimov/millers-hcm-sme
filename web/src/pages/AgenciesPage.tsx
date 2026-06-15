import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  recruitmentApi,
  type Agency,
  type AgencyFeeType,
  type AgencyInvoice,
  type AgencyRequest,
  type AgencySubmission,
  type Candidate,
  type InvoiceStatus,
  type SubmissionStatus,
  type Vacancy,
} from '../api/recruitment'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

/** M296 — Recruitment PRD Phase F: agency master, ownership, invoicing. */

const SUB_COLOR: Record<SubmissionStatus, string> = {
  ACTIVE: 'green',
  EXPIRED: 'default',
  HIRED: 'blue',
  WITHDRAWN: 'red',
}
const INV_COLOR: Record<InvoiceStatus, string> = {
  DRAFT: 'default',
  SENT: 'blue',
  PAID: 'green',
  CANCELLED: 'red',
}
function errMsg(e: unknown, f: string) {
  return (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? f
}

export function AgenciesPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.RECRUITMENT_TEAM)
  const canAdmin = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [agencies, setAgencies] = useState<Agency[]>([])
  const [subs, setSubs] = useState<AgencySubmission[]>([])
  const [invoices, setInvoices] = useState<AgencyInvoice[]>([])

  const load = useCallback(() => {
    recruitmentApi.agencies(false).then(setAgencies).catch(() => undefined)
    recruitmentApi.agencySubmissions().then(setSubs).catch(() => undefined)
    recruitmentApi.agencyInvoices().then(setInvoices).catch(() => undefined)
  }, [])
  useEffect(load, [load])

  // ── Agency master ────────────────────────────────────────────────────
  const [agencyOpen, setAgencyOpen] = useState(false)
  const [agencyEditing, setAgencyEditing] = useState<Agency | null>(null)
  const [agencyForm] = Form.useForm()
  const watchFeeType = Form.useWatch('feeType', agencyForm) as AgencyFeeType | undefined
  const openAgency = (a?: Agency) => {
    setAgencyEditing(a ?? null)
    agencyForm.resetFields()
    agencyForm.setFieldsValue(
      a ?? { feeType: 'PERCENT_SALARY', ownershipDays: 180, currency: 'AZN', active: true },
    )
    setAgencyOpen(true)
  }
  const saveAgency = async () => {
    const v = (await agencyForm.validateFields()) as AgencyRequest
    try {
      if (agencyEditing) await recruitmentApi.updateAgency(agencyEditing.id, v)
      else await recruitmentApi.createAgency(v)
      message.success('Saved')
      setAgencyOpen(false)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }

  // ── Submission ───────────────────────────────────────────────────────
  const [subOpen, setSubOpen] = useState(false)
  const [subForm] = Form.useForm()
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [vacancies, setVacancies] = useState<Vacancy[]>([])
  const openSub = async () => {
    subForm.resetFields()
    setSubOpen(true)
    try {
      const [c, v] = await Promise.all([
        recruitmentApi.candidates({ size: 500 }),
        recruitmentApi.vacancies({ size: 200 }),
      ])
      setCandidates(c.content)
      setVacancies(v.content)
    } catch (e) {
      message.error(errMsg(e, 'Failed to load options'))
    }
  }
  const saveSub = async () => {
    const v = await subForm.validateFields()
    try {
      await recruitmentApi.createSubmission(v)
      message.success('Submission logged')
      setSubOpen(false)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }
  const withdraw = async (s: AgencySubmission) => {
    try {
      await recruitmentApi.withdrawSubmission(s.id)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }

  // ── Invoice ──────────────────────────────────────────────────────────
  const invAction = async (i: AgencyInvoice, patch: Parameters<typeof recruitmentApi.updateAgencyInvoice>[1]) => {
    try {
      await recruitmentApi.updateAgencyInvoice(i.id, patch)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }
  const editAmount = (i: AgencyInvoice) => {
    const raw = window.prompt(`Fee amount for ${i.invoiceNo} (${i.currency}):`, String(i.feeAmount))
    if (raw == null) return
    const amt = Number(raw)
    if (Number.isNaN(amt) || amt < 0) {
      message.error('Enter a valid amount')
      return
    }
    invAction(i, { feeAmount: amt })
  }

  const agencyCols: ColumnsType<Agency> = [
    { title: '#', dataIndex: 'agencyNo', width: 100 },
    { title: 'Name', dataIndex: 'name', ellipsis: true },
    { title: 'Contact', dataIndex: 'contactName', render: (v?: string) => v ?? '—' },
    {
      title: 'Fee',
      render: (_, a) =>
        a.feeType === 'PERCENT_SALARY' ? `${a.feePercent ?? '?'}% of salary` : `${a.feeFixed ?? '?'} ${a.currency}`,
    },
    { title: 'Ownership', dataIndex: 'ownershipDays', width: 100, render: (d: number) => `${d}d` },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 80,
      render: (b: boolean) => (b ? <Tag color="green">Yes</Tag> : <Tag>No</Tag>),
    },
    ...(canAdmin
      ? [{ title: '', width: 80, render: (_: unknown, a: Agency) => <Button size="small" onClick={() => openAgency(a)}>Edit</Button> }]
      : []),
  ]

  const subCols: ColumnsType<AgencySubmission> = [
    { title: '#', dataIndex: 'submissionNo', width: 110 },
    { title: 'Agency', dataIndex: 'agencyName', ellipsis: true },
    { title: 'Candidate', dataIndex: 'candidateName', ellipsis: true },
    { title: 'Vacancy', dataIndex: 'vacancyTitle', ellipsis: true, render: (v?: string | null) => v ?? '—' },
    {
      title: 'Owned until',
      dataIndex: 'ownershipUntil',
      width: 120,
      render: (d: string) => new Date(d).toLocaleDateString(),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: SubmissionStatus) => <Tag color={SUB_COLOR[s]}>{s}</Tag>,
    },
    ...(canWrite
      ? [
          {
            title: '',
            width: 110,
            render: (_: unknown, s: AgencySubmission) =>
              s.status === 'ACTIVE' || s.status === 'EXPIRED' ? (
                <Popconfirm title="Withdraw this submission?" onConfirm={() => withdraw(s)}>
                  <Button size="small" danger>
                    Withdraw
                  </Button>
                </Popconfirm>
              ) : null,
          },
        ]
      : []),
  ]

  const invCols: ColumnsType<AgencyInvoice> = [
    { title: '#', dataIndex: 'invoiceNo', width: 110 },
    { title: 'Agency', dataIndex: 'agencyName', ellipsis: true },
    { title: 'Candidate', dataIndex: 'candidateName', ellipsis: true },
    { title: 'Fee', width: 130, render: (_, i) => `${i.feeAmount} ${i.currency}` },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: InvoiceStatus) => <Tag color={INV_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Issued',
      dataIndex: 'issuedAt',
      width: 110,
      render: (d: string) => new Date(d).toLocaleDateString(),
    },
    ...(canAdmin
      ? [
          {
            title: 'Actions',
            width: 250,
            render: (_: unknown, i: AgencyInvoice) => (
              <Space size="small" wrap>
                {i.status === 'DRAFT' && (
                  <>
                    <Button size="small" onClick={() => editAmount(i)}>Amount</Button>
                    <Button size="small" type="primary" onClick={() => invAction(i, { status: 'SENT' })}>
                      Send
                    </Button>
                  </>
                )}
                {i.status === 'SENT' && (
                  <Button size="small" type="primary" onClick={() => invAction(i, { status: 'PAID' })}>
                    Mark paid
                  </Button>
                )}
                {i.status !== 'PAID' && i.status !== 'CANCELLED' && (
                  <Popconfirm title="Cancel this invoice?" onConfirm={() => invAction(i, { status: 'CANCELLED' })}>
                    <Button size="small" danger>
                      Cancel
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]
      : []),
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Recruitment agencies</Typography.Title>}>
      <Tabs
        items={[
          {
            key: 'agencies',
            label: 'Agencies',
            children: (
              <Space direction="vertical" style={{ width: '100%' }}>
                {canAdmin && (
                  <Button type="primary" onClick={() => openAgency()}>
                    New agency
                  </Button>
                )}
                <Table<Agency> rowKey="id" size="small" columns={agencyCols} dataSource={agencies} pagination={{ pageSize: 25 }} />
              </Space>
            ),
          },
          {
            key: 'submissions',
            label: 'Submissions',
            children: (
              <Space direction="vertical" style={{ width: '100%' }}>
                {canWrite && (
                  <Button type="primary" onClick={openSub}>
                    New submission
                  </Button>
                )}
                <Table<AgencySubmission> rowKey="id" size="small" columns={subCols} dataSource={subs} pagination={{ pageSize: 25 }} />
              </Space>
            ),
          },
          {
            key: 'invoices',
            label: 'Invoices',
            children: (
              <Table<AgencyInvoice> rowKey="id" size="small" columns={invCols} dataSource={invoices} pagination={{ pageSize: 25 }} />
            ),
          },
        ]}
      />

      {/* Agency modal */}
      <Modal title={agencyEditing ? 'Edit agency' : 'New agency'} open={agencyOpen} onOk={saveAgency} onCancel={() => setAgencyOpen(false)} okText="Save" destroyOnClose>
        <Form form={agencyForm} layout="vertical">
          <Form.Item name="name" label="Agency name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Space>
            <Form.Item name="contactName" label="Contact">
              <Input />
            </Form.Item>
            <Form.Item name="contactEmail" label="Email">
              <Input />
            </Form.Item>
            <Form.Item name="contactPhone" label="Phone">
              <Input />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="feeType" label="Fee type" rules={[{ required: true }]}>
              <Select
                style={{ width: 180 }}
                options={[
                  { value: 'PERCENT_SALARY', label: '% of salary' },
                  { value: 'FIXED', label: 'Fixed amount' },
                ]}
              />
            </Form.Item>
            {watchFeeType === 'FIXED' ? (
              <Form.Item name="feeFixed" label="Fixed fee">
                <InputNumber min={0} style={{ width: 140 }} />
              </Form.Item>
            ) : (
              <Form.Item name="feePercent" label="Fee %">
                <InputNumber min={0} max={100} step={0.5} style={{ width: 120 }} />
              </Form.Item>
            )}
            <Form.Item name="currency" label="Currency">
              <Input maxLength={3} style={{ width: 80 }} />
            </Form.Item>
          </Space>
          <Space>
            <Form.Item name="ownershipDays" label="Ownership window (days)">
              <InputNumber min={0} max={730} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="active" label="Active" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Submission modal */}
      <Modal title="New agency submission" open={subOpen} onOk={saveSub} onCancel={() => setSubOpen(false)} okText="Submit" destroyOnClose>
        <Form form={subForm} layout="vertical">
          <Form.Item name="agencyId" label="Agency" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={agencies.filter((a) => a.active).map((a) => ({ value: a.id, label: `${a.agencyNo} — ${a.name}` }))}
            />
          </Form.Item>
          <Form.Item name="candidateId" label="Candidate" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={candidates.map((c) => ({ value: c.id, label: `${c.candidateNo} — ${c.firstName} ${c.lastName}` }))}
            />
          </Form.Item>
          <Form.Item name="vacancyId" label="Vacancy (optional)">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              options={vacancies.map((v) => ({ value: v.id, label: `${v.vacancyNo} — ${v.title}` }))}
            />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
