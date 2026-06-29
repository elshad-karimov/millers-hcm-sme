import { useEffect, useState } from 'react'
import { Card, Col, InputNumber, Row, Statistic, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { leaveApi } from '../api/leave'

interface LiabilityRow {
  employeeId: string
  employeeNo: string
  employeeName: string
  departmentName: string
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  year: number
  remainingDays: number
  monthlyBaseSalary: number
  dailyRate: number
  liabilityAmount: number
}

interface LiabilityReport {
  year: number
  workingDaysPerMonth: number
  totalLiability: number
  rows: LiabilityRow[]
}

const fmt = (v: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v)

export function LeaveLiabilityPage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [wdpm, setWdpm] = useState(22)
  const [report, setReport] = useState<LiabilityReport | null>(null)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const data = await leaveApi.liabilityReport(year, wdpm)
      setReport(data as LiabilityReport)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [year, wdpm])

  const columns: ColumnsType<LiabilityRow> = [
    { title: 'Emp No', dataIndex: 'employeeNo', width: 90 },
    { title: 'Name', dataIndex: 'employeeName', ellipsis: true },
    { title: 'Department', dataIndex: 'departmentName', ellipsis: true },
    { title: 'Leave Type', dataIndex: 'leaveTypeName', width: 130 },
    { title: 'Remaining Days', dataIndex: 'remainingDays', width: 130, align: 'right',
      render: (v) => Number(v).toFixed(1) },
    { title: 'Monthly Salary', dataIndex: 'monthlyBaseSalary', width: 130, align: 'right',
      render: (v) => fmt(v) },
    { title: 'Daily Rate', dataIndex: 'dailyRate', width: 110, align: 'right',
      render: (v) => fmt(v) },
    { title: 'Liability (AZN)', dataIndex: 'liabilityAmount', width: 130, align: 'right',
      render: (v) => <strong>{fmt(v)}</strong> },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Typography.Title level={4} style={{ margin: 0 }}>Leave Liability Report</Typography.Title>
        </Col>
        <Col>
          <Row gutter={8} align="middle">
            <Col>Year:</Col>
            <Col>
              <InputNumber min={2020} max={2100} value={year}
                onChange={(v) => v && setYear(v)} style={{ width: 90 }} />
            </Col>
            <Col>Working days/month:</Col>
            <Col>
              <InputNumber min={1} max={31} value={wdpm}
                onChange={(v) => v && setWdpm(v)} style={{ width: 70 }} />
            </Col>
          </Row>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic title="Total Liability (AZN)"
              value={fmt(report?.totalLiability ?? 0)}
              valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic title="Employees with liability"
              value={new Set(report?.rows.map((r) => r.employeeId) ?? []).size} />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic title="Leave types covered"
              value={new Set(report?.rows.map((r) => r.leaveTypeCode) ?? []).size} />
          </Card>
        </Col>
        <Col span={6}>
          <Card loading={loading}>
            <Statistic title="Working days / month" value={report?.workingDaysPerMonth ?? wdpm} />
          </Card>
        </Col>
      </Row>

      <Card loading={loading}>
        <Table
          rowKey={(r) => `${r.employeeId}-${r.leaveTypeId}`}
          size="small"
          dataSource={report?.rows ?? []}
          columns={columns}
          pagination={{ pageSize: 50, showSizeChanger: true }}
          summary={(pageData) => {
            const total = pageData.reduce((s, r) => s + Number(r.liabilityAmount), 0)
            return (
              <Table.Summary fixed>
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={7} align="right">
                    <strong>Page subtotal:</strong>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={7} align="right">
                    <strong>{fmt(total)}</strong>
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              </Table.Summary>
            )
          }}
        />
      </Card>
    </div>
  )
}
