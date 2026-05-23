import { useEffect, useMemo, useState } from 'react'
import {
  Button,
  Card,
  Collapse,
  Descriptions,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  downloadBankFile,
  payrollApi,
  type PayrollAllowance,
  type PayrollResult,
  type PayrollRun,
  type PayrollRunStatus,
} from '../api/payroll'
import { employeesApi, type Employee } from '../api/employees'
import { useAuth } from '../auth/AuthContext'
import { WorkflowPanel } from '../components/WorkflowPanel'

const STATUS_COLOR: Record<PayrollRunStatus, string> = {
  DRAFT: 'default',
  CALCULATED: 'gold',
  UNDER_REVIEW: 'orange',
  APPROVED: 'cyan',
  PAID: 'green',
  CLOSED: 'blue',
  REOPENED: 'magenta',
}

export function PayrollRunDetailPage() {
  const { id = '' } = useParams()
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const canCalculate = hasRole('HR_ADMIN', 'SYSTEM_ADMIN', 'PAYROLL_SPECIALIST')
  const canMarkPaid = hasRole('HR_ADMIN', 'SYSTEM_ADMIN', 'FINANCE_USER')

  const [run, setRun] = useState<PayrollRun | null>(null)
  const [results, setResults] = useState<PayrollResult[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [loading, setLoading] = useState(true)
  const [actLoading, setActLoading] = useState(false)
  const [detailsOpen, setDetailsOpen] = useState<PayrollResult | null>(null)
  // M41: allowance lines for the currently-open payslip — lazy-loaded
  // when the modal opens, keyed by employeeId so re-opens are instant.
  const [allowanceCache, setAllowanceCache] = useState<Record<string, PayrollAllowance[]>>({})

  const load = async () => {
    setLoading(true)
    try {
      const [r, res, emp] = await Promise.all([
        payrollApi.run(id),
        payrollApi.results(id),
        employeesApi.list({ size: 500 }),
      ])
      setRun(r)
      setResults(res)
      setEmployees(emp.content)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load run',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  // M41: when the payslip modal opens, fetch the per-employee allowance
  // breakdown once and cache it. Re-opening the same payslip is instant.
  useEffect(() => {
    if (!detailsOpen) return
    if (allowanceCache[detailsOpen.employeeId]) return
    payrollApi
      .allowances(id, detailsOpen.employeeId)
      .then((lines) =>
        setAllowanceCache((prev) => ({ ...prev, [detailsOpen.employeeId]: lines })),
      )
      .catch(() => {
        // Silently degrade — the rest of the payslip still renders.
      })
  }, [detailsOpen, id, allowanceCache])

  const employeeMap = useMemo(() => new Map(employees.map((e) => [e.id, e])), [employees])

  if (loading || !run) {
    return (
      <div style={{ textAlign: 'center', padding: 64 }}>
        <Spin />
      </div>
    )
  }

  const wrap = async (label: string, fn: () => Promise<unknown>) => {
    setActLoading(true)
    try {
      await fn()
      message.success(label + ' OK')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          label + ' failed',
      )
    } finally {
      setActLoading(false)
    }
  }

  const columns: ColumnsType<PayrollResult> = [
    { title: 'Payslip #', dataIndex: 'payslipNo', width: 120 },
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      render: (eid: string) => {
        const e = employeeMap.get(eid)
        return e ? `${e.employeeNo} ${e.lastName} ${e.firstName}` : eid
      },
    },
    {
      title: 'Base',
      dataIndex: 'baseSalary',
      align: 'right',
      render: (v: number) => v,
      width: 100,
    },
    {
      title: 'OT hours / pay',
      align: 'right',
      render: (_, r) => `${r.overtimeHours}h / ${r.overtimePay}`,
      width: 130,
    },
    { title: 'Bonus', dataIndex: 'bonusAmount', align: 'right', width: 90 },
    { title: 'Allowance', dataIndex: 'allowanceAmount', align: 'right', width: 100 },
    {
      title: 'Gross',
      dataIndex: 'grossAmount',
      align: 'right',
      width: 100,
      render: (v: number) => <strong>{v}</strong>,
    },
    { title: 'Income tax', dataIndex: 'incomeTax', align: 'right', width: 110 },
    { title: 'DSMF (em)', dataIndex: 'dsmfEmployee', align: 'right', width: 100 },
    { title: 'MMI (em)', dataIndex: 'mmiEmployee', align: 'right', width: 90 },
    { title: 'Unempl (em)', dataIndex: 'unemplEmployee', align: 'right', width: 100 },
    {
      title: 'Net',
      dataIndex: 'netAmount',
      align: 'right',
      width: 110,
      render: (v: number) => <strong style={{ color: '#7BC828' }}>{v}</strong>,
    },
    {
      title: '',
      width: 100,
      render: (_, r) => (
        <Button size="small" onClick={() => setDetailsOpen(r)}>
          Payslip
        </Button>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <Link to="/payroll/runs">Payroll</Link> /{' '}
            <strong>{run.runNo}</strong>
            <Tag color="geekblue">
              {run.periodYear}/{String(run.periodMonth).padStart(2, '0')}
            </Tag>
            <Tag color={STATUS_COLOR[run.status]}>{run.status.replace(/_/g, ' ')}</Tag>
          </Space>
        }
        extra={
          <Space wrap>
            {(run.status === 'DRAFT' ||
              run.status === 'CALCULATED' ||
              run.status === 'REOPENED') &&
              canCalculate && (
                <Popconfirm
                  title="Calculate payroll?"
                  description="Re-runs the engine against current APPROVED timesheets."
                  onConfirm={() => wrap('Calculation', () => payrollApi.calculate(run.id))}
                >
                  <Button loading={actLoading}>Calculate</Button>
                </Popconfirm>
              )}
            {(run.status === 'CALCULATED' || run.status === 'REOPENED') && canCalculate && (
              <Button
                type="primary"
                loading={actLoading}
                onClick={() => wrap('Submit', () => payrollApi.submit(run.id))}
              >
                Submit for approval
              </Button>
            )}
            {run.status === 'APPROVED' && canMarkPaid && (
              <Popconfirm
                title="Mark this run paid?"
                description="Locks the consumed timesheets and stamps a payment date."
                onConfirm={() => wrap('Mark paid', () => payrollApi.markPaid(run.id))}
              >
                <Button type="primary" loading={actLoading}>
                  Mark paid
                </Button>
              </Popconfirm>
            )}
            {run.status === 'PAID' && hasRole('HR_ADMIN', 'SYSTEM_ADMIN') && (
              <Button onClick={() => wrap('Close', () => payrollApi.close(run.id))}>Close</Button>
            )}
            {(run.status === 'APPROVED' || run.status === 'PAID' || run.status === 'CLOSED') && (
              <Button onClick={() => downloadBankFile(run.id)}>Download bank file (CSV)</Button>
            )}
            <Button onClick={() => navigate('/payroll/runs')}>Back to list</Button>
          </Space>
        }
      >
        <Descriptions column={4} bordered size="small">
          <Descriptions.Item label="Employees">{run.employeeCount}</Descriptions.Item>
          <Descriptions.Item label="Gross">
            {run.totalGross} {run.currency}
          </Descriptions.Item>
          <Descriptions.Item label="Income tax">{run.totalIncomeTax}</Descriptions.Item>
          <Descriptions.Item label="Net">
            <strong>
              {run.totalNet} {run.currency}
            </strong>
          </Descriptions.Item>
          <Descriptions.Item label="Allowances">{run.totalAllowance ?? 0}</Descriptions.Item>
          <Descriptions.Item label="DSMF (employee)">{run.totalDsmfEmployee}</Descriptions.Item>
          <Descriptions.Item label="DSMF (employer)">{run.totalDsmfEmployer}</Descriptions.Item>
          <Descriptions.Item label="MMI (employee)">{run.totalMmiEmployee}</Descriptions.Item>
          <Descriptions.Item label="MMI (employer)">{run.totalMmiEmployer}</Descriptions.Item>
          <Descriptions.Item label="Unempl (employee)">{run.totalUnemplEmployee}</Descriptions.Item>
          <Descriptions.Item label="Unempl (employer)">{run.totalUnemplEmployer}</Descriptions.Item>
          <Descriptions.Item label="Calculated">
            {run.calculatedAt ? `${new Date(run.calculatedAt).toLocaleString()} · ${run.calculatedBy ?? ''}` : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Paid">
            {run.paidAt ? new Date(run.paidAt).toLocaleString() : '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Employee payslips">
        {results.length === 0 ? (
          <Typography.Text type="secondary">
            No results yet. Make sure timesheets for this period are APPROVED, then click Calculate.
          </Typography.Text>
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={results}
            pagination={false}
            size="small"
          />
        )}
      </Card>

      {run.workflowInstanceId && (
        <Card title="Workflow">
          <WorkflowPanel
            module="PAYROLL"
            entity="PayrollRun"
            subjectId={run.id}
            onChanged={() => load()}
          />
        </Card>
      )}

      <Modal
        open={!!detailsOpen}
        title={detailsOpen ? `Payslip ${detailsOpen.payslipNo}` : ''}
        footer={null}
        width={720}
        onCancel={() => setDetailsOpen(null)}
      >
        {detailsOpen && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="Base salary">{detailsOpen.baseSalary}</Descriptions.Item>
              <Descriptions.Item label="Worked hours">{detailsOpen.workedHours}</Descriptions.Item>
              <Descriptions.Item label="Overtime hours">
                {detailsOpen.overtimeHours}
              </Descriptions.Item>
              <Descriptions.Item label="Overtime pay">{detailsOpen.overtimePay}</Descriptions.Item>
              <Descriptions.Item label="Bonus">{detailsOpen.bonusAmount}</Descriptions.Item>
              <Descriptions.Item label="Allowance (total)">
                {detailsOpen.allowanceAmount}
              </Descriptions.Item>
              <Descriptions.Item label="Gross">
                <strong>{detailsOpen.grossAmount}</strong>
              </Descriptions.Item>
              <Descriptions.Item label="Income tax">{detailsOpen.incomeTax}</Descriptions.Item>
              <Descriptions.Item label="DSMF (employee / employer)">
                {detailsOpen.dsmfEmployee} / {detailsOpen.dsmfEmployer}
              </Descriptions.Item>
              <Descriptions.Item label="MMI (employee / employer)">
                {detailsOpen.mmiEmployee} / {detailsOpen.mmiEmployer}
              </Descriptions.Item>
              <Descriptions.Item label="Unempl (employee / employer)">
                {detailsOpen.unemplEmployee} / {detailsOpen.unemplEmployer}
              </Descriptions.Item>
              <Descriptions.Item label="Net" span={2}>
                <strong style={{ color: '#7BC828', fontSize: 18 }}>
                  {detailsOpen.netAmount} {run.currency}
                </strong>
              </Descriptions.Item>
            </Descriptions>
            {/* M41: per-line allowance breakdown — only shown when at
                least one row exists. Lazy-loaded by the effect above. */}
            {(allowanceCache[detailsOpen.employeeId]?.length ?? 0) > 0 && (
              <Card size="small" title="Allowance breakdown">
                <Table<PayrollAllowance>
                  rowKey="id"
                  size="small"
                  pagination={false}
                  dataSource={allowanceCache[detailsOpen.employeeId]}
                  columns={[
                    {
                      title: 'Type',
                      render: (_, a) => a.allowanceTypeName ?? a.allowanceTypeCode ?? '(unknown)',
                    },
                    { title: 'Code', dataIndex: 'allowanceTypeCode', width: 110 },
                    {
                      title: 'Tax',
                      width: 90,
                      render: (_, a) =>
                        a.taxable ? (
                          <Tag color="orange">Taxable</Tag>
                        ) : (
                          <Tag color="green">Tax-free</Tag>
                        ),
                    },
                    {
                      title: 'Amount',
                      dataIndex: 'amount',
                      align: 'right',
                      width: 110,
                      render: (v: number, a) => `${v} ${a.currency}`,
                    },
                  ]}
                />
              </Card>
            )}
            <Collapse
              items={[
                {
                  key: 'trace',
                  label: 'Calculation trace (auditor view)',
                  children: (
                    <pre style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}>
                      {detailsOpen.calculationDetails
                        ? JSON.stringify(JSON.parse(detailsOpen.calculationDetails), null, 2)
                        : '(no trace)'}
                    </pre>
                  ),
                },
              ]}
            />
          </Space>
        )}
      </Modal>
    </Space>
  )
}
