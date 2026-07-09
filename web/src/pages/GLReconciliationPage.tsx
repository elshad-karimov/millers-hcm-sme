import { useState } from 'react'
import { Card, DatePicker, Space, Table, Tag, Typography, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { type Dayjs } from 'dayjs'
import { payrollApi, type ReconciliationRow } from '../api/payroll'

export function GLReconciliationPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [report, setReport] = useState<{
    year: number
    month: number
    rows: ReconciliationRow[]
  } | null>(null)
  const [selectedDate, setSelectedDate] = useState<Dayjs>(dayjs())

  const load = async (date: Dayjs) => {
    setLoading(true)
    try {
      const data = await payrollApi.glReconciliation(date.year(), date.month() + 1)
      setReport(data)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load reconciliation',
      )
    } finally {
      setLoading(false)
    }
  }

  const handleDateChange = (date: Dayjs | null) => {
    if (!date) return
    setSelectedDate(date)
    load(date)
  }

  const columns: ColumnsType<ReconciliationRow> = [
    {
      title: 'Component',
      dataIndex: 'description',
      width: 200,
    },
    {
      title: 'Account Type',
      dataIndex: 'accountType',
      width: 120,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    {
      title: 'Payroll Total',
      dataIndex: 'payrollTotal',
      align: 'right',
      width: 150,
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'GL Total',
      dataIndex: 'glTotal',
      align: 'right',
      width: 150,
      render: (v: number) => v.toFixed(2),
    },
    {
      title: 'Difference',
      dataIndex: 'difference',
      align: 'right',
      width: 150,
      render: (v: number) => (
        <span style={{ color: v !== 0 ? '#ff4d4f' : '#52c41a' }}>
          {v.toFixed(2)}
        </span>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (v: string) => (
        <Tag color={v === 'MATCHED' ? 'green' : 'red'}>
          {v === 'MATCHED' ? 'Matched' : 'Discrepancy'}
        </Tag>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <span>GL Reconciliation</span>
            {report && (
              <Tag color="geekblue">
                {report.year}/{String(report.month).padStart(2, '0')}
              </Tag>
            )}
          </Space>
        }
        extra={
          <Space>
            <Typography.Text type="secondary">Period:</Typography.Text>
            <DatePicker
              picker="month"
              value={selectedDate}
              onChange={handleDateChange}
              format="YYYY-MM"
              allowClear={false}
            />
          </Space>
        }
      >
        <Table
          rowKey={(r) => r.componentKind}
          columns={columns}
          dataSource={report?.rows ?? []}
          loading={loading}
          pagination={false}
          size="small"
          summary={(data) => {
            if (data.length === 0) return null
            const totalPayroll = data.reduce((sum, r) => sum + r.payrollTotal, 0)
            const totalGL = data.reduce((sum, r) => sum + r.glTotal, 0)
            const totalDiff = totalPayroll - totalGL
            return (
              <Table.Summary fixed>
                <Table.Summary.Row style={{ fontWeight: 'bold' }}>
                  <Table.Summary.Cell index={0}>Total</Table.Summary.Cell>
                  <Table.Summary.Cell index={1} />
                  <Table.Summary.Cell index={2} align="right">
                    {totalPayroll.toFixed(2)}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} align="right">
                    {totalGL.toFixed(2)}
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4} align="right">
                    <span style={{ color: totalDiff !== 0 ? '#ff4d4f' : '#52c41a' }}>
                      {totalDiff.toFixed(2)}
                    </span>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={5}>
                    <Tag color={totalDiff === 0 ? 'green' : 'red'}>
                      {totalDiff === 0 ? 'Balanced' : 'Out of balance'}
                    </Tag>
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              </Table.Summary>
            )
          }}
        />
      </Card>
    </Space>
  )
}
