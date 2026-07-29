import { useEffect, useState } from 'react'
import {
  Badge,
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { hrPartnerApi, type HrPartnerRequest, type HrPartnerResponse } from '../api/hrPartner'
import { employeesApi, type Employee } from '../api/employees'
import { EmployeePicker } from '../components/EmployeePicker'
import { orgApi, type OrgUnitResponse } from '../api/org'

const { Title } = Typography

export function HrPartnersPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<HrPartnerRequest>()

  const [assignments, setAssignments] = useState<HrPartnerResponse[]>([])
  const [employees, setEmployees] = useState<Employee[]>([])
  const [orgUnits, setOrgUnits] = useState<OrgUnitResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editId, setEditId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const [empPage, versions] = await Promise.all([
        employeesApi.list({ size: 500 }),
        orgApi.versions(),
      ])
      setEmployees(empPage.content)
      // collect org units from the active (or latest) version
      const active = versions.find((v) => v.status === 'ACTIVE') ?? versions[0]
      if (active) {
        const units = await orgApi.units(active.id)
        setOrgUnits(units)
        // Load assignments for all units (by loading each unit's list)
        // For the admin view, we load by employee instead to get a flat list.
        // As a pragmatic approach, load all assignments by fetching for each unit.
        const lists = await Promise.all(
          units.map((u) => hrPartnerApi.listForUnit(u.id).catch(() => [])),
        )
        setAssignments(lists.flat())
      }
    } catch {
      message.error('Failed to load HR partner assignments')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const empLabel = (id: string) => {
    const e = employees.find((x) => x.id === id)
    return e ? `${e.firstName} ${e.lastName} (${e.employeeNo})` : id
  }

  const unitLabel = (id: string) => {
    const u = orgUnits.find((x) => x.id === id)
    return u ? `${u.name} (${u.code})` : id
  }

  const openCreate = () => {
    setEditId(null)
    form.resetFields()
    form.setFieldsValue({ backup: false, active: true })
    setModalOpen(true)
  }

  const openEdit = (row: HrPartnerResponse) => {
    setEditId(row.id)
    form.setFieldsValue({
      orgUnitId: row.orgUnitId,
      employeeId: row.employeeId,
      backup: row.backup,
      effectiveFrom: row.effectiveFrom ?? undefined,
      effectiveTo: row.effectiveTo ?? undefined,
      active: row.active,
      notes: row.notes ?? undefined,
    })
    setModalOpen(true)
  }

  const onSave = async () => {
    let values: HrPartnerRequest
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      if (editId) {
        await hrPartnerApi.update(editId, values)
        message.success('Assignment updated')
      } else {
        await hrPartnerApi.create(values)
        message.success('Assignment created')
      }
      setModalOpen(false)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (row: HrPartnerResponse) => {
    try {
      if (row.active) {
        await hrPartnerApi.deactivate(row.id)
      } else {
        await hrPartnerApi.activate(row.id)
      }
      load()
    } catch {
      message.error('Failed to update status')
    }
  }

  const unitOptions = orgUnits.map((u) => ({
    value: u.id,
    label: `${u.name} (${u.code})`,
  }))

  const columns = [
    {
      title: 'Org unit',
      dataIndex: 'orgUnitId',
      render: (id: string) => unitLabel(id),
    },
    {
      title: 'HRBP',
      dataIndex: 'employeeId',
      render: (id: string) => empLabel(id),
    },
    {
      title: 'Role',
      dataIndex: 'backup',
      render: (b: boolean) =>
        b ? <Tag color="default">Backup</Tag> : <Tag color="blue">Primary</Tag>,
    },
    {
      title: 'Effective from',
      dataIndex: 'effectiveFrom',
      render: (d: string | null) => d ?? '—',
    },
    {
      title: 'Effective to',
      dataIndex: 'effectiveTo',
      render: (d: string | null) => d ?? '—',
    },
    {
      title: 'Status',
      dataIndex: 'active',
      render: (a: boolean) => <Badge status={a ? 'success' : 'default'} text={a ? 'Active' : 'Inactive'} />,
    },
    {
      title: 'Actions',
      render: (_: unknown, row: HrPartnerResponse) => (
        <Space>
          <Button size="small" onClick={() => openEdit(row)}>Edit</Button>
          <Button size="small" onClick={() => toggle(row)}>
            {row.active ? 'Deactivate' : 'Activate'}
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>HR Partner assignments</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          Add assignment
        </Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={assignments}
        columns={columns}
        size="small"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={editId ? 'Edit HRBP assignment' : 'New HRBP assignment'}
        open={modalOpen}
        onOk={onSave}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        width={520}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="orgUnitId" label="Org unit" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={unitOptions}
              placeholder="Select org unit"
            />
          </Form.Item>
          <Form.Item name="employeeId" label="HRBP employee" rules={[{ required: true }]}>
            <EmployeePicker placeholder="Select employee" />
          </Form.Item>
          <Form.Item name="backup" label="Role" valuePropName="checked">
            <Switch checkedChildren="Backup" unCheckedChildren="Primary" />
          </Form.Item>
          <Form.Item name="effectiveFrom" label="Effective from">
            <DatePicker
              style={{ width: '100%' }}
              value={form.getFieldValue('effectiveFrom') ? dayjs(form.getFieldValue('effectiveFrom')) : null}
              onChange={(d) => form.setFieldValue('effectiveFrom', d ? d.format('YYYY-MM-DD') : null)}
            />
          </Form.Item>
          <Form.Item name="effectiveTo" label="Effective to">
            <DatePicker
              style={{ width: '100%' }}
              value={form.getFieldValue('effectiveTo') ? dayjs(form.getFieldValue('effectiveTo')) : null}
              onChange={(d) => form.setFieldValue('effectiveTo', d ? d.format('YYYY-MM-DD') : null)}
            />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
