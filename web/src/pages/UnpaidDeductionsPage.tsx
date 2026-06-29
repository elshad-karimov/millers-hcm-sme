import { useState } from 'react'
import { Button, Card, Col, Form, InputNumber, message, Row, Select, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi } from '../api/leave'

interface SyncResult {
  year: number
  month: number
  workingDaysPerMonth: number
  created: number
  skipped: number
  details: string[]
}

interface Deduction {
  id: string
  employeeId: string
  deductionType: string
  description: string
  amountPerPeriod: number
  startPeriodYear: number
  startPeriodMonth: number
  endPeriodYear: number
  endPeriodMonth: number
  status: string
  sourceLeaveRequestId: string | null
  sourceLeaveDays: number | null
}

const MONTHS = Array.from({ length: 12 }, (_, i) => ({ value: i + 1, label: new Date(0, i).toLocaleString('default', { month: 'long' }) }))

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v)

export function UnpaidDeductionsPage() {
  const [form] = Form.useForm()
  const [employeeId, setEmployeeId] = useState('')
  const [syncing, setSyncing] = useState(false)
  const [listing, setListing] = useState(false)
  const [result, setResult] = useState<SyncResult | null>(null)
  const [deductions, setDeductions] = useState<Deduction[]>([])

  const now = new Date()

  const handleSync = async (values: { year: number; month: number; workingDaysPerMonth: number }) => {
    setSyncing(true)
    try {
      const res = await leaveApi.syncUnpaidDeductions(values.year, values.month, values.workingDaysPerMonth)
      setResult(res)
      message.success(`Sync complete: ${res.created} created, ${res.skipped} skipped`)
    } catch {
      message.error('Sync failed')
    } finally {
      setSyncing(false)
    }
  }

  const handleList = async () => {
    if (!employeeId.trim()) { message.warning('Enter employee ID'); return }
    setListing(true)
    try {
      const data = await leaveApi.listUnpaidDeductions(employeeId.trim())
      setDeductions(data)
    } catch {
      message.error('Failed to load deductions')
    } finally {
      setListing(false)
    }
  }

  const cols: ColumnsType<Deduction> = [
    { title: 'Description', dataIndex: 'description', ellipsis: true },
    { title: 'Days', dataIndex: 'sourceLeaveDays', render: v => v ?? '—' },
    { title: 'Amount', dataIndex: 'amountPerPeriod', render: v => fmt(v) },
    { title: 'Period', render: (_, r) => `${r.startPeriodYear}-${String(r.startPeriodMonth).padStart(2, '0')}` },
    { title: 'Status', dataIndex: 'status' },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Typography.Title level={4} style={{ marginBottom: 16 }}>Unpaid Leave → Payroll Bridge</Typography.Title>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="Sync Period" size="small">
            <Form form={form} layout="inline" onFinish={handleSync}
              initialValues={{ year: now.getFullYear(), month: now.getMonth() + 1, workingDaysPerMonth: 22 }}>
              <Form.Item name="year" label="Year">
                <InputNumber min={2020} max={2100} style={{ width: 80 }} />
              </Form.Item>
              <Form.Item name="month" label="Month">
                <Select style={{ width: 120 }} options={MONTHS} />
              </Form.Item>
              <Form.Item name="workingDaysPerMonth" label="Working days/month">
                <InputNumber min={1} max={31} style={{ width: 70 }} />
              </Form.Item>
              <Form.Item>
                <Button type="primary" htmlType="submit" loading={syncing}>Sync</Button>
              </Form.Item>
            </Form>

            {result && (
              <Row gutter={16} style={{ marginTop: 16 }}>
                <Col><Typography.Text strong>{result.created}</Typography.Text> created</Col>
                <Col><Typography.Text>{result.skipped}</Typography.Text> skipped</Col>
              </Row>
            )}
            {result?.details.length ? (
              <ul style={{ marginTop: 8, fontSize: 12, color: '#666', maxHeight: 150, overflow: 'auto' }}>
                {result.details.map((d, i) => <li key={i}>{d}</li>)}
              </ul>
            ) : null}
          </Card>
        </Col>

        <Col span={12}>
          <Card title="Employee Deduction History" size="small">
            <Row gutter={8} align="middle">
              <Col flex={1}>
                <input placeholder="Employee UUID" value={employeeId} onChange={e => setEmployeeId(e.target.value)}
                  style={{ width: '100%', padding: '4px 8px', border: '1px solid #d9d9d9', borderRadius: 4 }} />
              </Col>
              <Col>
                <Button onClick={handleList} loading={listing}>Load</Button>
              </Col>
            </Row>
            <Table
              style={{ marginTop: 12 }}
              size="small"
              columns={cols}
              dataSource={deductions}
              rowKey="id"
              pagination={{ pageSize: 8 }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
