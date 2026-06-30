import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Row,
  Col,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  payrollApi,
  type PayrollRun,
  type PayrollVarianceResponse,
} from '../api/payroll'

const FLAG_COLOR: Record<string, string> = {
  NEW_EMPLOYEE: 'blue',
  EMPLOYEE_ABSENT: 'default',
  SALARY_CHANGE: 'orange',
  BONUS_ADDED: 'cyan',
  DEDUCTION_ADDED: 'red',
  COMPONENT_CHANGE: 'purple',
}

export function VarianceReportPage() {
  const { message } = AntdApp.useApp()

  const [runs, setRuns] = useState<PayrollRun[]>([])
  const [priorRunId, setPriorRunId] = useState<string | undefined>()
  const [currentRunId, setCurrentRunId] = useState<string | undefined>()
  const [variance, setVariance] = useState<PayrollVarianceResponse | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    payrollApi.runs().then((r) => {
      const paid = r.filter((run) => run.status === 'PAID')
      setRuns(paid)
      if (paid.length >= 2) {
        setCurrentRunId(paid[0].id)
        setPriorRunId(paid[1].id)
      } else if (paid.length === 1) {
        setCurrentRunId(paid[0].id)
      }
    })
  }, [])

  const loadVariance = () => {
    if (!currentRunId || !priorRunId) {
      message.warning('Please select both runs')
      return
    }
    setLoading(true)
    payrollApi
      .varianceReport(currentRunId, priorRunId)
      .then(setVariance)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load variance'),
      )
      .finally(() => setLoading(false))
  }

  const paidRuns = runs.filter((r) => r.status === 'PAID')

  const columns: ColumnsType<PayrollVarianceResponse['employees'][0]> = [
    {
      title: 'Employee',
      render: (_, r) => `${r.employeeNo} - ${r.name}`,
    },
    {
      title: 'Prior Gross',
      dataIndex: 'priorGross',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Current Gross',
      dataIndex: 'currentGross',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Delta',
      dataIndex: 'grossDelta',
      align: 'right',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'green' : 'red' }}>
          {v >= 0 ? '+' : ''}
          {v.toFixed(2)} AZN
        </span>
      ),
    },
    {
      title: 'Delta %',
      dataIndex: 'grossDeltaPct',
      align: 'right',
      render: (v: number) => (
        <span style={{ color: v >= 0 ? 'green' : 'red' }}>
          {v >= 0 ? '+' : ''}
          {v.toFixed(1)}%
        </span>
      ),
    },
    {
      title: 'Flags',
      dataIndex: 'flags',
      render: (flags: string[]) =>
        flags.map((f) => (
          <Tag key={f} color={FLAG_COLOR[f] ?? 'default'} style={{ marginBottom: 4 }}>
            {f.replace(/_/g, ' ')}
          </Tag>
        )),
    },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Payroll Variance Report</Typography.Title>}>
      <Space style={{ marginBottom: 16 }}>
        <Typography.Text>Prior Run:</Typography.Text>
        <Select
          style={{ width: 220 }}
          value={priorRunId}
          onChange={setPriorRunId}
          options={paidRuns.map((r) => ({
            value: r.id,
            label: `${r.runNo} (${r.periodYear}/${String(r.periodMonth).padStart(2, '0')})`,
          }))}
        />
        <Typography.Text>Current Run:</Typography.Text>
        <Select
          style={{ width: 220 }}
          value={currentRunId}
          onChange={setCurrentRunId}
          options={paidRuns.map((r) => ({
            value: r.id,
            label: `${r.runNo} (${r.periodYear}/${String(r.periodMonth).padStart(2, '0')})`,
          }))}
        />
        <Button type="primary" onClick={loadVariance} disabled={!currentRunId || !priorRunId}>
          Generate Report
        </Button>
      </Space>

      {variance && (
        <>
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={4}>
              <Card>
                <Statistic
                  title="Total Gross Change"
                  value={variance.summary.totalGrossChange}
                  precision={2}
                  suffix="AZN"
                  valueStyle={{
                    color: variance.summary.totalGrossChange >= 0 ? 'green' : 'red',
                  }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic
                  title="Change %"
                  value={variance.summary.pctChange}
                  precision={1}
                  suffix="%"
                  valueStyle={{
                    color: variance.summary.pctChange >= 0 ? 'green' : 'red',
                  }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic
                  title="High Variance Count"
                  value={variance.summary.highVarianceCount}
                  valueStyle={{ color: variance.summary.highVarianceCount > 0 ? 'orange' : '' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic
                  title="New Employees"
                  value={variance.summary.newEmployeeCount}
                  valueStyle={{ color: 'blue' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card>
                <Statistic title="Absent Employees" value={variance.summary.absentEmployeeCount} />
              </Card>
            </Col>
          </Row>

          <Table
            rowKey="employeeId"
            columns={columns}
            dataSource={variance.employees}
            loading={loading}
            pagination={false}
            summary={(data) => {
              const totalPrior = data.reduce((sum, r) => sum + r.priorGross, 0)
              const totalCurrent = data.reduce((sum, r) => sum + r.currentGross, 0)
              const totalDelta = totalCurrent - totalPrior
              return (
                <Table.Summary.Row style={{ fontWeight: 'bold' }}>
                  <Table.Summary.Cell index={0}>Total</Table.Summary.Cell>
                  <Table.Summary.Cell index={1} align="right">
                    {totalPrior.toFixed(2)} AZN
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2} align="right">
                    {totalCurrent.toFixed(2)} AZN
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} align="right">
                    <span style={{ color: totalDelta >= 0 ? 'green' : 'red' }}>
                      {totalDelta >= 0 ? '+' : ''}
                      {totalDelta.toFixed(2)} AZN
                    </span>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4} />
                  <Table.Summary.Cell index={5} />
                </Table.Summary.Row>
              )
            }}
          />
        </>
      )}
    </Card>
  )
}
