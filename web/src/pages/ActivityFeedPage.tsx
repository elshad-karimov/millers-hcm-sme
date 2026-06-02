// M80 — Live activity feed (audit-driven).
//
// Reads the global audit log and renders it as a timeline. Filters by
// module / entity / actor; refresh button pulls the latest. The endpoint
// is HR_ADMIN/SYSTEM_ADMIN/AUDITOR only — no scope-restricted callers.

import { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { empMgmtApi, type ActivityRow } from '../api/empMgmt'

const MODULE_COLOR: Record<string, string> = {
  CORE_HR: 'blue',
  PAYROLL: 'green',
  LEAVE: 'orange',
  STAFFING: 'cyan',
  LIFECYCLE: 'purple',
  WORKFLOW: 'magenta',
  LETTERS: 'gold',
  REPORTING: 'geekblue',
  ATTACHMENT: 'lime',
}

export function ActivityFeedPage() {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<ActivityRow[]>([])
  const [loading, setLoading] = useState(true)
  const [moduleFilter, setModuleFilter] = useState<string | undefined>()
  const [entityFilter, setEntityFilter] = useState<string | undefined>()
  const [actorFilter, setActorFilter] = useState<string>('')

  const load = useCallback(() => {
    setLoading(true)
    empMgmtApi
      .activity({
        module: moduleFilter,
        entityName: entityFilter,
        actor: actorFilter || undefined,
        limit: 200,
      })
      .then((r) => setRows(r.rows))
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load activity feed'),
      )
      .finally(() => setLoading(false))
  }, [moduleFilter, entityFilter, actorFilter, message])

  useEffect(load, [load])

  const columns: ColumnsType<ActivityRow> = [
    {
      title: 'When',
      dataIndex: 'at',
      width: 180,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: 'Module',
      dataIndex: 'module',
      width: 120,
      render: (v: string) => <Tag color={MODULE_COLOR[v] ?? 'default'}>{v}</Tag>,
    },
    {
      title: 'Action',
      dataIndex: 'action',
      width: 160,
      render: (v: string) => <Tag>{v}</Tag>,
    },
    { title: 'Summary', dataIndex: 'summary' },
    { title: 'Actor', dataIndex: 'actor', width: 160 },
  ]

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Typography.Title level={4} style={{ margin: 0 }}>
            Activity feed
          </Typography.Title>
        }
        extra={
          <Space>
            <Select
              placeholder="Module"
              allowClear
              style={{ width: 140 }}
              value={moduleFilter}
              onChange={setModuleFilter}
              options={[
                'CORE_HR', 'PAYROLL', 'LEAVE', 'STAFFING', 'LIFECYCLE',
                'WORKFLOW', 'LETTERS', 'REPORTING', 'ATTACHMENT',
              ].map((m) => ({ value: m, label: m }))}
            />
            <Select
              placeholder="Entity"
              allowClear
              style={{ width: 180 }}
              value={entityFilter}
              onChange={setEntityFilter}
              options={[
                'Employee', 'EmployeeAddress', 'EmployeeIdentification',
                'EmployeeAssignment', 'EmployeeStatusOverlay',
                'EmploymentContract', 'LetterRequest',
                'PersonalInfoChangeRequest', 'LeaveRequest', 'PayrollRun',
              ].map((e) => ({ value: e, label: e }))}
            />
            <Input
              placeholder="Actor"
              style={{ width: 140 }}
              value={actorFilter}
              onChange={(e) => setActorFilter(e.target.value)}
              onPressEnter={load}
              allowClear
            />
            <Button icon={<ReloadOutlined />} onClick={load}>Refresh</Button>
          </Space>
        }
      >
        <Table
          rowKey={(r) => `${r.at}-${r.module}-${r.entityName}-${r.entityId ?? ''}-${r.action}`}
          columns={columns}
          dataSource={rows}
          loading={loading}
          pagination={{ pageSize: 50 }}
          locale={{ emptyText: <Empty description="No activity in window" /> }}
          size="small"
        />
      </Card>
    </Space>
  )
}
