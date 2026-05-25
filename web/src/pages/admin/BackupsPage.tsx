import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Button,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  App,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { CloudServerOutlined, SafetyOutlined, SyncOutlined } from '@ant-design/icons'
import { listBackups, triggerBackup, verifyBackup, BackupEntry } from '../../api/backupApi'

const { Title } = Typography

// ─── helpers ─────────────────────────────────────────────────────────────────

function formatBytes(bytes: number | null): string {
  if (bytes === null || bytes === undefined) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function truncateKey(key: string | null, maxLen = 30): string {
  if (!key) return '—'
  if (key.length <= maxLen) return key
  return '…' + key.slice(-maxLen)
}

type Status = BackupEntry['status']

const STATUS_COLOR: Record<Status, string> = {
  RUNNING:  'processing',
  SUCCESS:  'success',
  FAILED:   'error',
  VERIFIED: 'cyan',
}

// ─── component ───────────────────────────────────────────────────────────────

export function BackupsPage() {
  const { message: msg, notification } = App.useApp()
  const [entries, setEntries]         = useState<BackupEntry[]>([])
  const [loading, setLoading]         = useState(false)
  const [triggering, setTriggering]   = useState(false)
  const [verifying, setVerifying]     = useState<string | null>(null)
  const intervalRef                   = useRef<ReturnType<typeof setInterval> | null>(null)

  const hasRunning = entries.some((e) => e.status === 'RUNNING')

  const fetchEntries = useCallback(async () => {
    try {
      const data = await listBackups()
      setEntries(data)
    } catch {
      // silent — don't spam error toasts during polling
    }
  }, [])

  // Initial load
  useEffect(() => {
    setLoading(true)
    fetchEntries().finally(() => setLoading(false))
  }, [fetchEntries])

  // Auto-refresh every 10 s while any backup is RUNNING
  useEffect(() => {
    if (hasRunning) {
      intervalRef.current = setInterval(fetchEntries, 10_000)
    } else {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current)
    }
  }, [hasRunning, fetchEntries])

  const handleTrigger = async () => {
    setTriggering(true)
    try {
      const result = await triggerBackup()
      setEntries((prev) => [result, ...prev.slice(0, 19)])
      if (result.status === 'SUCCESS') {
        msg.success('Backup completed successfully')
      } else {
        notification.error({
          message: 'Backup failed',
          description: result.errorMessage ?? 'Unknown error',
          duration: 0,
        })
      }
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message
      msg.error('Failed to trigger backup: ' + (detail ?? 'unknown error'))
    } finally {
      setTriggering(false)
    }
  }

  const handleVerify = async (id: string) => {
    setVerifying(id)
    try {
      const result = await verifyBackup(id)
      setEntries((prev) => prev.map((e) => (e.id === id ? result : e)))
      msg.success('Backup verified successfully')
    } catch (err: unknown) {
      const detail = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message
      msg.error('Verification failed: ' + (detail ?? 'unknown error'))
    } finally {
      setVerifying(null)
    }
  }

  const columns: ColumnsType<BackupEntry> = [
    {
      title: 'Started',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v: string) => new Date(v).toLocaleString(),
      width: 180,
    },
    {
      title: 'Finished',
      dataIndex: 'finishedAt',
      key: 'finishedAt',
      render: (v: string | null) => (v ? new Date(v).toLocaleString() : '—'),
      width: 180,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: Status) => (
        <Tag
          color={STATUS_COLOR[s]}
          icon={s === 'RUNNING' ? <SyncOutlined spin /> : undefined}
        >
          {s}
        </Tag>
      ),
    },
    {
      title: 'Size',
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      width: 100,
      render: (v: number | null) => formatBytes(v),
    },
    {
      title: 'Object key',
      dataIndex: 'objectKey',
      key: 'objectKey',
      render: (v: string | null) =>
        v ? (
          <Tooltip title={v}>
            <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
              {truncateKey(v)}
            </span>
          </Tooltip>
        ) : (
          '—'
        ),
    },
    {
      title: 'Triggered by',
      dataIndex: 'triggeredBy',
      key: 'triggeredBy',
      width: 120,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: BackupEntry) =>
        record.status === 'SUCCESS' ? (
          <Button
            size="small"
            icon={<SafetyOutlined />}
            loading={verifying === record.id}
            onClick={() => handleVerify(record.id)}
          >
            Verify
          </Button>
        ) : null,
    },
  ]

  return (
    <App>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Title level={3} style={{ margin: 0 }}>
            <CloudServerOutlined style={{ marginRight: 8 }} />
            Database Backups
          </Title>
          <Button
            type="primary"
            loading={triggering}
            disabled={hasRunning}
            onClick={handleTrigger}
          >
            Trigger Backup
          </Button>
        </Space>

        <Table<BackupEntry>
          rowKey="id"
          columns={columns}
          dataSource={entries}
          loading={loading}
          pagination={false}
          size="small"
          expandable={{
            rowExpandable: (r) => !!r.errorMessage,
            expandedRowRender: (r) => (
              <Typography.Text type="danger" style={{ whiteSpace: 'pre-wrap' }}>
                {r.errorMessage}
              </Typography.Text>
            ),
          }}
        />
      </Space>
    </App>
  )
}
