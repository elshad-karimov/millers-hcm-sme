import { useCallback, useEffect, useState } from 'react'
import {
  App,
  Button,
  Card,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import {
  BarChartOutlined,
  CopyOutlined,
  DownloadOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import {
  BI_ENTITIES,
  BiEntity,
  BiExportLogEntry,
  downloadCsv,
  getODataUrl,
  listExportLog,
} from '../../api/biApi'

const { Title, Text } = Typography

// ─── helpers ─────────────────────────────────────────────────────────────────

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}

// ─── component ───────────────────────────────────────────────────────────────

export function BiExportPage() {
  const { message: msg } = App.useApp()

  const [log, setLog] = useState<BiExportLogEntry[]>([])
  const [logLoading, setLogLoading] = useState(false)
  const [downloading, setDownloading] = useState<string | null>(null)

  const fetchLog = useCallback(async () => {
    setLogLoading(true)
    try {
      const entries = await listExportLog()
      setLog(entries)
    } catch {
      msg.error('Failed to load export history')
    } finally {
      setLogLoading(false)
    }
  }, [msg])

  useEffect(() => {
    fetchLog()
  }, [fetchLog])

  // ── handlers ──────────────────────────────────────────────────────────────

  const handleCopyOData = (entity: string) => {
    const url = getODataUrl(entity)
    navigator.clipboard.writeText(url).then(
      () => msg.success(`OData URL copied to clipboard`),
      () => msg.error('Failed to copy URL'),
    )
  }

  const handleDownloadCsv = async (entity: string) => {
    setDownloading(entity)
    try {
      await downloadCsv(entity)
      msg.success(`${entity}.csv downloaded`)
      // Refresh log after a short delay to show the new entry
      setTimeout(fetchLog, 800)
    } catch (err: unknown) {
      const detail = (err as Error)?.message
      msg.error('Download failed: ' + (detail ?? 'unknown error'))
    } finally {
      setDownloading(null)
    }
  }

  // ── entity table ──────────────────────────────────────────────────────────

  const entityColumns: ColumnsType<BiEntity> = [
    {
      title: 'Entity',
      dataIndex: 'label',
      key: 'label',
      render: (label: string) => <Text strong>{label}</Text>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (desc: string) => <Text type="secondary">{desc}</Text>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 260,
      render: (_: unknown, record: BiEntity) => (
        <Space>
          <Button
            icon={<DownloadOutlined />}
            size="small"
            loading={downloading === record.key}
            disabled={downloading !== null && downloading !== record.key}
            onClick={() => handleDownloadCsv(record.key)}
          >
            Export CSV
          </Button>
          <Tooltip title={getODataUrl(record.key)}>
            <Button
              icon={<CopyOutlined />}
              size="small"
              onClick={() => handleCopyOData(record.key)}
            >
              Copy OData URL
            </Button>
          </Tooltip>
        </Space>
      ),
    },
  ]

  // ── log table ─────────────────────────────────────────────────────────────

  const logColumns: ColumnsType<BiExportLogEntry> = [
    {
      title: 'Exported at',
      dataIndex: 'exportedAt',
      key: 'exportedAt',
      render: formatDate,
      width: 180,
    },
    {
      title: 'Entity',
      dataIndex: 'entity',
      key: 'entity',
    },
    {
      title: 'Format',
      dataIndex: 'format',
      key: 'format',
      render: (fmt: string) => (
        <Tag color={fmt === 'ODATA' ? 'blue' : 'green'}>{fmt}</Tag>
      ),
      width: 90,
    },
    {
      title: 'Rows',
      dataIndex: 'rowCount',
      key: 'rowCount',
      width: 80,
      align: 'right',
    },
    {
      title: 'Requested by',
      dataIndex: 'requestedBy',
      key: 'requestedBy',
    },
  ]

  // ── render ────────────────────────────────────────────────────────────────

  return (
    <App>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        {/* ── Page header ─────────────────────────────────────────── */}
        <Title level={3} style={{ margin: 0 }}>
          <BarChartOutlined style={{ marginRight: 8 }} />
          BI Export / Power BI
        </Title>

        {/* ── How to connect card ──────────────────────────────────── */}
        <Card title="How to connect">
          <Space direction="vertical">
            <Text>
              <strong>Power BI OData connector:</strong> In Power BI Desktop, choose
              Get Data → OData Feed and paste the OData URL for the entity you need.
              Authenticate with your HCM credentials.
            </Text>
            <Text>
              <strong>Excel Get Data → From Web:</strong> Use the CSV download to get
              a flat file, or paste the OData URL into Excel's Web connector.
            </Text>
            <Text type="secondary">
              Both endpoints require the SYSTEM_ADMIN or AUDITOR role. Every export is
              logged in the audit table below.
            </Text>
          </Space>
        </Card>

        {/* ── Entities table ───────────────────────────────────────── */}
        <Card title="Available entities">
          <Table<BiEntity>
            dataSource={BI_ENTITIES}
            columns={entityColumns}
            rowKey="key"
            pagination={false}
            size="middle"
          />
        </Card>

        {/* ── Export log ───────────────────────────────────────────── */}
        <Card
          title="Export history (last 50)"
          extra={
            <Button size="small" onClick={fetchLog} loading={logLoading}>
              Refresh
            </Button>
          }
        >
          <Table<BiExportLogEntry>
            dataSource={log}
            columns={logColumns}
            rowKey="id"
            loading={logLoading}
            pagination={{ pageSize: 20, showSizeChanger: false }}
            size="small"
          />
        </Card>
      </Space>
    </App>
  )
}
