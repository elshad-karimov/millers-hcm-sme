import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  TimePicker,
  Modal,
  App as AntdApp,
  Row,
  Col,
  Descriptions,
  Tabs,
  Divider,
} from 'antd'
import {
  PlusOutlined,
  EyeOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import {
  incidentsApi,
  injuryReportsApi,
  type IncidentResponse,
  type IncidentStatus,
  type IncidentType,
  type IncidentSeverity,
  type InjuryReportResponse,
  type IncidentInvolvedResponse,
  type IncidentWitnessResponse,
  INCIDENT_TYPE_OPTIONS,
  INCIDENT_SEVERITY_OPTIONS,
  INCIDENT_STATUS_OPTIONS,
  INCIDENT_SEVERITY_COLOR,
  INCIDENT_STATUS_COLOR,
} from '../api/ehs'
import { employeesApi, type Employee } from '../api/employees'
import { orgApi, type OrgUnitResponse } from '../api/org'
import { locationApi, type LocationResponse } from '../api/location'

export function EhsIncidentsPage() {
  const { message } = AntdApp.useApp()
  const [loading, setLoading] = useState(false)
  const [incidents, setIncidents] = useState<IncidentResponse[]>([])
  const [filterStatus, setFilterStatus] = useState<IncidentStatus | undefined>()
  const [filterType, setFilterType] = useState<IncidentType | undefined>()
  const [filterSeverity, setFilterSeverity] = useState<IncidentSeverity | undefined>()

  const [reportModalOpen, setReportModalOpen] = useState(false)
  const [reportForm] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)

  const [selectedIncident, setSelectedIncident] = useState<IncidentResponse | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [involved, setInvolved] = useState<IncidentInvolvedResponse[]>([])
  const [witnesses, setWitnesses] = useState<IncidentWitnessResponse[]>([])
  const [injuries, setInjuries] = useState<InjuryReportResponse[]>([])

  const [closeModalOpen, setCloseModalOpen] = useState(false)
  const [closeForm] = Form.useForm()
  const [closing, setClosing] = useState(false)

  const [employees, setEmployees] = useState<Employee[]>([])
  const [orgUnits, setOrgUnits] = useState<OrgUnitResponse[]>([])
  const [locations, setLocations] = useState<LocationResponse[]>([])

  // Witness fields
  const [witnessCount, setWitnessCount] = useState(0)

  useEffect(() => {
    loadIncidents()
    loadEmployees()
    loadOrgUnits()
    loadLocations()
  }, [])

  const loadIncidents = async () => {
    setLoading(true)
    try {
      const data = await incidentsApi.list()
      setIncidents(data)
    } catch (err) {
      message.error('Failed to load incidents')
    } finally {
      setLoading(false)
    }
  }

  const loadEmployees = async () => {
    try {
      const data = await employeesApi.list()
      setEmployees(data.content)
    } catch {
      // non-critical
    }
  }

  const loadOrgUnits = async () => {
    try {
      const activeVersion = await orgApi.active()
      if (activeVersion) {
        const units = await orgApi.units(activeVersion.id)
        setOrgUnits(units)
      }
    } catch {
      // non-critical
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

  const handleReportIncident = async (values: any) => {
    setSubmitting(true)
    try {
      const witnesses = []
      for (let i = 0; i < witnessCount; i++) {
        if (values[`witness_name_${i}`]) {
          witnesses.push({
            name: values[`witness_name_${i}`],
            contact: values[`witness_contact_${i}`] || undefined,
            statement: values[`witness_statement_${i}`] || undefined,
          })
        }
      }

      await incidentsApi.report({
        incidentDate: values.incidentDate.format('YYYY-MM-DD'),
        incidentTime: values.incidentTime.format('HH:mm:ss'),
        workLocationId: values.workLocationId || undefined,
        orgUnitId: values.orgUnitId || undefined,
        incidentType: values.incidentType,
        severity: values.severity,
        description: values.description,
        immediateAction: values.immediateAction || undefined,
        involvedEmployeeIds: values.involvedEmployeeIds || undefined,
        witnesses: witnesses.length > 0 ? witnesses : undefined,
      })
      message.success('Incident reported')
      setReportModalOpen(false)
      reportForm.resetFields()
      setWitnessCount(0)
      loadIncidents()
    } catch (err) {
      message.error(
        (err as any)?.response?.data?.message || 'Failed to report incident',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const openIncidentDrawer = async (incident: IncidentResponse) => {
    setSelectedIncident(incident)
    setDrawerOpen(true)
    try {
      const [involvedData, witnessData, injuryData] = await Promise.all([
        incidentsApi.getInvolved(incident.id),
        incidentsApi.getWitnesses(incident.id),
        injuryReportsApi.list(incident.id, undefined),
      ])
      setInvolved(involvedData)
      setWitnesses(witnessData)
      setInjuries(injuryData)
    } catch (err) {
      message.error('Failed to load incident details')
    }
  }

  const handleCloseIncident = async (values: any) => {
    if (!selectedIncident) return
    setClosing(true)
    try {
      await incidentsApi.close(selectedIncident.id, { resolution: values.resolution })
      message.success('Incident closed')
      setCloseModalOpen(false)
      closeForm.resetFields()
      setDrawerOpen(false)
      loadIncidents()
    } catch (err) {
      message.error(
        (err as any)?.response?.data?.message || 'Failed to close incident',
      )
    } finally {
      setClosing(false)
    }
  }

  const filteredIncidents = incidents.filter((inc) => {
    if (filterStatus && inc.status !== filterStatus) return false
    if (filterType && inc.incidentType !== filterType) return false
    if (filterSeverity && inc.severity !== filterSeverity) return false
    return true
  })

  const columns: ColumnsType<IncidentResponse> = [
    {
      title: 'Incident No',
      dataIndex: 'incidentNo',
      key: 'incidentNo',
      width: 140,
      render: (text, record) => (
        <a onClick={() => openIncidentDrawer(record)}>{text}</a>
      ),
    },
    {
      title: 'Date',
      dataIndex: 'incidentDate',
      key: 'incidentDate',
      width: 110,
      sorter: (a, b) => a.incidentDate.localeCompare(b.incidentDate),
    },
    {
      title: 'Type',
      dataIndex: 'incidentType',
      key: 'incidentType',
      width: 160,
      render: (val) =>
        INCIDENT_TYPE_OPTIONS.find((o) => o.value === val)?.label || val,
    },
    {
      title: 'Severity',
      dataIndex: 'severity',
      key: 'severity',
      width: 110,
      render: (val: IncidentSeverity) => (
        <Tag color={INCIDENT_SEVERITY_COLOR[val]}>{val}</Tag>
      ),
    },
    {
      title: 'Reported by',
      dataIndex: 'reportedByEmployeeName',
      key: 'reportedByEmployeeName',
      width: 180,
      render: (text) => text || '—',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (val: IncidentStatus) => (
        <Tag color={INCIDENT_STATUS_COLOR[val]}>
          {val.replace('_', ' ')}
        </Tag>
      ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: 'Action',
      key: 'action',
      width: 90,
      render: (_, record) => (
        <Button
          icon={<EyeOutlined />}
          size="small"
          onClick={() => openIncidentDrawer(record)}
        />
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title="Safety Incidents"
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setReportModalOpen(true)}
          >
            Report incident
          </Button>
        }
      >
        <Space style={{ marginBottom: 16 }} wrap>
          <Select
            placeholder="Filter by status"
            style={{ width: 180 }}
            allowClear
            value={filterStatus}
            onChange={setFilterStatus}
            options={INCIDENT_STATUS_OPTIONS}
          />
          <Select
            placeholder="Filter by type"
            style={{ width: 180 }}
            allowClear
            value={filterType}
            onChange={setFilterType}
            options={INCIDENT_TYPE_OPTIONS}
          />
          <Select
            placeholder="Filter by severity"
            style={{ width: 150 }}
            allowClear
            value={filterSeverity}
            onChange={setFilterSeverity}
            options={INCIDENT_SEVERITY_OPTIONS}
          />
        </Space>

        <Table
          loading={loading}
          dataSource={filteredIncidents}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 20 }}
        />
      </Card>

      {/* Report Incident Modal */}
      <Modal
        title="Report safety incident"
        open={reportModalOpen}
        onCancel={() => {
          setReportModalOpen(false)
          reportForm.resetFields()
          setWitnessCount(0)
        }}
        footer={null}
        width={720}
      >
        <Form
          form={reportForm}
          layout="vertical"
          onFinish={handleReportIncident}
          initialValues={{
            incidentDate: dayjs(),
            incidentTime: dayjs(),
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="incidentDate"
                label="Incident date"
                rules={[{ required: true }]}
              >
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="incidentTime"
                label="Time"
                rules={[{ required: true }]}
              >
                <TimePicker style={{ width: '100%' }} format="HH:mm" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="incidentType"
                label="Incident type"
                rules={[{ required: true }]}
              >
                <Select options={INCIDENT_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="severity"
                label="Severity"
                rules={[{ required: true }]}
              >
                <Select options={INCIDENT_SEVERITY_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

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
              <Form.Item name="orgUnitId" label="Department">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={orgUnits.map((u) => ({
                    value: u.id,
                    label: `${u.code} — ${u.name}`,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="description"
            label="Description"
            rules={[{ required: true, max: 2000 }]}
          >
            <Input.TextArea rows={4} />
          </Form.Item>

          <Form.Item name="immediateAction" label="Immediate action taken">
            <Input.TextArea rows={2} />
          </Form.Item>

          <Form.Item name="involvedEmployeeIds" label="Involved employees">
            <Select
              mode="multiple"
              showSearch
              optionFilterProp="label"
              options={employees.map((e) => ({
                value: e.id,
                label: `${e.employeeNo} — ${e.firstName} ${e.lastName}`,
              }))}
            />
          </Form.Item>

          <Divider>Witnesses (optional)</Divider>

          <Button
            type="dashed"
            onClick={() => setWitnessCount(witnessCount + 1)}
            block
            style={{ marginBottom: 16 }}
          >
            + Add witness
          </Button>

          {Array.from({ length: witnessCount }).map((_, i) => (
            <Card
              key={i}
              size="small"
              title={`Witness ${i + 1}`}
              style={{ marginBottom: 12 }}
            >
              <Form.Item
                name={`witness_name_${i}`}
                label="Name"
                rules={[{ required: true, max: 200 }]}
              >
                <Input />
              </Form.Item>
              <Form.Item
                name={`witness_contact_${i}`}
                label="Contact (phone/email)"
              >
                <Input />
              </Form.Item>
              <Form.Item name={`witness_statement_${i}`} label="Statement">
                <Input.TextArea rows={2} />
              </Form.Item>
            </Card>
          ))}

          <Form.Item style={{ marginTop: 24, marginBottom: 0 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Submit report
              </Button>
              <Button
                onClick={() => {
                  setReportModalOpen(false)
                  reportForm.resetFields()
                  setWitnessCount(0)
                }}
              >
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Incident Detail Drawer */}
      <Drawer
        title={`Incident ${selectedIncident?.incidentNo || ''}`}
        placement="right"
        width={720}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        {selectedIncident && (
          <div>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="Date">
                {selectedIncident.incidentDate} {selectedIncident.incidentTime}
              </Descriptions.Item>
              <Descriptions.Item label="Type">
                {
                  INCIDENT_TYPE_OPTIONS.find(
                    (o) => o.value === selectedIncident.incidentType,
                  )?.label
                }
              </Descriptions.Item>
              <Descriptions.Item label="Severity">
                <Tag color={INCIDENT_SEVERITY_COLOR[selectedIncident.severity]}>
                  {selectedIncident.severity}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={INCIDENT_STATUS_COLOR[selectedIncident.status]}>
                  {selectedIncident.status.replace('_', ' ')}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Reported by" span={2}>
                {selectedIncident.reportedByEmployeeName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Description" span={2}>
                {selectedIncident.description}
              </Descriptions.Item>
              {selectedIncident.immediateAction && (
                <Descriptions.Item label="Immediate action" span={2}>
                  {selectedIncident.immediateAction}
                </Descriptions.Item>
              )}
            </Descriptions>

            <Divider />

            <Tabs
              items={[
                {
                  key: 'involved',
                  label: `Involved (${involved.length})`,
                  children: (
                    <div>
                      {involved.length === 0 ? (
                        <p style={{ color: '#999' }}>None</p>
                      ) : (
                        <ul>
                          {involved.map((inv) => (
                            <li key={inv.id}>{inv.employeeName || '—'}</li>
                          ))}
                        </ul>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'witnesses',
                  label: `Witnesses (${witnesses.length})`,
                  children: (
                    <div>
                      {witnesses.length === 0 ? (
                        <p style={{ color: '#999' }}>None</p>
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          {witnesses.map((w) => (
                            <Card key={w.id} size="small">
                              <div>
                                <strong>{w.name}</strong>
                                {w.contact && (
                                  <div style={{ fontSize: 12, color: '#666' }}>
                                    Contact: {w.contact}
                                  </div>
                                )}
                                {w.statement && (
                                  <div style={{ marginTop: 8 }}>
                                    {w.statement}
                                  </div>
                                )}
                              </div>
                            </Card>
                          ))}
                        </Space>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'injuries',
                  label: `Injuries (${injuries.length})`,
                  children: (
                    <div>
                      {injuries.length === 0 ? (
                        <p style={{ color: '#999' }}>
                          No injuries recorded (HR Admin only)
                        </p>
                      ) : (
                        <Space direction="vertical" style={{ width: '100%' }}>
                          {injuries.map((inj) => (
                            <Card key={inj.id} size="small">
                              <div>
                                <strong>{inj.employeeName}</strong> —{' '}
                                {inj.injuryType}
                                <div style={{ fontSize: 12, color: '#666' }}>
                                  Body part: {inj.bodyPart} | Severity:{' '}
                                  {inj.severity}
                                  {inj.lostTimeDays && (
                                    <> | Lost time: {inj.lostTimeDays} days</>
                                  )}
                                </div>
                              </div>
                            </Card>
                          ))}
                        </Space>
                      )}
                    </div>
                  ),
                },
              ]}
            />

            {selectedIncident.status !== 'CLOSED' && (
              <div style={{ marginTop: 24 }}>
                <Button
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => setCloseModalOpen(true)}
                >
                  Close incident
                </Button>
              </div>
            )}
          </div>
        )}
      </Drawer>

      {/* Close Incident Modal */}
      <Modal
        title="Close incident"
        open={closeModalOpen}
        onCancel={() => {
          setCloseModalOpen(false)
          closeForm.resetFields()
        }}
        footer={null}
      >
        <Form form={closeForm} layout="vertical" onFinish={handleCloseIncident}>
          <Form.Item
            name="resolution"
            label="Resolution notes"
            rules={[{ required: true, max: 2000 }]}
          >
            <Input.TextArea rows={6} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={closing}>
                Close
              </Button>
              <Button
                onClick={() => {
                  setCloseModalOpen(false)
                  closeForm.resetFields()
                }}
              >
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
