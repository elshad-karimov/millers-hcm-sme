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
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  recruitmentApi,
  type Candidate,
  type Referral,
  type ReferralStatus,
  type Vacancy,
} from '../api/recruitment'
import { employeesApi, type Employee } from '../api/employees'
import { payrollApi, type PayrollRun } from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

/**
 * M295 — Recruitment PRD Phase F: employee referral program. Log a
 * referral, watch it qualify after hire, and pay the bonus into a payroll
 * run.
 */
const STATUS_COLOR: Record<ReferralStatus, string> = {
  SUBMITTED: 'default',
  HIRED: 'blue',
  QUALIFIED: 'gold',
  PAID: 'green',
  REJECTED: 'red',
}

function errMsg(e: unknown, fallback: string) {
  return (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback
}

export function ReferralsPage() {
  const { message } = AntdApp.useApp()
  const { hasRole } = useAuth()
  const canWrite = hasRole(...RoleSets.RECRUITMENT_TEAM)
  const canPay = hasRole(...RoleSets.HR_ADMIN_WRITE)

  const [rows, setRows] = useState<Referral[]>([])
  const [loading, setLoading] = useState(true)
  const [newOpen, setNewOpen] = useState(false)
  const [form] = Form.useForm()
  const [employees, setEmployees] = useState<Employee[]>([])
  const [candidates, setCandidates] = useState<Candidate[]>([])
  const [vacancies, setVacancies] = useState<Vacancy[]>([])

  const [payFor, setPayFor] = useState<Referral | null>(null)
  const [runs, setRuns] = useState<PayrollRun[]>([])
  const [payRun, setPayRun] = useState<string | undefined>(undefined)

  const load = useCallback(() => {
    setLoading(true)
    recruitmentApi
      .referrals()
      .then(setRows)
      .catch((e) => message.error(errMsg(e, 'Failed to load referrals')))
      .finally(() => setLoading(false))
  }, [message])

  useEffect(load, [load])

  const openNew = async () => {
    form.resetFields()
    setNewOpen(true)
    try {
      const [emp, cand, vac] = await Promise.all([
        employeesApi.list({ size: 500 }),
        recruitmentApi.candidates({ size: 500 }),
        recruitmentApi.vacancies({ size: 200 }),
      ])
      setEmployees(emp.content)
      setCandidates(cand.content)
      setVacancies(vac.content)
    } catch (e) {
      message.error(errMsg(e, 'Failed to load options'))
    }
  }

  const create = async () => {
    const v = await form.validateFields()
    try {
      await recruitmentApi.createReferral(v)
      message.success('Referral logged')
      setNewOpen(false)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }

  const reject = async (r: Referral) => {
    try {
      await recruitmentApi.rejectReferral(r.id)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }

  const openPay = async (r: Referral) => {
    setPayFor(r)
    setPayRun(undefined)
    try {
      const all = await payrollApi.runs()
      setRuns(all.filter((x) => x.status !== 'PAID' && x.status !== 'CLOSED'))
    } catch (e) {
      message.error(errMsg(e, 'Failed to load payroll runs'))
    }
  }

  const pay = async () => {
    if (!payFor || !payRun) return
    try {
      await recruitmentApi.payReferral(payFor.id, payRun)
      message.success('Referral bonus pushed to payroll')
      setPayFor(null)
      load()
    } catch (e) {
      message.error(errMsg(e, 'Failed'))
    }
  }

  const cols: ColumnsType<Referral> = [
    { title: '#', dataIndex: 'referralNo', width: 110 },
    { title: 'Referrer', dataIndex: 'referrerName', ellipsis: true },
    { title: 'Candidate', dataIndex: 'candidateName', ellipsis: true },
    {
      title: 'Vacancy',
      dataIndex: 'vacancyTitle',
      ellipsis: true,
      render: (v?: string | null) => v ?? '—',
    },
    {
      title: 'Bonus',
      width: 120,
      render: (_, r) => `${r.bonusAmount} ${r.currency}`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 110,
      render: (s: ReferralStatus) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Qualifies',
      dataIndex: 'qualifiedAt',
      width: 110,
      render: (d?: string | null) => (d ? new Date(d).toLocaleDateString() : '—'),
    },
    {
      title: 'Actions',
      width: 180,
      render: (_, r) => (
        <Space size="small">
          {canPay && r.status === 'QUALIFIED' && (
            <Button size="small" type="primary" onClick={() => openPay(r)}>
              Pay
            </Button>
          )}
          {canWrite && r.status !== 'PAID' && r.status !== 'REJECTED' && (
            <Popconfirm title="Reject this referral?" onConfirm={() => reject(r)}>
              <Button size="small" danger>
                Reject
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Card
      title={<Typography.Title level={4} style={{ margin: 0 }}>Employee referrals</Typography.Title>}
      extra={
        canWrite && (
          <Button type="primary" onClick={openNew}>
            New referral
          </Button>
        )
      }
    >
      <Table<Referral>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={rows}
        columns={cols}
        pagination={{ pageSize: 25 }}
        locale={{ emptyText: 'No referrals yet' }}
      />

      <Modal
        title="New referral"
        open={newOpen}
        onOk={create}
        onCancel={() => setNewOpen(false)}
        okText="Log referral"
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="referrerEmployeeId" label="Referrer (employee)" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Employee who referred"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="candidateId" label="Referred candidate" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Candidate"
              options={candidates.map((c) => ({
                value: c.id,
                label: `${c.candidateNo} — ${c.firstName} ${c.lastName}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="vacancyId" label="Vacancy (optional)">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="Vacancy"
              options={vacancies.map((v) => ({ value: v.id, label: `${v.vacancyNo} — ${v.title}` }))}
            />
          </Form.Item>
          <Space>
            <Form.Item name="bonusAmount" label="Bonus (blank = default)">
              <InputNumber min={0} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="qualifyingDays" label="Qualifying days">
              <InputNumber min={0} max={365} style={{ width: 130 }} />
            </Form.Item>
          </Space>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={payFor ? `Pay ${payFor.referralNo} — ${payFor.bonusAmount} ${payFor.currency}` : 'Pay referral'}
        open={!!payFor}
        onOk={pay}
        okButtonProps={{ disabled: !payRun }}
        onCancel={() => setPayFor(null)}
        okText="Push to payroll"
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          The bonus is added to the chosen payroll run as a one-time bonus for the referrer.
        </Typography.Paragraph>
        <Select
          style={{ width: '100%' }}
          placeholder="Pick an open payroll run"
          value={payRun}
          onChange={setPayRun}
          options={runs.map((r) => ({
            value: r.id,
            label: `${r.runNo} — ${r.periodYear}/${String(r.periodMonth).padStart(2, '0')} (${r.status})`,
          }))}
        />
      </Modal>
    </Card>
  )
}
