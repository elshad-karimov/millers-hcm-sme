// M139 — public letter verification surface (no auth).
//
// Reached by scanning the QR code printed on the PDF. Returns only
// non-PII fields so third parties (banks, embassies, landlords) can
// confirm that a letter is genuine without ever seeing the employee's
// personal data.

import { useEffect, useState } from 'react'
import { Alert, Card, Descriptions, Result, Spin, Typography } from 'antd'
import { useParams } from 'react-router-dom'
import { publicLetterApi, type LetterVerifyResponse } from '../api/letters'

const { Title, Paragraph } = Typography

export function PublicLetterVerifyPage() {
  const { token } = useParams<{ token: string }>()
  const [data, setData] = useState<LetterVerifyResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!token) {
      setError('Missing verification token')
      setLoading(false)
      return
    }
    publicLetterApi.verify(token)
      .then(setData)
      .catch((e) => setError(
        e?.response?.data?.message ?? 'Letter not found or token invalid'))
      .finally(() => setLoading(false))
  }, [token])

  if (loading) return <Spin />

  if (error || !data) {
    return (
      <Result
        status="error"
        title="Verification failed"
        subTitle={error ?? 'Unknown error'}
      />
    )
  }

  return (
    <div style={{ maxWidth: 640, margin: '40px auto', padding: 16 }}>
      <Title level={2}>Letter verification</Title>
      <Paragraph type="secondary">
        Scanning the QR code on a Millers HCM letter confirms its authenticity.
        This page contains no personal data — just enough to verify the letter
        was issued by the organisation.
      </Paragraph>

      <Card>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="Request number">
            <code>{data.requestNo}</code>
          </Descriptions.Item>
          <Descriptions.Item label="Status">
            {data.status === 'ISSUED'
              ? <span style={{ color: '#52c41a' }}>✓ ISSUED</span>
              : <span style={{ color: '#fa541c' }}>{data.status}</span>}
          </Descriptions.Item>
          <Descriptions.Item label="Issued">
            {data.issuedDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Signed by">
            {data.signedBy ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Language">
            {data.language ?? '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {data.status !== 'ISSUED' && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 16 }}
          message="This letter is not currently issued"
          description="It may have been rejected, cancelled, or revoked."
        />
      )}
    </div>
  )
}
