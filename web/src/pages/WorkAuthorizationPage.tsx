import { useEffect, useState } from 'react'
import { Card, Select, Space, Table, Tag, Typography, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { complianceApi, type ExpiringWorkAuth } from '../api/compliance'

export function WorkAuthorizationPage() {
  const { message } = AntdApp.useApp()
  const [data, setData] = useState<ExpiringWorkAuth[]>([])
  const [loading, setLoading] = useState(true)
  const [days, setDays] = useState(90)

  const load = async () => {
    setLoading(true)
    try {
      const result = await complianceApi.expiringWorkAuth(days)
      setData(result)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed to load work authorization data',
      )
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [days])

  const columns: ColumnsType<ExpiringWorkAuth> = [
    { title: 'Employee No', dataIndex: 'employeeNo', width: 130 },
    {
      title: 'Name',
      render: (_, r) => `${r.lastName} ${r.firstName}`,
    },
    {
      title: 'Expiry Date',
      dataIndex: 'expiryDate',
      width: 130,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD'),
    },
    {
      title: 'Days Until Expiry',
      dataIndex: 'daysUntilExpiry',
      width: 160,
      render: (v: number) => (
        <Tag color={v < 0 ? 'red' : v < 30 ? 'orange' : v < 60 ? 'gold' : 'green'}>
          {v < 0 ? `${Math.abs(v)} days expired` : `${v} days`}
        </Tag>
      ),
    },
    {
      title: '',
      width: 150,
      render: (_, r) => (
        <Typography.Link href={`/employees/${r.id}`} target="_blank">
          View Profile
        </Typography.Link>
      ),
    },
  ]

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <span>Work Authorization Expiry</span>
            <Select value={days} onChange={setDays} size="small" style={{ width: 120 }}>
              <Select.Option value={30}>30 days</Select.Option>
              <Select.Option value={60}>60 days</Select.Option>
              <Select.Option value={90}>90 days</Select.Option>
              <Select.Option value={180}>180 days</Select.Option>
            </Select>
          </Space>
        }
      >
        {data.length === 0 && !loading ? (
          <Typography.Text type="secondary">
            No work authorization documents expiring within {days} days.
          </Typography.Text>
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={data}
            loading={loading}
            pagination={{ pageSize: 20 }}
            size="small"
          />
        )}
      </Card>
      <Card>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          Note: Work authorization details are edited on the employee profile page.
        </Typography.Paragraph>
      </Card>
    </Space>
  )
}
