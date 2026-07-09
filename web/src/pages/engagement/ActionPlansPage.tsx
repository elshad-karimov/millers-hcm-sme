// M479 — Engagement action plans CRUD + items.

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Checkbox,
  Drawer,
  Form,
  Input,
  Modal,
  Progress,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
  DatePicker,
} from 'antd'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { actionPlanApi, type EngagementActionPlan, type EngagementActionItem, type ActionPlanWithProgress } from '../../api/engagement'

export function ActionPlansPage() {
  const { message } = AntdApp.useApp()
  const [plans, setPlans] = useState<EngagementActionPlan[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [currentPlan, setCurrentPlan] = useState<ActionPlanWithProgress | null>(null)
  const [items, setItems] = useState<EngagementActionItem[]>([])
  const [form] = Form.useForm()
  const [itemForm] = Form.useForm()

  const load = () => {
    setLoading(true)
    actionPlanApi
      .listAll()
      .then(setPlans)
      .catch((e) => message.error(e?.response?.data?.message ?? 'Failed to load'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const openCreate = () => {
    form.resetFields()
    setEditingId(null)
    setModalOpen(true)
  }

  const openEdit = (plan: EngagementActionPlan) => {
    form.setFieldsValue({ ...plan, dueDate: plan.dueDate ? dayjs(plan.dueDate) : null })
    setEditingId(plan.id!)
    setModalOpen(true)
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const payload = { ...values, dueDate: values.dueDate ? values.dueDate.format('YYYY-MM-DD') : null }
      if (editingId) {
        await actionPlanApi.update(editingId, payload)
        message.success('Plan updated')
      } else {
        await actionPlanApi.create(payload)
        message.success('Plan created')
      }
      setModalOpen(false)
      load()
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Save failed')
    }
  }

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: 'Delete plan?',
      onOk: async () => {
        try {
          await actionPlanApi.delete(id)
          message.success('Plan deleted')
          load()
        } catch (e: any) {
          message.error(e?.response?.data?.message ?? 'Delete failed')
        }
      },
    })
  }

  const openItems = async (planId: string) => {
    try {
      const [plan, items] = await Promise.all([actionPlanApi.get(planId), actionPlanApi.listItems(planId)])
      setCurrentPlan(plan)
      setItems(items)
      setDrawerOpen(true)
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Failed to load items')
    }
  }

  const addItem = async () => {
    try {
      const values = await itemForm.validateFields()
      await actionPlanApi.addItem(currentPlan!.plan.id!, values)
      message.success('Item added')
      itemForm.resetFields()
      if (currentPlan?.plan.id) {
        const items = await actionPlanApi.listItems(currentPlan.plan.id)
        setItems(items)
      }
    } catch (e: any) {
      if (e?.errorFields) return
      message.error(e?.response?.data?.message ?? 'Add failed')
    }
  }

  const toggleItem = async (itemId: string) => {
    try {
      await actionPlanApi.toggleItem(itemId)
      if (currentPlan?.plan.id) {
        const items = await actionPlanApi.listItems(currentPlan.plan.id)
        setItems(items)
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message ?? 'Toggle failed')
    }
  }

  const columns: ColumnsType<EngagementActionPlan> = [
    { title: 'Title', dataIndex: 'title' },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => <Tag>{s}</Tag>,
    },
    { title: 'Due Date', dataIndex: 'dueDate', width: 120, render: (d) => d ?? '—' },
    {
      title: 'Actions',
      width: 200,
      render: (_, r) => (
        <Space>
          <a onClick={() => openItems(r.id!)}>Items</a>
          <a onClick={() => openEdit(r)}>Edit</a>
          <a onClick={() => handleDelete(r.id!)} style={{ color: 'red' }}>
            Delete
          </a>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card title="Engagement Action Plans" extra={<Button type="primary" onClick={openCreate}>Create Plan</Button>}>
        <Table rowKey="id" columns={columns} dataSource={plans} loading={loading} />
      </Card>

      <Modal
        title={editingId ? 'Edit Plan' : 'Create Plan'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="dueDate" label="Due Date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={currentPlan?.plan.title}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
      >
        {currentPlan && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Card size="small">
              <Progress percent={currentPlan.progress} />
              <Typography.Text type="secondary">
                {currentPlan.completedItems} / {currentPlan.totalItems} completed
              </Typography.Text>
            </Card>
            <Card title="Add Item" size="small">
              <Form form={itemForm} layout="inline" onFinish={addItem}>
                <Form.Item name="description" rules={[{ required: true }]} style={{ flex: 1 }}>
                  <Input placeholder="Task description" />
                </Form.Item>
                <Form.Item>
                  <Button type="primary" htmlType="submit">
                    Add
                  </Button>
                </Form.Item>
              </Form>
            </Card>
            <Space direction="vertical" style={{ width: '100%' }}>
              {items.map((item) => (
                <Card key={item.id} size="small">
                  <Checkbox checked={item.done} onChange={() => toggleItem(item.id!)}>
                    <Typography.Text delete={item.done}>{item.description}</Typography.Text>
                  </Checkbox>
                </Card>
              ))}
            </Space>
          </Space>
        )}
      </Drawer>
    </>
  )
}
