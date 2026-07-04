import { useEffect, useState } from 'react'
import { Card, Table, Typography, Tabs, Tag, App as AntdApp } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  skillInventoryApi,
  type ByDepartmentRow,
  type CriticalSkillRow,
  type CertificationRow,
} from '../api/skillInventory'

const { Title } = Typography

export function SkillInventoryDashboardPage() {
  const { message } = AntdApp.useApp()

  const [byDept, setByDept] = useState<ByDepartmentRow[]>([])
  const [critical, setCritical] = useState<CriticalSkillRow[]>([])
  const [certs, setCerts] = useState<CertificationRow[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    loadAll()
  }, [])

  const loadAll = () => {
    setLoading(true)
    Promise.all([
      skillInventoryApi.byDepartment(),
      skillInventoryApi.critical(),
      skillInventoryApi.certifications(),
    ])
      .then(([d, c, ct]) => {
        setByDept(d.data)
        setCritical(c.data)
        setCerts(ct.data)
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load reports'),
      )
      .finally(() => setLoading(false))
  }

  const deptColumns: ColumnsType<ByDepartmentRow> = [
    { title: 'Department', dataIndex: 'department', width: 200 },
    { title: 'Competency', dataIndex: 'competencyName' },
    { title: 'Employees', dataIndex: 'employeeCount', width: 120 },
    {
      title: 'Avg Level',
      dataIndex: 'avgLevel',
      width: 120,
      render: (val: number) => val.toFixed(1),
    },
  ]

  const criticalColumns: ColumnsType<CriticalSkillRow> = [
    { title: 'Competency', dataIndex: 'competencyName' },
    {
      title: 'Required Level',
      dataIndex: 'requiredLevel',
      width: 140,
      render: (lvl: number) => <Tag color="blue">{lvl}</Tag>,
    },
    {
      title: 'Covered Employees',
      dataIndex: 'coveredEmployees',
      width: 160,
      render: (count: number) => (
        <Tag color={count > 0 ? 'green' : 'red'}>{count}</Tag>
      ),
    },
  ]

  const certColumns: ColumnsType<CertificationRow> = [
    { title: 'Certification', dataIndex: 'certificationName' },
    { title: 'Total', dataIndex: 'totalCount', width: 100 },
    {
      title: 'Expired',
      dataIndex: 'expiredCount',
      width: 100,
      render: (count: number) => (
        count > 0 ? <Tag color="red">{count}</Tag> : count
      ),
    },
    {
      title: 'Expiring (90d)',
      dataIndex: 'expiringSoonCount',
      width: 140,
      render: (count: number) => (
        count > 0 ? <Tag color="orange">{count}</Tag> : count
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Skill Inventory Dashboard</Title>

      <Tabs
        items={[
          {
            key: 'dept',
            label: 'By Department',
            children: (
              <Card>
                <Table
                  dataSource={byDept}
                  columns={deptColumns}
                  rowKey={(r) => `${r.department}-${r.competencyName}`}
                  loading={loading}
                  pagination={{ pageSize: 50 }}
                />
              </Card>
            ),
          },
          {
            key: 'critical',
            label: `Critical Skills (${critical.filter((r) => r.coveredEmployees === 0).length} gaps)`,
            children: (
              <Card>
                <Table
                  dataSource={critical}
                  columns={criticalColumns}
                  rowKey="competencyName"
                  loading={loading}
                  pagination={{ pageSize: 50 }}
                />
              </Card>
            ),
          },
          {
            key: 'certs',
            label: `Certifications (${certs.reduce((sum, r) => sum + r.expiredCount + r.expiringSoonCount, 0)} alerts)`,
            children: (
              <Card>
                <Table
                  dataSource={certs}
                  columns={certColumns}
                  rowKey="certificationName"
                  loading={loading}
                  pagination={{ pageSize: 50 }}
                />
              </Card>
            ),
          },
        ]}
      />
    </div>
  )
}
