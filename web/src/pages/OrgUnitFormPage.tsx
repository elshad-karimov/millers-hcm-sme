import { useEffect, useState } from 'react'
import {
  Button,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  App as AntdApp,
} from 'antd'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  orgApi,
  type OrgUnitRequest,
  type OrgUnitResponse,
  type OrgUnitType,
} from '../api/org'
import { locationApi, type LocationResponse } from '../api/location'
import { FormPageShell } from '../components/FormPageShell'

const UNIT_TYPES: OrgUnitType[] = [
  'COMPANY',
  'BRANCH',
  'DIVISION',
  'DEPARTMENT',
  'SECTION',
  'UNIT',
  'TEAM',
]

export function OrgUnitFormPage() {
  const { unitId } = useParams()
  const editing = !!unitId
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<OrgUnitRequest>()
  const [versionId, setVersionId] = useState<string | null>(searchParams.get('versionId'))
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  const [parentOptions, setParentOptions] = useState<{ value: string; label: string }[]>([])
  const [locationOptions, setLocationOptions] = useState<{ value: string; label: string }[]>([])

  const backPath = versionId ? `/organization?versionId=${versionId}` : '/organization'

  useEffect(() => {
    locationApi.list(true)
      .then((locs: LocationResponse[]) =>
        setLocationOptions(locs.map((l) => ({ value: l.id, label: `${l.code} — ${l.name}` }))))
      .catch(() => {/* non-critical */})
  }, [])

  useEffect(() => {
    const load = async () => {
      try {
        if (editing) {
          // Find the unit by scanning each version's units (we don't have a by-id endpoint).
          const versions = await orgApi.versions()
          let owning: OrgUnitResponse | null = null
          let owningVersionId: string | null = null
          for (const v of versions) {
            const units = await orgApi.units(v.id)
            const found = units.find((u) => u.id === unitId)
            if (found) {
              owning = found
              owningVersionId = v.id
              break
            }
          }
          if (!owning || !owningVersionId) {
            message.error('Unit not found')
            navigate('/organization')
            return
          }
          setVersionId(owningVersionId)
          const peers = (await orgApi.units(owningVersionId))
            .filter((u) => u.id !== owning!.id)
            .map((u) => ({ value: u.id, label: `${u.name} (${u.code})` }))
          setParentOptions(peers)
          form.setFieldsValue({
            code: owning.code,
            name: owning.name,
            unitType: owning.unitType,
            parentId: owning.parentId ?? undefined,
            sortOrder: owning.sortOrder,
            // M141 + M81 — extended attributes
            locationId: owning.locationId ?? undefined,
            costCentreCode: owning.costCentreCode ?? undefined,
            location: owning.location ?? undefined,
            contactEmail: owning.contactEmail ?? undefined,
            glAccount: owning.glAccount ?? undefined,
            headcountBudget: owning.headcountBudget ?? undefined,
          })
        } else {
          if (!versionId) {
            message.error('versionId is required for creating a unit')
            navigate('/organization')
            return
          }
          const peers = (await orgApi.units(versionId)).map((u) => ({
            value: u.id,
            label: `${u.name} (${u.code})`,
          }))
          setParentOptions(peers)
          const parentId = searchParams.get('parentId')
          form.setFieldsValue({
            parentId: parentId ?? undefined,
            unitType: parentId ? 'DEPARTMENT' : 'COMPANY',
            sortOrder: 0,
          })
        }
      } catch (err) {
        message.error(
          (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
            'Failed to load',
        )
      } finally {
        setLoading(false)
      }
    }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unitId, editing])

  const onFinish = async (values: OrgUnitRequest) => {
    setSaving(true)
    try {
      if (editing) {
        await orgApi.updateUnit(unitId!, values)
        message.success('Unit updated')
      } else {
        await orgApi.addUnit(versionId!, values)
        message.success('Unit added')
      }
      navigate(backPath)
    } catch (err) {
      message.error(
        (err as { response?: { data?: { message?: string } } }).response?.data?.message ??
          'Save failed',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <FormPageShell
      title={editing ? 'Edit org unit' : 'Add org unit'}
      backTo={backPath}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 560 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="code" label="Code" rules={[{ required: true, max: 64 }]}>
                <Input placeholder="e.g. HQ, FIN, ENG-EU" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="unitType" label="Type" rules={[{ required: true }]}>
                <Select options={UNIT_TYPES.map((t) => ({ value: t, label: t }))} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="name" label="Name" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentId" label="Parent unit">
            <Select
              allowClear
              placeholder="(root)"
              options={parentOptions}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="sortOrder" label="Sort order">
            <InputNumber min={0} style={{ width: 160 }} />
          </Form.Item>

          {/* M141 — structured location (replaces free-text) */}
          <Form.Item
            name="locationId"
            label="Location"
            tooltip="Select from the Location master. Provides time-zone, GPS, holiday calendar, and shift defaults."
          >
            <Select
              allowClear
              showSearch
              placeholder="— none —"
              optionFilterProp="label"
              options={locationOptions}
              style={{ maxWidth: 360 }}
            />
          </Form.Item>

          {/* M81 — finance / facilities attributes */}
          <Form.Item
            name="costCentreCode"
            label="Cost centre code"
            tooltip="Free-text code; surfaces in payroll bank-file exports + GL postings."
          >
            <Input maxLength={64} style={{ maxWidth: 280 }} />
          </Form.Item>
          <Form.Item
            name="contactEmail"
            label="Contact email"
            rules={[{ type: 'email', message: 'Must be a valid email' }]}
          >
            <Input maxLength={160} style={{ maxWidth: 280 }} />
          </Form.Item>
          <Form.Item name="glAccount" label="GL account">
            <Input maxLength={64} style={{ maxWidth: 280 }} />
          </Form.Item>
          <Form.Item name="headcountBudget" label="Headcount budget">
            <InputNumber min={0} style={{ width: 160 }} />
          </Form.Item>

          <Form.Item>
            <Space>
              <Button onClick={() => navigate(backPath)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Add unit'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
