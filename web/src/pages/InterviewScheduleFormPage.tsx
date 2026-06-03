// M86 — Schedule a new interview against an application. Reached either
// from the application detail surface (in a future revision) or directly
// via /recruitment/interviews/schedule?applicationId=…&kitId=…

import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { interviewKitsApi, interviewsApi, type InterviewKit } from '../api/interviews'

export function InterviewScheduleFormPage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const [search] = useSearchParams()
  const presetApplicationId = search.get('applicationId') ?? ''

  const [kits, setKits] = useState<InterviewKit[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [form] = Form.useForm<{
    applicationId: string
    kitId: string
    interviewerEmployeeId: string
    scheduledAt: ReturnType<typeof dayjs>
  }>()

  useEffect(() => {
    interviewKitsApi
      .list(undefined, true)
      .then(setKits)
      .catch((e) =>
        message.error(e?.response?.data?.message ?? 'Failed to load kits'),
      )
    if (presetApplicationId) {
      form.setFieldsValue({ applicationId: presetApplicationId })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const onFinish = async (values: {
    applicationId: string
    kitId: string
    interviewerEmployeeId: string
    scheduledAt: ReturnType<typeof dayjs>
  }) => {
    setSubmitting(true)
    try {
      const created = await interviewsApi.schedule({
        applicationId: values.applicationId,
        kitId: values.kitId,
        interviewerEmployeeId: values.interviewerEmployeeId,
        scheduledAt: values.scheduledAt.toISOString(),
      })
      message.success(`Scheduled ${created.interviewNo}`)
      navigate(`/recruitment/interviews/${created.id}`)
    } catch (e) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Schedule failed',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Card
      title={
        <Typography.Title level={4} style={{ margin: 0 }}>
          Schedule interview
        </Typography.Title>
      }
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        style={{ maxWidth: 640 }}
      >
        <Form.Item
          label="Application ID"
          name="applicationId"
          rules={[{ required: true, message: 'Required' }]}
          extra="UUID of the candidate's application to a vacancy"
        >
          <Input placeholder="00000000-0000-0000-0000-000000000000" />
        </Form.Item>
        <Form.Item
          label="Interview kit"
          name="kitId"
          rules={[{ required: true, message: 'Pick a kit' }]}
        >
          <Select
            placeholder="Pick a scoring template"
            options={kits.map((k) => ({ value: k.id, label: `${k.code} — ${k.name}` }))}
          />
        </Form.Item>
        <Form.Item
          label="Interviewer employee ID"
          name="interviewerEmployeeId"
          rules={[{ required: true, message: 'Required' }]}
          extra="UUID of the employee conducting the interview"
        >
          <Input placeholder="00000000-0000-0000-0000-000000000000" />
        </Form.Item>
        <Form.Item
          label="Scheduled time"
          name="scheduledAt"
          rules={[{ required: true, message: 'Pick a time' }]}
        >
          <DatePicker showTime style={{ width: 320 }} />
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={submitting}>
            Schedule
          </Button>
          <Button onClick={() => navigate(-1)}>Cancel</Button>
        </Space>
      </Form>
    </Card>
  )
}
