import { useCallback, useEffect, useState } from 'react'
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import {
  recruitmentApi,
  type CheckResult,
  type CheckStatus,
  type CheckType,
  type PreHireCheck,
  type PreHireCheckRequest,
} from '../api/recruitment'

/**
 * M286 — Recruitment PRD §25-§27: pre-hire checks for one application.
 * Background / reference / medical / … with confidential medical
 * result detail redacted server-side for non-HR_ADMIN callers.
 */

const TYPE_OPTIONS: { value: CheckType; label: string }[] = [
  { value: 'BACKGROUND', label: 'Background' },
  { value: 'IDENTITY', label: 'Identity' },
  { value: 'EDUCATION', label: 'Education' },
  { value: 'EMPLOYMENT', label: 'Employment' },
  { value: 'REFERENCE', label: 'Reference' },
  { value: 'CRIMINAL', label: 'Criminal record' },
  { value: 'CREDIT', label: 'Credit' },
  { value: 'LICENSE', label: 'License' },
  { value: 'WORK_AUTHORIZATION', label: 'Work authorization' },
  { value: 'MEDICAL', label: 'Medical / fitness' },
]

const STATUS_COLOR: Record<CheckStatus, string> = {
  NOT_REQUIRED: 'default',
  REQUIRED: 'gold',
  REQUESTED: 'blue',
  IN_PROGRESS: 'blue',
  COMPLETED: 'cyan',
  PASSED: 'green',
  FAILED: 'red',
  REQUIRES_REVIEW: 'orange',
  CANCELLED: 'default',
}

const RESULT_COLOR: Record<CheckResult, string> = {
  PASS: 'green',
  FAIL: 'red',
  CONDITIONAL: 'orange',
}

export function PreHireChecksPanel({
  applicationId,
  canEdit,
}: {
  applicationId: string
  canEdit: boolean
}) {
  const { message } = AntdApp.useApp()
  const [rows, setRows] = useState<PreHireCheck[]>([])
  const [addForm] = Form.useForm<PreHireCheckRequest>()

  const load = useCallback(() => {
    recruitmentApi
      .checksForApplication(applicationId)
      .then(setRows)
      .catch(() => setRows([]))
  }, [applicationId])

  useEffect(load, [load])

  const add = async () => {
    const v = await addForm.validateFields()
    try {
      await recruitmentApi.createCheck(applicationId, v)
      addForm.resetFields()
      message.success('Check added')
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed',
      )
    }
  }

  const transition = async (
    c: PreHireCheck,
    status: CheckStatus,
    result?: CheckResult,
  ) => {
    let resultNotes: string | undefined
    if (status === 'FAILED' || status === 'PASSED' || result) {
      resultNotes =
        window.prompt(
          c.checkType === 'MEDICAL'
            ? 'Result note (confidential — visible to HR admins only):'
            : 'Result note (optional):',
        ) ?? undefined
    }
    try {
      await recruitmentApi.updateCheck(c.id, { status, result, resultNotes })
      message.success(`Check ${status}`)
      load()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Failed',
      )
    }
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Table<PreHireCheck>
        rowKey="id"
        size="small"
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: 'No pre-hire checks yet' }}
        columns={[
          { title: '#', dataIndex: 'checkNo', width: 100 },
          {
            title: 'Type',
            dataIndex: 'checkType',
            width: 120,
            render: (t: CheckType) => (
              <Space size={4}>
                {t.replace(/_/g, ' ')}
                {t === 'MEDICAL' && (
                  <Tooltip title="Confidential — result detail visible to HR admins only">
                    <Tag color="purple">🔒</Tag>
                  </Tooltip>
                )}
              </Space>
            ),
          },
          { title: 'Provider', dataIndex: 'provider', ellipsis: true, render: (p?: string) => p ?? '—' },
          {
            title: 'Status',
            dataIndex: 'status',
            width: 130,
            render: (s: CheckStatus) => <Tag color={STATUS_COLOR[s]}>{s.replace(/_/g, ' ')}</Tag>,
          },
          {
            title: 'Result',
            dataIndex: 'result',
            width: 150,
            render: (r: CheckResult | null, c: PreHireCheck) =>
              r ? (
                <Space size={4}>
                  <Tag color={RESULT_COLOR[r]}>{r}</Tag>
                  {c.resultRedacted ? (
                    <Tooltip title="Detail hidden (confidential)">
                      <span style={{ color: '#999' }}>🔒</span>
                    </Tooltip>
                  ) : (
                    c.resultNotes && (
                      <Tooltip title={c.resultNotes}>
                        <span style={{ color: '#999', cursor: 'help' }}>note</span>
                      </Tooltip>
                    )
                  )}
                </Space>
              ) : (
                '—'
              ),
          },
          {
            title: 'Blocks hire',
            dataIndex: 'blocksHire',
            width: 90,
            render: (b: boolean) => (b ? <Tag color="volcano">Yes</Tag> : 'No'),
          },
          ...(canEdit
            ? [
                {
                  title: 'Actions',
                  width: 230,
                  render: (_: unknown, c: PreHireCheck) => {
                    const terminal = ['PASSED', 'FAILED', 'CANCELLED'].includes(c.status)
                    if (terminal) return <span style={{ color: '#bbb' }}>—</span>
                    return (
                      <Space size={4} wrap>
                        {c.status === 'REQUIRED' && (
                          <Button size="small" onClick={() => transition(c, 'REQUESTED')}>
                            Request
                          </Button>
                        )}
                        <Button
                          size="small"
                          onClick={() => transition(c, 'PASSED', 'PASS')}
                        >
                          Pass
                        </Button>
                        <Button
                          size="small"
                          danger
                          onClick={() => transition(c, 'FAILED', 'FAIL')}
                        >
                          Fail
                        </Button>
                      </Space>
                    )
                  },
                },
              ]
            : []),
        ]}
      />
      {canEdit && (
        <Form form={addForm} layout="inline" onFinish={add}>
          <Form.Item name="checkType" rules={[{ required: true, message: 'Type' }]}>
            <Select placeholder="Check type" options={TYPE_OPTIONS} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="provider">
            <Input placeholder="Provider (optional)" style={{ width: 180 }} />
          </Form.Item>
          <Form.Item>
            <Button htmlType="submit">Add check</Button>
          </Form.Item>
        </Form>
      )}
    </Space>
  )
}
