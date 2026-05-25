import React, { useEffect, useState } from 'react';
import {
  Button, Card, Col, DatePicker, Drawer, Form, Input, List, message,
  Modal, Popconfirm, Progress, Row, Select, Space, Steps, Table, Tag, Typography,
} from 'antd';
import {
  AimOutlined, BookOutlined, CheckCircleOutlined, DeleteOutlined,
  PlusOutlined, RocketOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { idpApi, IdpResponse, ActivityDto, SkillGapDto } from '../../api/idpApi';

const { Title, Text } = Typography;
const { Option } = Select;

// Seed employee ID — in production, comes from OIDC context or route param
const DEMO_EMPLOYEE_ID = 'e0000000-0000-0000-0000-000000000001';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default', ACTIVE: 'blue', COMPLETED: 'green', CANCELLED: 'red',
};
const ACTIVITY_STATUS_COLOR: Record<string, string> = {
  PENDING: 'default', IN_PROGRESS: 'processing', DONE: 'success', SKIPPED: 'warning',
};

export default function CareerPage() {
  const [idps, setIdps] = useState<IdpResponse[]>([]);
  const [selected, setSelected] = useState<IdpResponse | null>(null);
  const [loading, setLoading] = useState(false);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [activityOpen, setActivityOpen] = useState(false);
  const [gapOpen, setGapOpen] = useState(false);

  const [createForm] = Form.useForm();
  const [actForm] = Form.useForm();
  const [gapForm] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const data = await idpApi.listForEmployee(DEMO_EMPLOYEE_ID);
      setIdps(data);
      if (data.length > 0 && !selected) setSelected(data[0]);
    } catch {
      message.error('Failed to load IDPs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const refresh = async (idpId: string) => {
    const updated = await idpApi.get(idpId);
    setIdps(prev => prev.map(i => i.id === idpId ? updated : i));
    setSelected(updated);
  };

  // ── Create IDP ──────────────────────────────────────────────────────────────
  const handleCreate = async (values: any) => {
    try {
      const idp = await idpApi.createForEmployee(DEMO_EMPLOYEE_ID, {
        targetRole: values.targetRole,
        targetDate: values.targetDate?.format('YYYY-MM-DD'),
      });
      setIdps(prev => [idp, ...prev]);
      setSelected(idp);
      setCreateOpen(false);
      createForm.resetFields();
      message.success('IDP created');
    } catch (e: any) {
      message.error(e.response?.data?.message || 'Create failed');
    }
  };

  // ── Lifecycle ────────────────────────────────────────────────────────────────
  const activate = async () => {
    if (!selected) return;
    await idpApi.activate(selected.id);
    refresh(selected.id);
    message.success('IDP activated');
  };

  const complete = async () => {
    if (!selected) return;
    await idpApi.complete(selected.id);
    refresh(selected.id);
    message.success('IDP completed');
  };

  const cancel = async () => {
    if (!selected) return;
    await idpApi.cancel(selected.id);
    refresh(selected.id);
    message.success('IDP cancelled');
  };

  // ── Skill gaps ───────────────────────────────────────────────────────────────
  const handleAddGap = async (values: any) => {
    if (!selected) return;
    await idpApi.addSkillGap(selected.id, {
      skillName: values.skillName,
      currentLevel: values.currentLevel,
      targetLevel: values.targetLevel,
      notes: values.notes,
    });
    refresh(selected.id);
    setGapOpen(false);
    gapForm.resetFields();
    message.success('Skill gap added');
  };

  const removeGap = async (gapId: string) => {
    if (!selected) return;
    await idpApi.removeSkillGap(selected.id, gapId);
    refresh(selected.id);
  };

  // ── Activities ───────────────────────────────────────────────────────────────
  const handleAddActivity = async (values: any) => {
    if (!selected) return;
    await idpApi.addActivity(selected.id, {
      title: values.title,
      activityType: values.activityType,
      dueDate: values.dueDate?.format('YYYY-MM-DD'),
      notes: values.notes,
    });
    refresh(selected.id);
    setActivityOpen(false);
    actForm.resetFields();
    message.success('Activity added');
  };

  const markActivity = async (actId: string, status: string) => {
    if (!selected) return;
    await idpApi.updateActivityStatus(selected.id, actId, status);
    refresh(selected.id);
  };

  // ── Computed ─────────────────────────────────────────────────────────────────
  const doneCount = selected?.activities.filter(a => a.status === 'DONE').length ?? 0;
  const totalCount = selected?.activities.length ?? 0;
  const pct = totalCount > 0 ? Math.round((doneCount / totalCount) * 100) : 0;

  return (
    <div style={{ padding: 24 }}>
      <Row gutter={16} align="middle" style={{ marginBottom: 16 }}>
        <Col flex={1}><Title level={3} style={{ margin: 0 }}>Career Development Plans</Title></Col>
        <Col>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            New IDP
          </Button>
        </Col>
      </Row>

      <Row gutter={16}>
        {/* Left: IDP list */}
        <Col xs={24} md={8}>
          <List
            loading={loading}
            dataSource={idps}
            renderItem={idp => (
              <List.Item
                style={{
                  cursor: 'pointer',
                  background: selected?.id === idp.id ? '#e6f4ff' : undefined,
                  padding: '8px 12px',
                  borderRadius: 6,
                  marginBottom: 8,
                }}
                onClick={() => setSelected(idp)}
              >
                <List.Item.Meta
                  avatar={<RocketOutlined style={{ fontSize: 20, color: '#1677ff' }} />}
                  title={idp.targetRole}
                  description={
                    <Space>
                      <Tag color={STATUS_COLOR[idp.status]}>{idp.status}</Tag>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {idp.targetDate ? dayjs(idp.targetDate).format('MMM YYYY') : 'No target date'}
                      </Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />
        </Col>

        {/* Right: IDP detail */}
        {selected && (
          <Col xs={24} md={16}>
            <Card
              title={
                <Space>
                  <RocketOutlined />
                  <span>{selected.targetRole}</span>
                  <Tag color={STATUS_COLOR[selected.status]}>{selected.status}</Tag>
                </Space>
              }
              extra={
                <Space>
                  {selected.status === 'DRAFT' && (
                    <Button type="primary" onClick={activate}>Activate</Button>
                  )}
                  {selected.status === 'ACTIVE' && (
                    <Button type="primary" onClick={complete} icon={<CheckCircleOutlined />}>Mark Complete</Button>
                  )}
                  {(selected.status === 'DRAFT' || selected.status === 'ACTIVE') && (
                    <Popconfirm title="Cancel this IDP?" onConfirm={cancel}>
                      <Button danger>Cancel</Button>
                    </Popconfirm>
                  )}
                </Space>
              }
            >
              {/* Progress */}
              {totalCount > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <Text type="secondary">Training progress: {doneCount}/{totalCount} activities</Text>
                  <Progress percent={pct} status={pct === 100 ? 'success' : 'active'} />
                </div>
              )}

              {/* Skill Gaps */}
              <Card
                size="small"
                title={<><AimOutlined /> Skill Gaps</>}
                style={{ marginBottom: 12 }}
                extra={
                  <Button size="small" icon={<PlusOutlined />} onClick={() => setGapOpen(true)}>
                    Add
                  </Button>
                }
              >
                <Table
                  dataSource={selected.skillGaps}
                  rowKey="id"
                  size="small"
                  pagination={false}
                  columns={[
                    { title: 'Skill', dataIndex: 'skillName' },
                    {
                      title: 'Current → Target',
                      render: (_: any, r: SkillGapDto) =>
                        `${r.currentLevel ?? '?'} → ${r.targetLevel ?? '?'}`,
                    },
                    { title: 'Notes', dataIndex: 'notes', ellipsis: true },
                    {
                      title: '',
                      render: (_: any, r: SkillGapDto) => (
                        <Popconfirm title="Remove gap?" onConfirm={() => removeGap(r.id)}>
                          <Button icon={<DeleteOutlined />} size="small" danger type="text" />
                        </Popconfirm>
                      ),
                    },
                  ]}
                />
              </Card>

              {/* Activities */}
              <Card
                size="small"
                title={<><BookOutlined /> Training Activities</>}
                extra={
                  <Button size="small" icon={<PlusOutlined />} onClick={() => setActivityOpen(true)}>
                    Add
                  </Button>
                }
              >
                <Table
                  dataSource={selected.activities}
                  rowKey="id"
                  size="small"
                  pagination={false}
                  columns={[
                    { title: 'Title', dataIndex: 'title', ellipsis: true },
                    {
                      title: 'Type',
                      dataIndex: 'activityType',
                      render: (v: string) => <Tag>{v}</Tag>,
                    },
                    {
                      title: 'Due',
                      dataIndex: 'dueDate',
                      render: (v: string) => v ? dayjs(v).format('DD MMM YYYY') : '—',
                    },
                    {
                      title: 'Status',
                      dataIndex: 'status',
                      render: (v: string) => <Tag color={ACTIVITY_STATUS_COLOR[v]}>{v}</Tag>,
                    },
                    {
                      title: 'Action',
                      render: (_: any, r: ActivityDto) => (
                        <Select
                          size="small"
                          value={r.status}
                          style={{ width: 120 }}
                          onChange={s => markActivity(r.id, s)}
                        >
                          <Option value="PENDING">Pending</Option>
                          <Option value="IN_PROGRESS">In Progress</Option>
                          <Option value="DONE">Done</Option>
                          <Option value="SKIPPED">Skipped</Option>
                        </Select>
                      ),
                    },
                  ]}
                />
              </Card>

              {selected.managerComment && (
                <Card size="small" title="Manager Comment" style={{ marginTop: 12 }}>
                  <Text>{selected.managerComment}</Text>
                </Card>
              )}
            </Card>
          </Col>
        )}
      </Row>

      {/* Create IDP modal */}
      <Modal
        title="New Individual Development Plan"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        okText="Create"
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="targetRole" label="Target Role" rules={[{ required: true }]}>
            <Input placeholder="e.g. Senior Engineer" />
          </Form.Item>
          <Form.Item name="targetDate" label="Target Date">
            <DatePicker picker="month" style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* Add skill gap drawer */}
      <Drawer
        title="Add Skill Gap"
        open={gapOpen}
        onClose={() => setGapOpen(false)}
        extra={<Button type="primary" onClick={() => gapForm.submit()}>Save</Button>}
      >
        <Form form={gapForm} layout="vertical" onFinish={handleAddGap}>
          <Form.Item name="skillName" label="Skill Name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="currentLevel" label="Current Level (1-5)">
            <Select>
              {[1,2,3,4,5].map(n => <Option key={n} value={n}>{n}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="targetLevel" label="Target Level (1-5)">
            <Select>
              {[1,2,3,4,5].map(n => <Option key={n} value={n}>{n}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* Add activity drawer */}
      <Drawer
        title="Add Training Activity"
        open={activityOpen}
        onClose={() => setActivityOpen(false)}
        extra={<Button type="primary" onClick={() => actForm.submit()}>Save</Button>}
      >
        <Form form={actForm} layout="vertical" onFinish={handleAddActivity}>
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="activityType" label="Type" initialValue="COURSE">
            <Select>
              {['COURSE','MENTORING','PROJECT','CONFERENCE','READING','OTHER'].map(t => (
                <Option key={t} value={t}>{t}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="dueDate" label="Due Date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}
