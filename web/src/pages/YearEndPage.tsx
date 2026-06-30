import { useState } from 'react'
import {
  Button,
  Card,
  Divider,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd'
import { DownloadOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import {
  payrollApi,
  type AnnualTaxCertificate,
  type CertificateStatus,
} from '../api/payroll'
import { useAuth } from '../auth/AuthContext'
import { RoleSets } from '../auth/roleSets'

const CERT_STATUS_COLOR: Record<CertificateStatus, string> = {
  DRAFT: 'default',
  GENERATED: 'blue',
  DELIVERED: 'green',
}

export function YearEndPage() {
  const { hasRole } = useAuth()
  const { message } = AntdApp.useApp()
  const canWrite = hasRole(...RoleSets.PAYROLL_WRITE)

  const currentYear = dayjs().year()
  const years = Array.from({ length: 10 }, (_, i) => currentYear - i)

  const [year, setYear] = useState(currentYear)
  const [certificates, setCertificates] = useState<AnnualTaxCertificate[]>([])
  const [loading, setLoading] = useState(false)

  const loadCertificates = () => {
    setLoading(true)
    payrollApi
      .taxCertificates(year)
      .then(setCertificates)
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load certificates'),
      )
      .finally(() => setLoading(false))
  }

  const handleGenerateSummaries = async () => {
    try {
      await payrollApi.generateYearEndSummary(year)
      message.success(`Annual summaries generated for ${year}`)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generate summaries failed',
      )
    }
  }

  const handleGenerateCertificates = async () => {
    try {
      await payrollApi.generateTaxCertificates(year)
      message.success(`Tax certificates generated for ${year}`)
      loadCertificates()
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Generate certificates failed',
      )
    }
  }

  const handleDownload = async (cert: AnnualTaxCertificate) => {
    try {
      await payrollApi.downloadTaxCertificate(cert.id, cert.employeeNo, cert.year)
      message.success('Certificate downloaded')
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Download failed',
      )
    }
  }

  const columns: ColumnsType<AnnualTaxCertificate> = [
    {
      title: 'Employee',
      render: (_, r) => `${r.employeeNo} - ${r.employeeName}`,
    },
    {
      title: 'Annual Gross',
      dataIndex: 'annualGross',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Exempt',
      dataIndex: 'exemptAmount',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Taxable',
      dataIndex: 'taxableGross',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Tax Withheld',
      dataIndex: 'totalTaxWithheld',
      align: 'right',
      render: (v: number) => `${v.toFixed(2)} AZN`,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: CertificateStatus) => <Tag color={CERT_STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: 'Download',
      width: 100,
      render: (_, r) =>
        r.status === 'GENERATED' || r.status === 'DELIVERED' ? (
          <Button
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(r)}
          />
        ) : null,
    },
  ]

  return (
    <Card title={<Typography.Title level={4} style={{ margin: 0 }}>Year-End Processing</Typography.Title>}>
      <Space style={{ marginBottom: 16 }}>
        <Typography.Text>Year:</Typography.Text>
        <Select
          value={year}
          onChange={setYear}
          style={{ width: 120 }}
          options={years.map((y) => ({ value: y, label: y }))}
        />
      </Space>

      <Divider orientation="left">Annual Summaries</Divider>
      {canWrite && (
        <Button type="primary" onClick={handleGenerateSummaries} style={{ marginBottom: 16 }}>
          Generate Summaries
        </Button>
      )}
      <Typography.Paragraph type="secondary">
        Generate annual payroll summaries for all employees with paid runs in {year}.
      </Typography.Paragraph>

      <Divider orientation="left">Tax Certificates</Divider>
      <Space style={{ marginBottom: 16 }}>
        {canWrite && (
          <Button type="primary" onClick={handleGenerateCertificates}>
            Generate All Certificates
          </Button>
        )}
        <Button onClick={loadCertificates}>Load Certificates</Button>
      </Space>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={certificates}
        loading={loading}
        pagination={false}
      />
    </Card>
  )
}
