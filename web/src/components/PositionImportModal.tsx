import { useState } from 'react'
import {
  Alert,
  Button,
  Modal,
  Space,
  Steps,
  Table,
  Tag,
  Typography,
  Upload,
  App as AntdApp,
} from 'antd'
import type { UploadFile } from 'antd/es/upload/interface'
import {
  DownloadOutlined,
  InboxOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import {
  positionImportApi,
  type ImportResult,
  type ImportRowResult,
} from '../api/positionImport'

/**
 * M255 — Position bulk-import wizard (PRD §46).
 *
 * 3-step wizard:
 *   1. Download template
 *   2. Upload + preview (per-row validation)
 *   3. Commit (creates all valid rows in one transaction)
 *
 * Keeps the work in a Modal rather than a dedicated page so the
 * operator never loses their place on the position list.
 */
export function PositionImportModal({
  open,
  onClose,
  onImported,
}: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const { message } = AntdApp.useApp()
  const [step, setStep] = useState(0)
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportResult | null>(null)
  const [working, setWorking] = useState(false)

  const reset = () => {
    setStep(0)
    setFile(null)
    setPreview(null)
    setWorking(false)
  }

  const close = () => {
    reset()
    onClose()
  }

  const downloadTemplate = async () => {
    try {
      const blob = await positionImportApi.downloadTemplate()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = 'positions-import-template.xlsx'
      a.click()
      URL.revokeObjectURL(url)
      message.success('Template downloaded')
    } catch (err) {
      message.error('Failed to download template')
    }
  }

  const runPreview = async () => {
    if (!file) {
      message.warning('Pick a file first')
      return
    }
    setWorking(true)
    try {
      const r = await positionImportApi.preview(file)
      setPreview(r)
      setStep(2)
      if (r.errorRows > 0) {
        message.warning(
          `${r.errorRows} of ${r.totalRows} rows have errors — review before committing`,
        )
      } else {
        message.success(`All ${r.totalRows} rows look good`)
      }
    } catch (err: any) {
      message.error(err?.response?.data?.message ?? 'Preview failed')
    } finally {
      setWorking(false)
    }
  }

  const runCommit = async () => {
    if (!file) return
    setWorking(true)
    try {
      const r = await positionImportApi.commit(file)
      message.success(`Imported ${r.totalRows} positions`)
      onImported()
      close()
    } catch (err: any) {
      message.error(err?.response?.data?.message ?? 'Commit failed')
    } finally {
      setWorking(false)
    }
  }

  // ── Per-row table columns ──
  const columns = [
    {
      title: '#',
      dataIndex: 'rowNumber',
      width: 60,
    },
    {
      title: 'Status',
      key: 'status',
      width: 100,
      render: (_: unknown, row: ImportRowResult) =>
        row.errors.length === 0 ? (
          <Tag color="green">OK</Tag>
        ) : (
          <Tag color="red">{row.errors.length} error</Tag>
        ),
    },
    {
      title: 'Code (ref)',
      dataIndex: 'referenceCode',
      width: 110,
      render: (v: string | null) =>
        v ? <code style={{ fontSize: 11 }}>{v}</code> : <span>—</span>,
    },
    { title: 'Title', dataIndex: 'title', ellipsis: true },
    {
      title: 'Org Unit',
      dataIndex: 'orgUnitLabel',
      width: 130,
      ellipsis: true,
    },
    {
      title: 'HC',
      dataIndex: 'approvedHeadcount',
      width: 60,
    },
    {
      title: 'Salary',
      key: 'salary',
      width: 150,
      render: (_: unknown, row: ImportRowResult) => {
        if (!row.salaryMin && !row.salaryMax) return <span>—</span>
        return (
          <span style={{ fontSize: 12 }}>
            {row.salaryMin ?? '?'} – {row.salaryMax ?? '?'} {row.currency}
          </span>
        )
      },
    },
    {
      title: 'Errors',
      dataIndex: 'errors',
      render: (errs: string[]) =>
        errs.length === 0 ? (
          <span style={{ color: '#52c41a' }}>—</span>
        ) : (
          <Space direction="vertical" size={2}>
            {errs.map((e, i) => (
              <Typography.Text key={i} type="danger" style={{ fontSize: 12 }}>
                • {e}
              </Typography.Text>
            ))}
          </Space>
        ),
    },
  ]

  return (
    <Modal
      title="📥 Bulk import positions"
      open={open}
      onCancel={close}
      width={1100}
      footer={null}
      destroyOnClose
    >
      <Steps
        size="small"
        current={step}
        items={[
          { title: 'Download template' },
          { title: 'Upload file' },
          { title: 'Preview & commit' },
        ]}
        style={{ marginBottom: 24 }}
      />

      {step === 0 && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Alert
            type="info"
            showIcon
            message="Step 1 — Download the Excel template"
            description="The template ships with 3 sample rows and column hints. Fill in your positions, delete the yellow sample rows, save and re-upload here."
          />
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={downloadTemplate}
            size="large"
          >
            Download positions-import-template.xlsx
          </Button>
          <div style={{ marginTop: 16 }}>
            <Button onClick={() => setStep(1)}>I have the template →</Button>
          </div>
        </Space>
      )}

      {step === 1 && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Alert
            type="info"
            showIcon
            message="Step 2 — Upload your filled-in file"
            description="The system will validate every row and show you any errors before any rows are created."
          />
          <Upload.Dragger
            accept=".xlsx"
            multiple={false}
            maxCount={1}
            beforeUpload={(f) => {
              setFile(f as File)
              return false  // we handle the upload via /preview ourselves
            }}
            onRemove={() => setFile(null)}
            fileList={
              file
                ? [
                    {
                      uid: '1',
                      name: file.name,
                      status: 'done',
                    } as UploadFile,
                  ]
                : []
            }
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Click or drag .xlsx file here</p>
            <p className="ant-upload-hint" style={{ fontSize: 12 }}>
              Use the template downloaded in step 1. Headers must match
              exactly.
            </p>
          </Upload.Dragger>
          <Space>
            <Button onClick={() => setStep(0)}>← Back</Button>
            <Button
              type="primary"
              icon={<UploadOutlined />}
              loading={working}
              disabled={!file}
              onClick={runPreview}
            >
              Validate file
            </Button>
          </Space>
        </Space>
      )}

      {step === 2 && preview && (
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Alert
            type={preview.errorRows === 0 ? 'success' : 'warning'}
            showIcon
            message={
              preview.errorRows === 0
                ? `✓ All ${preview.totalRows} rows are valid — ready to commit`
                : `⚠ ${preview.errorRows} of ${preview.totalRows} rows have errors`
            }
            description={
              preview.errorRows === 0
                ? 'Click Commit to create all positions in a single transaction.'
                : `Fix the errors in your Excel file (column "Errors" shows what's wrong) and re-upload. The commit will refuse to run while any row is invalid.`
            }
          />
          <Table
            size="small"
            rowKey="rowNumber"
            dataSource={preview.rows}
            columns={columns}
            pagination={{ pageSize: 20, showSizeChanger: false }}
            rowClassName={(row) =>
              row.errors.length > 0 ? 'row-error' : 'row-ok'
            }
            scroll={{ x: 900 }}
          />
          <Space>
            <Button onClick={() => setStep(1)}>← Re-upload</Button>
            <Button
              type="primary"
              loading={working}
              disabled={preview.errorRows > 0}
              onClick={runCommit}
            >
              {preview.errorRows > 0
                ? `Fix ${preview.errorRows} error(s) first`
                : `✓ Commit ${preview.totalRows} positions`}
            </Button>
          </Space>
        </Space>
      )}
    </Modal>
  )
}
