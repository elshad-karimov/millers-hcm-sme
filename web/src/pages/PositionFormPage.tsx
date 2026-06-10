import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Collapse,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Typography,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { positionsApi, type Position, type PositionRequest } from '../api/positions'
import { FormPageShell } from '../components/FormPageShell'
import { PositionLifecyclePanel } from '../components/PositionLifecyclePanel'
import { PositionBudgetFundingPanel } from '../components/PositionBudgetFundingPanel'
import { PositionOccupancyPanel } from '../components/PositionOccupancyPanel'
import { PositionProfilePanel } from '../components/PositionProfilePanel'
import { PositionTransferPanel } from '../components/PositionTransferPanel'

interface FormValues {
  title: string
  parentPositionId?: string
  orgUnitLabel?: string
  grade?: string
  jobFamily?: string
  jobLevel?: string
  approvedHeadcount: number
  salaryMin?: number
  salaryMax?: number
  currency?: string
  employmentType?: string
  costCentre?: string
  budgetCode?: string
  location?: string
  // M254 — compliance (PRD §44)
  establishmentNumber?: string
  civilServiceGrade?: string
  unionCategory?: string
  exemptStatus?: string
  occupationalCategory?: string
  laborClassification?: string
  legalBasisReference?: string
  // M256 — risk & criticality (PRD §31)
  criticalFlag?: boolean
  businessImpactScore?: number
  riskCategory?: string
  keySkillConcentration?: boolean
  successorRequired?: boolean
  effectiveFrom?: dayjs.Dayjs
  effectiveTo?: dayjs.Dayjs
}

const LIST_PATH = '/positions'

export function PositionFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState(editing)
  const [saving, setSaving] = useState(false)
  // M243 — kept in sync with the loaded position so the lifecycle panel
  // re-renders after a transition without re-fetching the whole form.
  const [current, setCurrent] = useState<Position | null>(null)
  const [positionOptions, setPositionOptions] = useState<{ value: string; label: string }[]>([])

  useEffect(() => {
    positionsApi.list({ size: 500 }).then((r) => {
      setPositionOptions(r.content.map((p) => ({ value: p.id, label: `${p.code} — ${p.title}` })))
    })
  }, [])

  useEffect(() => {
    if (!editing) {
      form.setFieldsValue({ currency: 'AZN', approvedHeadcount: 1 })
      return
    }
    setLoading(true)
    positionsApi
      .get(id!)
      .then((p: Position) => {
        setCurrent(p)
        form.setFieldsValue({
          title: p.title,
          parentPositionId: p.parentPositionId ?? undefined,
          orgUnitLabel: p.orgUnitLabel ?? undefined,
          grade: p.grade ?? undefined,
          jobFamily: p.jobFamily ?? undefined,
          jobLevel: p.jobLevel ?? undefined,
          approvedHeadcount: p.approvedHeadcount,
          salaryMin: p.salaryMin ?? undefined,
          salaryMax: p.salaryMax ?? undefined,
          currency: p.currency,
          employmentType: p.employmentType ?? undefined,
          costCentre: p.costCentre ?? undefined,
          budgetCode: p.budgetCode ?? undefined,
          location: p.location ?? undefined,
          // M254 — prefill compliance fields when editing an existing position
          establishmentNumber: p.establishmentNumber ?? undefined,
          civilServiceGrade: p.civilServiceGrade ?? undefined,
          unionCategory: p.unionCategory ?? undefined,
          exemptStatus: p.exemptStatus ?? undefined,
          occupationalCategory: p.occupationalCategory ?? undefined,
          laborClassification: p.laborClassification ?? undefined,
          legalBasisReference: p.legalBasisReference ?? undefined,
          // M256 — prefill risk & criticality
          criticalFlag: p.criticalFlag,
          businessImpactScore: p.businessImpactScore ?? undefined,
          riskCategory: p.riskCategory ?? undefined,
          keySkillConcentration: p.keySkillConcentration,
          successorRequired: p.successorRequired,
          effectiveFrom: p.effectiveFrom ? dayjs(p.effectiveFrom) : undefined,
          effectiveTo: p.effectiveTo ? dayjs(p.effectiveTo) : undefined,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load position'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload: PositionRequest = {
      ...v,
      effectiveFrom: v.effectiveFrom?.format('YYYY-MM-DD'),
      effectiveTo: v.effectiveTo?.format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await positionsApi.update(id!, payload)
        message.success('Position updated')
      } else {
        await positionsApi.create(payload)
        message.success('Position created')
      }
      navigate(LIST_PATH)
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
    <FormPageShell title={editing ? 'Edit position' : 'New position'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <>
          {editing && current && (
            <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 6 }}>
              <PositionLifecyclePanel
                position={current}
                onChange={(updated) => setCurrent(updated)}
              />
            </div>
          )}
          {editing && current && (
            <div style={{ marginBottom: 16 }}>
              <PositionBudgetFundingPanel
                positionId={current.id}
                defaultCurrency={current.currency}
              />
            </div>
          )}
          {editing && current && (
            <div style={{ marginBottom: 16 }}>
              <PositionOccupancyPanel positionId={current.id} />
            </div>
          )}
          {editing && current && (
            <div style={{ marginBottom: 16 }}>
              <PositionProfilePanel positionId={current.id} />
            </div>
          )}
          {/* M260 — Position transfer workflow (PRD §40). Embedded only
              on edit so a brand-new position can be saved before a
              transfer is initiated against it. */}
          {editing && current && (
            <Card
              size="small"
              title={
                <span>📦 Transfers <Typography.Text type="secondary" style={{ fontSize: 12 }}>(PRD §40)</Typography.Text></span>
              }
              style={{ marginBottom: 16 }}
            >
              <PositionTransferPanel positionId={current.id} />
            </Card>
          )}
          <Form form={form} layout="vertical" onFinish={onFinish} style={{ maxWidth: 760 }}>
          <Form.Item name="title" label="Title" rules={[{ required: true, max: 200 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentPositionId" label="Parent position">
            <Select
              allowClear
              showSearch
              placeholder="None (root position)"
              optionFilterProp="label"
              options={positionOptions.filter((o) => o.value !== id)}
            />
          </Form.Item>
          <Form.Item name="orgUnitLabel" label="Department / Unit" rules={[{ max: 200 }]}>
            <Input placeholder="e.g. Engineering · Platform" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item
                name="approvedHeadcount"
                label="Approved headcount"
                rules={[{ required: true }]}
              >
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="grade" label="Grade" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="jobLevel" label="Job level" rules={[{ max: 32 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="jobFamily" label="Job family" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="employmentType" label="Employment type" rules={[{ max: 32 }]}>
                <Input placeholder="FULL_TIME / PART_TIME / CONTRACT" />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="salaryMin" label="Salary min">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="salaryMax" label="Salary max">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="currency" label="Currency">
                <Input maxLength={3} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="budgetCode" label="Budget code" rules={[{ max: 64 }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="location" label="Location" rules={[{ max: 160 }]}>
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="effectiveFrom" label="Effective from">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="effectiveTo" label="Effective to">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          {/* M254 — PRD §44 compliance / regulatory fields.
              Tucked into a Collapse because most deployments won't need
              them; gov-sector pitches expand on demand. */}
          <Collapse
            ghost
            items={[
              {
                key: 'compliance',
                label: '🏛️ Compliance & regulatory (PRD §44)',
                children: (
                  <>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item
                          name="establishmentNumber"
                          label="Establishment number"
                          tooltip="Government registration / establishment id"
                          rules={[{ max: 64 }]}
                        >
                          <Input placeholder="e.g. EST-2024-00123" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item
                          name="civilServiceGrade"
                          label="Civil service grade"
                          rules={[{ max: 32 }]}
                        >
                          <Input placeholder="e.g. CS-A5" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item
                          name="unionCategory"
                          label="Union / labor category"
                          rules={[{ max: 64 }]}
                        >
                          <Input placeholder="e.g. PUBLIC_SERVANT_CAT_II" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item
                          name="exemptStatus"
                          label="Exempt status"
                          tooltip="Overtime eligibility under labor act"
                        >
                          <Select
                            allowClear
                            options={[
                              { value: 'EXEMPT', label: 'Exempt' },
                              { value: 'NON_EXEMPT', label: 'Non-exempt' },
                              { value: 'SEMI_EXEMPT', label: 'Semi-exempt' },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item
                          name="occupationalCategory"
                          label="Occupational category"
                          tooltip="ISCO / national occupational classification code"
                          rules={[{ max: 32 }]}
                        >
                          <Input placeholder="e.g. ISCO-2411" />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item
                          name="laborClassification"
                          label="Labor classification"
                          rules={[{ max: 64 }]}
                        >
                          <Input placeholder="e.g. LABOR-ACT-2018-CLS-A" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Form.Item
                      name="legalBasisReference"
                      label="Legal basis reference"
                      tooltip="Appointment authority / legal act / order # that authorises this position"
                      rules={[{ max: 200 }]}
                    >
                      <Input placeholder="e.g. Cabinet Decree #245 / 12-Mar-2024" />
                    </Form.Item>
                  </>
                ),
              },
              // M256 — PRD §31 risk & criticality flags. Hidden by
              // default in another Collapse — operator opens when
              // flagging critical or hard-to-replace positions.
              {
                key: 'risk',
                label: '🔴 Risk & criticality (PRD §31)',
                children: (
                  <>
                    <Row gutter={16}>
                      <Col span={8}>
                        <Form.Item
                          name="criticalFlag"
                          label="Critical position"
                          valuePropName="checked"
                          tooltip="Position is business-critical — vacancy triggers urgent succession review"
                        >
                          <Switch />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item
                          name="successorRequired"
                          label="Named successor required"
                          valuePropName="checked"
                          tooltip="Must have a named successor at all times"
                        >
                          <Switch />
                        </Form.Item>
                      </Col>
                      <Col span={8}>
                        <Form.Item
                          name="keySkillConcentration"
                          label="Key-skill concentration"
                          valuePropName="checked"
                          tooltip="Role depends on hard-to-replace skills"
                        >
                          <Switch />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={16}>
                      <Col span={12}>
                        <Form.Item
                          name="businessImpactScore"
                          label="Business impact score"
                          tooltip="1 (low) — 5 (extreme) impact if the position were vacant"
                        >
                          <Select
                            allowClear
                            options={[
                              { value: 1, label: '1 — Low' },
                              { value: 2, label: '2 — Moderate' },
                              { value: 3, label: '3 — High' },
                              { value: 4, label: '4 — Severe' },
                              { value: 5, label: '5 — Extreme' },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                      <Col span={12}>
                        <Form.Item
                          name="riskCategory"
                          label="Risk category"
                          tooltip="Primary risk dimension this position carries"
                        >
                          <Select
                            allowClear
                            options={[
                              { value: 'KEY_PERSON', label: 'Key Person' },
                              { value: 'REGULATORY', label: 'Regulatory' },
                              { value: 'OPERATIONAL', label: 'Operational' },
                              { value: 'SPECIALIST', label: 'Specialist Skills' },
                              { value: 'EXECUTIVE', label: 'Executive' },
                            ]}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                  </>
                ),
              },
            ]}
          />
          <Form.Item>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create position'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
        </>
      )}
    </FormPageShell>
  )
}
