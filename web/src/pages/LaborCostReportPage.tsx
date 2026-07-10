// M486 — Labor cost reports (Finance/HR confidential)

import { useEffect, useState } from 'react'
import {
  App as AntdApp,
  Card,
  Col,
  InputNumber,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  laborCostReportApi,
  type LaborCostRow,
} from '../api/laborRates'

const { Title, Text } = Typography

function ByProjectTab({ year, month }: { year: number; month: number }) {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<LaborCostRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    laborCostReportApi.byProject(year, month)
      .then(setData)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load report'))
      .finally(() => setLoading(false))
  }, [year, month, message])

  const totalCost = data.reduce((sum, row) => sum + Number(row.totalCost || 0), 0)

  const columns: ColumnsType<LaborCostRow> = [
    { title: 'Project', dataIndex: 'projectCode', key: 'projectCode' },
    { title: 'Project Name', dataIndex: 'projectName', key: 'projectName' },
    {
      title: 'Hours',
      dataIndex: 'totalHours',
      key: 'totalHours',
      align: 'right',
      render: h => Number(h || 0).toFixed(2),
    },
    {
      title: 'Cost',
      dataIndex: 'totalCost',
      key: 'totalCost',
      align: 'right',
      render: c => Number(c || 0).toFixed(2),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Statistic title="Total Labor Cost" value={totalCost.toFixed(2)} precision={2} />
      </Card>
      <Table
        dataSource={data}
        columns={columns}
        rowKey={(_rec, idx) => idx?.toString() ?? ''}
        loading={loading}
        pagination={false}
      />
    </Space>
  )
}

function ByDepartmentTab({ year, month }: { year: number; month: number }) {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<LaborCostRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    laborCostReportApi.byDepartment(year, month)
      .then(setData)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load report'))
      .finally(() => setLoading(false))
  }, [year, month, message])

  const totalCost = data.reduce((sum, row) => sum + Number(row.totalCost || 0), 0)

  const columns: ColumnsType<LaborCostRow> = [
    { title: 'Department', dataIndex: 'departmentName', key: 'departmentName' },
    {
      title: 'Headcount',
      dataIndex: 'headcount',
      key: 'headcount',
      align: 'right',
    },
    {
      title: 'Hours',
      dataIndex: 'totalHours',
      key: 'totalHours',
      align: 'right',
      render: h => Number(h || 0).toFixed(2),
    },
    {
      title: 'Cost',
      dataIndex: 'totalCost',
      key: 'totalCost',
      align: 'right',
      render: c => Number(c || 0).toFixed(2),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Statistic title="Total Labor Cost" value={totalCost.toFixed(2)} precision={2} />
      </Card>
      <Table
        dataSource={data}
        columns={columns}
        rowKey={(_rec, idx) => idx?.toString() ?? ''}
        loading={loading}
        pagination={false}
      />
    </Space>
  )
}

function MonthlySummaryTab({ year, month }: { year: number; month: number }) {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<LaborCostRow[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    laborCostReportApi.monthlySummary(year, month)
      .then(setData)
      .catch(e => message.error(e?.response?.data?.message ?? 'Failed to load report'))
      .finally(() => setLoading(false))
  }, [year, month, message])

  const totalCost = data.reduce((sum, row) => sum + Number(row.totalCost || 0), 0)

  const columns: ColumnsType<LaborCostRow> = [
    { title: 'Category', dataIndex: 'category', key: 'category' },
    {
      title: 'Hours',
      dataIndex: 'totalHours',
      key: 'totalHours',
      align: 'right',
      render: h => Number(h || 0).toFixed(2),
    },
    {
      title: 'Cost',
      dataIndex: 'totalCost',
      key: 'totalCost',
      align: 'right',
      render: c => Number(c || 0).toFixed(2),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card>
        <Statistic title="Total Labor Cost" value={totalCost.toFixed(2)} precision={2} />
      </Card>
      <Table
        dataSource={data}
        columns={columns}
        rowKey={(_rec, idx) => idx?.toString() ?? ''}
        loading={loading}
        pagination={false}
      />
    </Space>
  )
}

export function LaborCostReportPage() {
  const now = dayjs()
  const [year, setYear] = useState(now.year())
  const [month, setMonth] = useState(now.month() + 1)

  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Labor Cost Report</Title>
      <Text type="secondary">
        Monthly labor cost analysis by project, department, and summary.
      </Text>

      <Card style={{ marginTop: 24 }}>
        <Row gutter={16}>
          <Col>
            <Space>
              <Text>Year:</Text>
              <InputNumber value={year} onChange={v => setYear(v || now.year())} style={{ width: 100 }} />
            </Space>
          </Col>
          <Col>
            <Space>
              <Text>Month:</Text>
              <Select value={month} onChange={setMonth} style={{ width: 150 }}>
                {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
                  <Select.Option key={m} value={m}>
                    {dayjs().month(m - 1).format('MMMM')}
                  </Select.Option>
                ))}
              </Select>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card style={{ marginTop: 16 }}>
        <Tabs
          items={[
            { key: 'project', label: 'By Project', children: <ByProjectTab year={year} month={month} /> },
            { key: 'department', label: 'By Department', children: <ByDepartmentTab year={year} month={month} /> },
            { key: 'summary', label: 'Monthly Summary', children: <MonthlySummaryTab year={year} month={month} /> },
          ]}
        />
      </Card>
    </div>
  )
}
