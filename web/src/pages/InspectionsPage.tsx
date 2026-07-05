import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  App as AntdApp,
  Descriptions,
  Divider,
} from 'antd'
import { PlusOutlined, CheckOutlined, EyeOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  inspectionsApi,
  type SafetyInspectionResponse,
  type InspectionStatus,
  type InspectionFindingResponse,
  FINDING_STATUS_OPTIONS,
} from '../api/ehs'
import { locationApi, type LocationResponse } from '../api/location'

export function InspectionsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [inspections, setInspections] = useState<SafetyInspectionResponse[]>([])
  const [filterStatus, setFilterStatus] = useState<InspectionStatus | undefined>()

  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [locations, setLocations] = useState<LocationResponse[]>([])

  const [selectedInspection, setSelectedInspection] = useState<SafetyInspectionResponse | null>(null)
  const [findings, setFindings] = useState<InspectionFindingResponse[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)

  const [findingRows, setFindingRows] = useState<Array<{ key: number }>>([])

  useEffect(() => {
    loadInspections()
    loadLocations()
  }, [])

  const loadInspections = async () => {
    setLoading(true)
    try {
      const data = await inspectionsApi.list()
      setInspections(data)
    } catch (err) {
      message.error('Failed to load inspections')
    } finally {
      setLoading(false)
    }
  }

  const loadLocations = async () => {
    try {
      const data = await locationApi.list(true)
      setLocations(data)
    } catch {
      // non-critical
    }
  }

  const openCreateModal = () => {
    form.resetFields()
    setFindingRows([])
    setModalOpen(true)
  }

  const handleSubmit = async (values: any) => {
    setSubmitting(true)
    try {
      const findingsData = findingRows.map((row) => ({
        itemLabel: values[`finding_item_${row.key}`] || '',
        findingStatus: values[`finding_status_${row.key}`] || 'OK',
        notes: values[`finding_notes_${row.key}`] || undefined,
      })).filter((f) => f.itemLabel)

      await inspectionsApi.create({
        workLocationId: values.workLocationId || undefined,
        inspectionDate: values.inspectionDate.format('YYYY-MM-DD'),
        inspectorUsername: values.inspectorUsername,
        title: values.title,
        notes: values.notes || undefined,
        findings: findingsData.length > 0 ? findingsData : undefined,
      })
      message.success('Inspection created')
      setModalOpen(false)
      form.resetFields()
      loadInspections()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to create inspection')
    } finally {
      setSubmitting(false)
    }
  }

  const openDrawer = async (inspection: SafetyInspectionResponse) => {
    setSelectedInspection(inspection)
    setDrawerOpen(true)
    try {
      const data = await inspectionsApi.getFindings(inspection.id)
      setFindings(data)
    } catch (err) {
      message.error('Failed to load findings')
    }
  }

  const handleComplete = async (id: string) => {
    try {
      await inspectionsApi.complete(id)
      message.success('Inspection completed')
      setDrawerOpen(false)
      loadInspections()
    } catch (err) {
      message.error((err as any)?.response?.data?.message || 'Failed to complete inspection')
    }
  }

  const filteredInspections = filterStatus
    ? inspections.filter((i) => i.status === filterStatus)
    : inspections

  const columns: ColumnsType<SafetyInspectionResponse> = [
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      width: 300,
      render: (text, record) => <a onClick={() => openDrawer(record)}>{text}</a>,
    },
    {
      title: 'Date',
      dataIndex: 'inspectionDate',
      key: 'inspectionDate',
      width: 110,
      sorter: (a, b) => a.inspectionDate.localeCompare(b.inspectionDate),
    },
    {
      title: 'Inspector',
      dataIndex: 'inspectorUsername',
      key: 'inspectorUsername',
      width: 140,
    },
    {
      title: 'Score',
      dataIndex: 'overallScore',
      key: 'overallScore',
      width: 80,
      render: (val) => (val !== null && val !== undefined ? val : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (val: InspectionStatus) => (
        <Tag color={val === 'COMPLETED' ? 'green' : 'default'}>{val}</Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_, record) => (
        <Button icon={<EyeOutlined />} size="small" onClick={() => openDrawer(record)} />
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Safety Inspections"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            New inspection
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }}>
          <Select
            placeholder="Filter by status"
            style={{ width: 180 }}
            allowClear
            value={filterStatus}
            onChange={setFilterStatus}
            options={[
              { value: 'SCHEDULED', label: 'Scheduled' },
              { value: 'COMPLETED', label: 'Completed' },
            ]}
          />
        </Space>

        <Table
          loading={loading}
          dataSource={filteredInspections}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        title="New safety inspection"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={720}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="workLocationId" label="Location">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={locations.map((l) => ({
                    value: l.id,
                    label: `${l.code} — ${l.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="inspectionDate"
                label="Inspection date"
                rules={[{ required: true }]}
                initialValue={dayjs()}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="inspectorUsername"
                label="Inspector username"
                rules={[{ required: true }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="title" label="Title" rules={[{ required: true, max: 200 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Divider>Findings</Divider>

          <Button
            type="dashed"
            onClick={() =>
              setFindingRows([...findingRows, { key: Date.now() }])
            }
            block
            style={{ marginBottom: 16 }}
          >
            + Add finding
          </Button>

          {findingRows.map((row, idx) => (
            <Card key={row.key} size="small" title={`Finding ${idx + 1}`} style={{ marginBottom: 12 }}>
              <Form.Item
                name={`finding_item_${row.key}`}
                label="Item"
                rules={[{ required: true, max: 300 }]}
              >
                <Input />
              </Form.Item>
              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name={`finding_status_${row.key}`}
                    label="Status"
                    initialValue="OK"
                  >
                    <Select options={FINDING_STATUS_OPTIONS} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name={`finding_notes_${row.key}`} label="Notes">
                    <Input />
                  </Form.Item>
                </Col>
              </Row>
            </Card>
          ))}

          <Form.Item style={{ marginTop: 24, marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Create
              </Button>
              <Button
                onClick={() => {
                  setModalOpen(false)
                  form.resetFields()
                }}
              >
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Drawer */}
      <Drawer
        title="Inspection Details"
        placement="right"
        width={600}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {selectedInspection && (
          <div>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="Title">{selectedInspection.title}</Descriptions.Item>
              <Descriptions.Item label="Date">{selectedInspection.inspectionDate}</Descriptions.Item>
              <Descriptions.Item label="Inspector">{selectedInspection.inspectorUsername}</Descriptions.Item>
              <Descriptions.Item label="Score">
                {selectedInspection.overallScore !== null && selectedInspection.overallScore !== undefined
                  ? selectedInspection.overallScore
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={selectedInspection.status === 'COMPLETED' ? 'green' : 'default'}>
                  {selectedInspection.status}
                </Tag>
              </Descriptions.Item>
              {selectedInspection.notes && (
                <Descriptions.Item label="Notes">{selectedInspection.notes}</Descriptions.Item>
              )}
            </Descriptions>

            <Divider>Findings ({findings.length})</Divider>

            {findings.length === 0 ? (
              <p style={{ color: '#999' }}>No findings recorded</p>
            ) : (
              <Space direction="vertical" style={{ width: '100%' }}>
                {findings.map((f) => (
                  <Card key={f.id} size="small">
                    <div>
                      <strong>{f.itemLabel}</strong>
                      <Tag
                        color={f.findingStatus === 'OK' ? 'green' : 'red'}
                        style={{ marginLeft: 8 }}
                      >
                        {f.findingStatus}
                      </Tag>
                      {f.notes && (
                        <div style={{ marginTop: 4, fontSize: 12, color: '#666' }}>
                          {f.notes}
                        </div>
                      )}
                    </div>
                  </Card>
                ))}
              </Space>
            )}

            {selectedInspection.status === 'SCHEDULED' && (
              <div style={{ marginTop: 24 }}>
                <Button
                  type="primary"
                  icon={<CheckOutlined />}
                  onClick={() => handleComplete(selectedInspection.id)}
                >
                  Complete inspection
                </Button>
              </div>
            )}
          </div>
        )}
      </Drawer>
    </div>
  )
}
