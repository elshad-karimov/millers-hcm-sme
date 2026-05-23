import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Descriptions,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams } from 'react-router-dom'
import {
  lifecycleApi,
  type ContractChange,
  type ContractChangeStatus,
} from '../api/lifecycle'
import { employeesApi, type Employee } from '../api/employees'
import { FormPageShell } from '../components/FormPageShell'
import { AttachmentUploader } from '../components/AttachmentUploader'

const LIST_PATH = '/lifecycle/contract-changes'

const STATUS_COLOR: Record<ContractChangeStatus, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  APPROVED: 'cyan',
  REJECTED: 'red',
  CANCELLED: 'default',
  APPLIED: 'green',
}

export function ContractChangeDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [c, setC] = useState<ContractChange | null>(null)
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!id) return
    setLoading(true)
    lifecycleApi
      .contractChange(id)
      .then(async (data) => {
        setC(data)
        try {
          const emp = await employeesApi.get(data.employeeId)
          setEmployee(emp)
        } catch {
          setEmployee(null)
        }
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }, [id, message])

  if (loading || !c) {
    return (
      <FormPageShell title="Contract change" backTo={LIST_PATH}>
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      </FormPageShell>
    )
  }

  return (
    <FormPageShell
      title={`${c.changeNo} — ${c.changeType.replace(/_/g, ' ')}`}
      backTo={LIST_PATH}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%', maxWidth: 960 }}>
        <Card extra={<Tag color={STATUS_COLOR[c.status]} style={{ fontSize: 13 }}>{c.status}</Tag>}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Employee">
              {employee
                ? `${employee.employeeNo} — ${employee.firstName} ${employee.lastName}`
                : c.employeeId}
            </Descriptions.Item>
            <Descriptions.Item label="Change type">
              {c.changeType.replace(/_/g, ' ')}
            </Descriptions.Item>
            <Descriptions.Item label="Effective date">{c.effectiveDate}</Descriptions.Item>
            <Descriptions.Item label="Created by">{c.createdBy ?? '—'}</Descriptions.Item>
            {c.reason && (
              <Descriptions.Item label="Reason" span={2}>
                {c.reason}
              </Descriptions.Item>
            )}
            {c.note && (
              <Descriptions.Item label="Note" span={2}>
                {c.note}
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>

        <Row gutter={16}>
          <Col span={12}>
            <Card title="Old value (snapshot at submit)" size="small">
              <pre style={{ background: '#fafafa', padding: 12, borderRadius: 4, fontSize: 12 }}>
                {c.oldValue ? JSON.stringify(c.oldValue, null, 2) : '—'}
              </pre>
            </Card>
          </Col>
          <Col span={12}>
            <Card title="New value (will be applied)" size="small">
              <pre style={{ background: '#f6ffed', padding: 12, borderRadius: 4, fontSize: 12 }}>
                {JSON.stringify(c.newValue, null, 2)}
              </pre>
            </Card>
          </Col>
        </Row>

        {c.status === 'APPLIED' && (
          <Card title="Applied">
            <Descriptions column={2} size="small">
              <Descriptions.Item label="Applied at">{c.appliedAt}</Descriptions.Item>
              <Descriptions.Item label="Applied by">{c.appliedBy}</Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              For SALARY changes the new value lives in <code>payroll.employee_compensation</code>{' '}
              (the next payroll run picks it up automatically). For other types the employee record
              was updated in place.
            </Typography.Text>
          </Card>
        )}

        <Card title="Attachments" size="small">
          <AttachmentUploader
            ownerModule="LIFECYCLE"
            ownerEntity="ContractChange"
            ownerId={c.id}
          />
        </Card>

        <Space>
          <Button onClick={() => navigate(LIST_PATH)}>Back to list</Button>
        </Space>
      </Space>
    </FormPageShell>
  )
}
