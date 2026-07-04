// HCM_20 M426 — Payroll cost forecast page.

import { useEffect, useState } from 'react'
import { App as AntdApp, Card, Form, InputNumber, Button, Space } from 'antd'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import { api } from '../api/client'

interface MonthlyForecast {
  month: string
  forecastCost: number
  cumulativeHires: number
  cumulativeExits: number
  monthlyGrowth: number
}

export function BudgetForecastPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm()
  const [forecasts, setForecasts] = useState<MonthlyForecast[]>([])
  const [loading, setLoading] = useState(false)

  const onGenerate = async () => {
    const values = await form.validateFields()
    setLoading(true)
    api
      .get<MonthlyForecast[]>('/budgets/forecast/payroll', {
        params: {
          months: values.months || 12,
          growthPct: values.growthPct || 5,
        },
      })
      .then((r) => {
        setForecasts(r.data)
        message.success('Forecast generated')
      })
      .catch((err) => message.error(err?.response?.data?.message ?? 'Forecast failed'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    form.setFieldsValue({ months: 12, growthPct: 5 })
    onGenerate()
  }, [])

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card title="Payroll Cost Forecast" size="small">
        <Form form={form} layout="inline">
          <Form.Item name="months" label="Months">
            <InputNumber min={1} max={120} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="growthPct" label="Annual growth %">
            <InputNumber min={0} max={50} step={0.5} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" loading={loading} onClick={onGenerate}>
              Generate
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {forecasts.length > 0 && (
        <Card size="small">
          <ResponsiveContainer width="100%" height={400}>
            <LineChart data={forecasts}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="month" />
              <YAxis />
              <Tooltip />
              <Legend />
              <Line type="monotone" dataKey="forecastCost" stroke="#1890ff" name="Forecast cost" />
              <Line type="monotone" dataKey="monthlyGrowth" stroke="#52c41a" name="Monthly growth" />
            </LineChart>
          </ResponsiveContainer>
        </Card>
      )}
    </Space>
  )
}
