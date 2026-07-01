import { useState } from 'react'
import {
  Card,
  Col,
  Descriptions,
  Row,
  Select,
  Statistic,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  Empty,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  compensationApi,
  type CompensationProfileDto,
  type SalaryHistoryItem,
} from '../api/compensation'
import { employeesApi, type Employee } from '../api/employees'

const { Title, Text } = Typography

const RANGE_POSITION_COLOR: Record<string, string> = {
  BELOW_MIN: 'red',
  LOW: 'gold',
  MID: 'green',
  HIGH: 'gold',
  ABOVE_MAX: 'red',
}

export function CompensationProfilePage() {
  const { message } = AntdApp.useApp()

  const [employees, setEmployees] = useState<Employee[]>([])
  const [loadingEmployees, setLoadingEmployees] = useState(false)
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<string | undefined>()
  const [profile, setProfile] = useState<CompensationProfileDto | null>(null)
  const [loadingProfile, setLoadingProfile] = useState(false)
  const [accessDenied, setAccessDenied] = useState(false)

  const handleSearchEmployees = (search: string) => {
    if (search.length < 2) {
      setEmployees([])
      return
    }
    setLoadingEmployees(true)
    employeesApi
      .list({ search, size: 50 })
      .then((resp) => setEmployees(resp.content))
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to search employees'),
      )
      .finally(() => setLoadingEmployees(false))
  }

  const handleSelectEmployee = (empId: string) => {
    setSelectedEmployeeId(empId)
    setProfile(null)
    setAccessDenied(false)
    setLoadingProfile(true)

    compensationApi
      .getProfile(empId)
      .then(setProfile)
      .catch((err) => {
        if (err?.response?.status === 403) {
          setAccessDenied(true)
        } else {
          message.error(err?.response?.data?.message ?? 'Failed to load profile')
        }
      })
      .finally(() => setLoadingProfile(false))
  }

  const historyColumns: ColumnsType<SalaryHistoryItem> = [
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      width: 150,
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      width: 150,
      render: (v) => v ?? <span style={{ color: '#999' }}>—</span>,
    },
    {
      title: 'Salary',
      dataIndex: 'monthlyBaseSalary',
      key: 'monthlyBaseSalary',
      align: 'right',
      render: (v, rec) => `${v.toLocaleString()} ${rec.currency}`,
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      render: (v) => v ?? <span style={{ color: '#999' }}>—</span>,
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Title level={2}>Compensation Profile</Title>

      <Card style={{ marginBottom: 24 }}>
        <Text strong>Select Employee:</Text>
        <Select
          showSearch
          placeholder="Type employee name or number..."
          style={{ width: '100%', marginTop: 8 }}
          filterOption={false}
          onSearch={handleSearchEmployees}
          onChange={handleSelectEmployee}
          value={selectedEmployeeId}
          loading={loadingEmployees}
          notFoundContent={loadingEmployees ? 'Loading...' : 'Type to search'}
          options={employees.map((e) => ({
            value: e.id,
            label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
          }))}
        />
      </Card>

      {accessDenied && (
        <Card>
          <Empty description="Access denied. You do not have permission to view this employee's compensation profile." />
        </Card>
      )}

      {!accessDenied && profile && (
        <>
          {/* Current Salary Header */}
          {profile.currentSalary && (
            <Card style={{ marginBottom: 16 }} loading={loadingProfile}>
              <Descriptions title="Current Salary" column={2} bordered>
                <Descriptions.Item label="Monthly Base Salary">
                  {profile.currentSalary.monthlyBaseSalary.toLocaleString()}{' '}
                  {profile.currentSalary.currency}
                </Descriptions.Item>
                <Descriptions.Item label="Effective From">
                  {profile.currentSalary.effectiveFrom}
                </Descriptions.Item>
                {profile.grade && (
                  <>
                    <Descriptions.Item label="Grade">
                      {profile.grade.code} — {profile.grade.name}
                    </Descriptions.Item>
                    <Descriptions.Item label="Grade Range">
                      {profile.grade.minSalary?.toLocaleString() ?? '—'} →{' '}
                      {profile.grade.maxSalary?.toLocaleString() ?? '—'}
                    </Descriptions.Item>
                  </>
                )}
                {profile.band && (
                  <>
                    <Descriptions.Item label="Pay Band">
                      {profile.band.code} — {profile.band.name}
                    </Descriptions.Item>
                    <Descriptions.Item label="Band Range">
                      Min: {profile.band.minSalary.toLocaleString()} | Mid:{' '}
                      {profile.band.midSalary.toLocaleString()} | Max:{' '}
                      {profile.band.maxSalary.toLocaleString()}
                    </Descriptions.Item>
                  </>
                )}
              </Descriptions>
            </Card>
          )}

          {/* Compa-Ratio and Range Penetration */}
          <Row gutter={16} style={{ marginBottom: 16 }}>
            <Col span={12}>
              <Card loading={loadingProfile}>
                <Statistic
                  title="Compa-Ratio"
                  value={profile.compaRatio?.toFixed(2) ?? '—'}
                  suffix={profile.compaRatio ? '%' : ''}
                />
                {profile.rangePositionLabel && (
                  <Tag
                    color={RANGE_POSITION_COLOR[profile.rangePositionLabel] || 'default'}
                    style={{ marginTop: 8 }}
                  >
                    {profile.rangePositionLabel.replace('_', ' ')}
                  </Tag>
                )}
              </Card>
            </Col>
            <Col span={12}>
              <Card loading={loadingProfile}>
                <Statistic
                  title="Range Penetration"
                  value={profile.rangePenetration?.toFixed(2) ?? '—'}
                  suffix={profile.rangePenetration ? '%' : ''}
                />
              </Card>
            </Col>
          </Row>

          {/* Salary History */}
          <Card title="Salary History" loading={loadingProfile}>
            {profile.salaryHistory.length === 0 ? (
              <Empty description="No salary history" />
            ) : (
              <Table
                dataSource={profile.salaryHistory}
                columns={historyColumns}
                rowKey={(rec, idx) => `${rec.effectiveFrom}-${idx}`}
                pagination={false}
                size="small"
              />
            )}
          </Card>
        </>
      )}

      {!profile && !accessDenied && !loadingProfile && selectedEmployeeId && (
        <Card>
          <Empty description="No compensation profile found for this employee." />
        </Card>
      )}
    </div>
  )
}
