import { useCallback, useEffect, useState } from 'react'
import {
  Alert, App as AntdApp, Card, Col, DatePicker, Descriptions, Drawer, Empty,
  Row, Space, Statistic, Table, Tag, Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { useSearchParams } from 'react-router-dom'
import {
  categoryLabel, timePayInputsApi, type EmployeePreview, type PeriodPreview,
} from '../api/timePayInputs'

/**
 * Payroll inputs — approved timesheet quantities, priced.
 *
 * Read-only by construction: this creates no payroll run, result or payslip.
 * It exists so payroll can put these figures next to the workbook they replace
 * and check them before the engine is allowed to pay anyone.
 *
 * Only approved and locked months appear. A draft month has not been judged by
 * anyone, and showing payroll a number nobody stands behind is how an unchecked
 * figure becomes a payment.
 */
export function PayrollTimeInputsPage() {
  const { message } = AntdApp.useApp()
  const [params, setParams] = useSearchParams()

  const raw = params.get('period')
  const parsed = raw ? dayjs(raw, 'YYYY-MM', true) : null
  const period = parsed && parsed.isValid() ? parsed : dayjs().startOf('month')
  const year = period.year()
  const month = period.month() + 1

  const [data, setData] = useState<PeriodPreview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState<EmployeePreview | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await timePayInputsApi.period(year, month))
    } catch (err) {
      const msg = errorOf(err, 'Could not price this period')
      setError(msg)
      setData(null)
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }, [year, month, message])

  useEffect(() => { load() }, [load])

  const columns: ColumnsType<EmployeePreview> = [
    { title: 'Emp No', dataIndex: 'employeeNo', width: 110, fixed: 'left' },
    { title: 'Employee', dataIndex: 'employeeName', width: 200, fixed: 'left' },
    {
      title: 'Status', dataIndex: 'timesheetStatus', width: 110,
      render: (v: string) => <Tag color={v === 'LOCKED' ? 'purple' : 'green'}>{v}</Tag>,
    },
    ...['OFFSHORE_HOURS', 'ONSHORE_HOURS', 'ONSHORE_OVERTIME_HOURS', 'QUAYSIDE_HOURS',
        'MEAL_ALLOWANCE_DAYS', 'TRANSPORT_ALLOWANCE_DAYS', 'OFFSHORE_NIGHT_HOURS',
        'QUAYSIDE_NIGHT_HOURS', 'OFFSHORE_HOLIDAY_HOURS', 'QUAYSIDE_HOLIDAY_HOURS']
      .map<ColumnsType<EmployeePreview>[number]>((code) => ({
        title: categoryLabel(code), width: 130, align: 'right',
        render: (_, r) => fmtQty(r.quantities[code]),
      })),
    {
      title: 'Gross', width: 120, align: 'right', fixed: 'right',
      render: (_, r) => <strong>{money(r.result.gross)}</strong>,
    },
    {
      title: 'Net', width: 120, align: 'right', fixed: 'right',
      render: (_, r) => <strong>{money(r.result.netPay)}</strong>,
    },
    {
      title: '', width: 60, fixed: 'right',
      render: (_, r) => (r.blockers.length > 0
        ? <Tag color="orange">{r.blockers.length}</Tag>
        : null),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Row gutter={[16, 16]} align="middle">
          <Col flex="auto">
            <Typography.Title level={4} style={{ margin: 0 }}>
              Payroll Inputs — Time &amp; Attendance
            </Typography.Title>
            <Typography.Text type="secondary">
              Approved timesheet quantities, priced. Nothing on this page creates a
              payroll run or a payslip.
            </Typography.Text>
          </Col>
          <Col>
            <DatePicker
              picker="month"
              allowClear={false}
              value={period}
              onChange={(v: Dayjs | null) => {
                if (v) setParams({ period: v.format('YYYY-MM') }, { replace: true })
              }}
            />
          </Col>
        </Row>
      </Card>

      {error && <Alert type="error" showIcon message={error} />}

      {data && data.notPriceable > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${data.notPriceable} timesheet(s) are not approved and are excluded`}
          description="Payroll consumes approved and locked months only. Chase them through Timesheet Control before running payroll."
        />
      )}
      {data && data.withBlockers > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${data.withBlockers} employee(s) have unresolved pricing questions`}
          description="Open a row to see them. A missing rule pays nothing rather than guessing, so these figures are understated until resolved."
        />
      )}

      <Row gutter={16}>
        <Col xs={12} md={6}>
          <Card><Statistic title="Norm hours" value={data?.normHours ?? 0} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="Priced" value={data?.priceable ?? 0} /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="Total gross" value={data?.totalGross ?? 0} precision={2} suffix="AZN" /></Card>
        </Col>
        <Col xs={12} md={6}>
          <Card><Statistic title="Total net" value={data?.totalNet ?? 0} precision={2} suffix="AZN" /></Card>
        </Col>
      </Row>

      <Card>
        {!loading && (data?.employees.length ?? 0) === 0 ? (
          <Empty description="No approved timesheets to price in this period" />
        ) : (
          <Table
            rowKey="employeeId"
            size="small"
            loading={loading}
            dataSource={data?.employees ?? []}
            columns={columns}
            pagination={false}
            scroll={{ x: 1800 }}
            onRow={(r) => ({ onClick: () => setOpen(r), style: { cursor: 'pointer' } })}
          />
        )}
      </Card>

      <Drawer
        open={open != null}
        onClose={() => setOpen(null)}
        width={620}
        title={open ? `${open.employeeName} — ${period.format('MMMM YYYY')}` : ''}
      >
        {open && <EmployeeDetail preview={open} />}
      </Drawer>
    </Space>
  )
}

function EmployeeDetail({ preview }: { preview: EmployeePreview }) {
  const r = preview.result
  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {preview.blockers.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message="Unresolved before this figure can be trusted"
          description={
            <ul style={{ margin: '6px 0 0 16px', padding: 0 }}>
              {preview.blockers.map((b, i) => <li key={i}>{b}</li>)}
            </ul>
          }
        />
      )}

      <Descriptions column={2} size="small" bordered>
        <Descriptions.Item label="Position" span={2}>{preview.positionTitle ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="Base salary">{money(preview.baseSalary)}</Descriptions.Item>
        <Descriptions.Item label="Norm hours">{preview.normHours}</Descriptions.Item>
        <Descriptions.Item label="Hourly rate">{money(r.hourlyRate)}</Descriptions.Item>
        <Descriptions.Item label="Overtime rate">{money(r.overtimeRate)}</Descriptions.Item>
      </Descriptions>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Base salary is not itself paid — it is only the source of the hourly rate.
        Gross is the sum of the earning lines below.
      </Typography.Text>

      <Card size="small" title="Earnings">
        <Table
          rowKey={(l) => l.categoryCode}
          size="small"
          pagination={false}
          dataSource={r.earnings}
          columns={[
            { title: 'Line', render: (_, l) => categoryLabel(l.categoryCode) },
            { title: 'Qty', dataIndex: 'quantity', width: 80, align: 'right',
              render: fmtQty },
            { title: 'Rate', dataIndex: 'rate', width: 100, align: 'right',
              render: (v: number | null) => (v == null ? '—' : money(v)) },
            { title: 'Amount', dataIndex: 'amount', width: 110, align: 'right',
              render: (v: number) => <strong>{money(v)}</strong> },
          ]}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3}><strong>Gross</strong></Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right"><strong>{money(r.gross)}</strong></Table.Summary.Cell>
            </Table.Summary.Row>
          )}
        />
      </Card>

      <Card size="small" title="Deductions">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Income tax">{money(r.incomeTax)}</Descriptions.Item>
          <Descriptions.Item label="SPF">{money(r.spf)}</Descriptions.Item>
          <Descriptions.Item label="Unemployment fund">{money(r.unemploymentFund)}</Descriptions.Item>
          <Descriptions.Item label="Compulsory insurance">{money(r.compulsoryInsurance)}</Descriptions.Item>
          <Descriptions.Item label="Life insurance">{money(r.lifeInsurance)}</Descriptions.Item>
          <Descriptions.Item label="Azercell">{money(r.azercell)}</Descriptions.Item>
          <Descriptions.Item label="Advance">{money(r.advance)}</Descriptions.Item>
          <Descriptions.Item label={<strong>Total deductions</strong>}>
            <strong>{money(r.totalDeductions)}</strong>
          </Descriptions.Item>
          <Descriptions.Item label={<strong>Net pay</strong>}>
            <strong style={{ fontSize: 16 }}>{money(r.netPay)} AZN</strong>
          </Descriptions.Item>
        </Descriptions>
        {r.contributionExemptAmount > 0 && (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {money(r.contributionExemptAmount)} AZN of allowance is paid in full but
            excluded from every contribution base.
          </Typography.Text>
        )}
      </Card>
    </Space>
  )
}

const money = (v?: number | null) =>
  v == null ? '—' : v.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const fmtQty = (v?: number | null) => (v == null || v === 0 ? '—' : String(v))

function errorOf(err: unknown, fallback: string): string {
  const res = (err as { response?: { data?: { message?: string } } })?.response
  return res?.data?.message ?? fallback
}

export default PayrollTimeInputsPage
