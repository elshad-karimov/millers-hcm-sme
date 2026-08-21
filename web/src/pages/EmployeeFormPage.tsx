import { useEffect, useState } from 'react'
import {
  Alert,
  AutoComplete,
  Button,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Spin,
  Tabs,
  App as AntdApp,
} from 'antd'
import dayjs from 'dayjs'
import { useNavigate, useParams } from 'react-router-dom'
import { apiErrorDuration, apiErrorMessage } from '../api/errors'
import { CITY_OPTIONS } from '../config/cities'
import {
  CANDIDATE_SOURCES,
  GENDERS,
  JOB_DESCRIPTION_STATUSES,
  OFFSHORE_DAILY_SCHEDULES,
  POSITION_CLASSIFICATIONS,
  TIME_ACCOUNTING_METHODS,
  WORK_SCHEDULES,
  WORK_SCHEDULE_DERIVED,
} from '../config/employeeLovs'
import { COUNTRY_OPTIONS } from '../config/countries'
import { employeesApi, type Employee, type EmployeeWorkType } from '../api/employees'
import { locationApi, type LocationResponse } from '../api/location'
import { departmentsApi } from '../api/departments'
import { positionsApi } from '../api/positions'
import { EmployeePicker } from '../components/EmployeePicker'
import { FormPageShell } from '../components/FormPageShell'

interface FormValues {
  firstName: string
  lastName: string
  middleName?: string
  email?: string
  phone?: string
  gender?: string
  nationalId?: string
  birthDate?: dayjs.Dayjs
  hireDate: dayjs.Dayjs
  departmentName?: string
  orgUnitId?: string
  positionId?: string
  positionTitle?: string
  costCentre?: string
  // M141 — work location
  workLocationId?: string
  // V329 — birth place, split
  birthCountry?: string
  birthCity?: string
  birthAddress?: string
  // M133 — Section 3 contact fields
  altPhone?: string
  workEmail?: string
  workPhone?: string
  // M134 — Section 4 employment fields
  employeeCategory?: string
  seniorityDate?: dayjs.Dayjs
  // M150 — workforce-register master data
  externalHrId?: string
  fullNameLocal?: string
  sourceOfHire?: string
  positionTitleLocal?: string
  occupationClassification?: string
  positionClassification?: string
  workType?: EmployeeWorkType
  projectName?: string
  professionalExperienceYears?: number
  jobDescriptionStatus?: string
  timesheetApproverId?: string
  expenseApproverId?: string
  hrTimesheetVerifierId?: string
  workScheduleText?: string
  workTimeText?: string
  lunchTimeText?: string
  offshoreWorkScheduleText?: string
  summarizedPeriodMethod?: string
  // PRD §4 steps 4-5 — captured during the hire
  contractStartDate?: dayjs.Dayjs
  contractEndDate?: dayjs.Dayjs
  contractType?: string
  monthlyBaseSalary?: number
  salaryEffectiveFrom?: dayjs.Dayjs
}


// M150 — mirrors the EmployeeWorkType enum. Labels spell out what each one selects,
// because the choice drives which compensation rate applies downstream.
const WORK_TYPES: { value: EmployeeWorkType; label: string }[] = [
  { value: 'ONSHORE', label: 'Onshore — base / city office' },
  { value: 'OFFSHORE', label: 'Offshore — offshore rate + rotation schedule' },
  { value: 'QUAYSIDE', label: 'Quayside — yard / quayside rate' },
  { value: 'HYBRID', label: 'Hybrid — split, rate resolved per timesheet day' },
]

/**
 * M150 — which tab each field lives on. Used to jump the user to the first
 * tab carrying a validation error: a required field failing on a hidden tab
 * would otherwise silently block the save with nothing visible on screen.
 */
const FIELD_TAB: Record<string, string> = {
  firstName: 'personal',
  lastName: 'personal',
  middleName: 'personal',
  fullNameLocal: 'personal',
  gender: 'personal',
  birthDate: 'personal',
  birthCountry: 'personal',
  birthCity: 'personal',
  birthAddress: 'personal',
  nationalId: 'personal',
  email: 'personal',
  phone: 'personal',
  altPhone: 'personal',
  workEmail: 'personal',
  workPhone: 'personal',
  externalHrId: 'job',
  hireDate: 'job',
  seniorityDate: 'job',
  employeeCategory: 'job',
  departmentName: 'job',
  orgUnitId: 'job',
  positionId: 'job',
  positionTitle: 'job',
  positionTitleLocal: 'job',
  positionClassification: 'job',
  occupationClassification: 'job',
  costCentre: 'job',
  workLocationId: 'job',
  workType: 'job',
  projectName: 'job',
  professionalExperienceYears: 'job',
  sourceOfHire: 'recruitment',
  jobDescriptionStatus: 'job',
  timesheetApproverId: 'approvals',
  expenseApproverId: 'approvals',
  hrTimesheetVerifierId: 'approvals',
  workScheduleText: 'schedule',
  workTimeText: 'schedule',
  lunchTimeText: 'schedule',
  offshoreWorkScheduleText: 'schedule',
  summarizedPeriodMethod: 'schedule',
  contractStartDate: 'contract',
  contractEndDate: 'contract',
  contractType: 'contract',
  monthlyBaseSalary: 'compensation',
  salaryEffectiveFrom: 'compensation',
}

const TAB_ORDER = ['personal', 'job', 'contract', 'compensation', 'schedule', 'approvals', 'recruitment']

const LIST_PATH = '/employees'

export function EmployeeFormPage() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<FormValues>()
  const [loading, setLoading] = useState<boolean>(editing)
  const [saving, setSaving] = useState(false)
  const [activeTab, setActiveTab] = useState('personal')
  const [locationOptions, setLocationOptions] = useState<{ value: string; label: string }[]>([])
  // Master data behind the Department and Position pickers. PRD §6: both are
  // lookups against a master, never free text — the workbook held 13 spellings
  // of the department list and 89 position labels because they were typed.
  const [orgUnits, setOrgUnits] = useState<{ value: string; label: string }[]>([])
  const [positions, setPositions] = useState<{ value: string; label: string }[]>([])

  useEffect(() => {
    locationApi.list(true)
      .then((locs: LocationResponse[]) =>
        setLocationOptions(locs.map((l) => ({ value: l.id, label: `${l.code} — ${l.name}` }))))
      .catch(() => {/* non-critical */})

    // Departments come from the department master, which is the active
    // structure read flat. Maintained under Master Data > Departments.
    departmentsApi.list()
      .then((deps) =>
        setOrgUnits(deps.map((d) => ({ value: d.id, label: `${d.code} — ${d.name}` }))))
      .catch(() => {/* master unavailable — the picker just stays empty */})

    positionsApi.list({ size: 500 })
      .then((page) =>
        setPositions(page.content.map((x) => ({ value: x.id, label: `${x.code} — ${x.title}` }))))
      .catch(() => {/* master unavailable — the picker just stays empty */})
  }, [])

  useEffect(() => {
    if (!editing) return
    setLoading(true)
    employeesApi
      .get(id!)
      .then((e: Employee) => {
        form.setFieldsValue({
          firstName: e.firstName,
          lastName: e.lastName,
          middleName: e.middleName ?? undefined,
          email: e.email ?? undefined,
          phone: e.phone ?? undefined,
          gender: e.gender ?? undefined,
          nationalId: e.nationalId ?? undefined,
          birthDate: e.birthDate ? dayjs(e.birthDate) : undefined,
          hireDate: dayjs(e.hireDate),
          departmentName: e.departmentName ?? undefined,
          orgUnitId: e.orgUnitId ?? undefined,
          positionId: e.positionId ?? undefined,
          positionTitle: e.positionTitle ?? undefined,
          costCentre: e.costCentre ?? undefined,
          workLocationId: e.workLocationId ?? undefined,
          // V329 — birth place, split
          birthCountry: e.birthCountry ?? undefined,
          birthCity: e.birthCity ?? undefined,
          birthAddress: e.birthAddress ?? undefined,
          // M133 — Section 3 contact fields
          altPhone: e.altPhone ?? undefined,
          workEmail: e.workEmail ?? undefined,
          workPhone: e.workPhone ?? undefined,
          // M134 — Section 4 employment fields
          employeeCategory: e.employeeCategory ?? undefined,
          seniorityDate: e.seniorityDate ? dayjs(e.seniorityDate) : undefined,
          // M150 — workforce-register master data
          externalHrId: e.externalHrId ?? undefined,
          fullNameLocal: e.fullNameLocal ?? undefined,
          sourceOfHire: e.sourceOfHire ?? undefined,
          positionTitleLocal: e.positionTitleLocal ?? undefined,
          occupationClassification: e.occupationClassification ?? undefined,
          positionClassification: e.positionClassification ?? undefined,
          workType: e.workType ?? undefined,
          projectName: e.projectName ?? undefined,
          professionalExperienceYears: e.professionalExperienceYears ?? undefined,
          jobDescriptionStatus: e.jobDescriptionStatus ?? undefined,
          timesheetApproverId: e.timesheetApproverId ?? undefined,
          expenseApproverId: e.expenseApproverId ?? undefined,
          hrTimesheetVerifierId: e.hrTimesheetVerifierId ?? undefined,
          workScheduleText: e.workScheduleText ?? undefined,
          workTimeText: e.workTimeText ?? undefined,
          lunchTimeText: e.lunchTimeText ?? undefined,
          offshoreWorkScheduleText: e.offshoreWorkScheduleText ?? undefined,
          summarizedPeriodMethod: e.summarizedPeriodMethod ?? undefined,
        })
      })
      .catch((err) =>
        message.error(err?.response?.data?.message ?? 'Failed to load employee'),
      )
      .finally(() => setLoading(false))
  }, [editing, id, form, message])

  const onFinish = async (v: FormValues) => {
    setSaving(true)
    const payload = {
      ...v,
      birthDate: v.birthDate?.format('YYYY-MM-DD'),
      hireDate: v.hireDate.format('YYYY-MM-DD'),
      // M134 — seniority date, optional
      seniorityDate: v.seniorityDate?.format('YYYY-MM-DD'),
      // PRD §4 — the contract and the opening salary travel with the hire.
      contractStartDate: v.contractStartDate?.format('YYYY-MM-DD'),
      contractEndDate: v.contractEndDate?.format('YYYY-MM-DD'),
      salaryEffectiveFrom: v.salaryEffectiveFrom?.format('YYYY-MM-DD'),
    }
    try {
      if (editing) {
        await employeesApi.update(id!, payload)
        message.success('Employee updated')
      } else {
        await employeesApi.create(payload)
        message.success('Employee created')
      }
      navigate(LIST_PATH)
    } catch (err) {
      message.error(apiErrorMessage(err, 'Save failed'), apiErrorDuration(err))
    } finally {
      setSaving(false)
    }
  }

  /**
   * Validation failures on a hidden tab produce no visible feedback, so jump
   * to the earliest tab that has one.
   */
  const onFinishFailed = ({ errorFields }: { errorFields: { name: (string | number)[] }[] }) => {
    const tabs = new Set(
      errorFields
        .map((f) => FIELD_TAB[String(f.name[0])])
        .filter((t): t is string => !!t),
    )
    const first = TAB_ORDER.find((t) => tabs.has(t))
    if (first) setActiveTab(first)
    message.error('Some required fields need attention — check the highlighted tab.')
  }

  /**
   * PRD §9 — "Work Schedule → Work/Lunch/Offshore times: derive timing values
   * from schedule master. Do not require user to type them independently."
   *
   * Only fills; never clears. A schedule with no mapping (legacy wording from
   * the workbook) leaves whatever is already there rather than blanking a
   * value someone entered deliberately.
   */
  const applySchedule = (schedule: string) => {
    const derived = WORK_SCHEDULE_DERIVED[schedule]
    if (!derived) return
    form.setFieldsValue({
      workTimeText: derived.workTime,
      lunchTimeText: derived.lunchTime,
      offshoreWorkScheduleText: derived.offshore,
    })
  }

  const personalTab = (
    <>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="firstName" label="First name" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="lastName" label="Last name" rules={[{ required: true, max: 100 }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="middleName" label="Middle name" rules={[{ max: 100 }]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          {/* M150 — local-script legal name. Contracts and state filings need
              the patronymic form, which first/middle/last cannot rebuild. */}
          <Form.Item
            name="fullNameLocal"
            label="Full name (local script)"
            tooltip="As it appears on the labour contract and state filings — e.g. “ABBASLI Abbas Elxan oğlu”."
            rules={[{ max: 300 }]}
          >
            <Input placeholder="SURNAME Name Patronymic oğlu / qızı" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="gender" label="Gender">
            <Select allowClear placeholder="—" options={GENDERS} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="birthDate" label="Date of birth">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="nationalId" label="National ID" rules={[{ max: 64 }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          {/* V329 — birth place split into country / city / address. Country is
              a closed list (ISO 3166-1); city suggests Azerbaijani places but
              accepts anything typed, because people are born anywhere. */}
          <Form.Item name="birthCountry" label="Country of birth">
            <Select
              allowClear
              showSearch
              placeholder="—"
              optionFilterProp="label"
              options={COUNTRY_OPTIONS}
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="birthCity" label="City of birth" rules={[{ max: 120 }]}>
            <AutoComplete
              allowClear
              options={CITY_OPTIONS}
              placeholder="Start typing"
              filterOption={(input, option) =>
                String(option?.value ?? '').toLowerCase().includes(input.toLowerCase())
              }
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="birthAddress" label="Address of birth" rules={[{ max: 255 }]}>
            <Input placeholder="Village, district or street" />
          </Form.Item>
        </Col>
      </Row>
      <Divider orientation="left" plain style={{ marginTop: 8 }}>
        Contact
      </Divider>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="email"
            label="Personal email"
            rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="phone" label="Personal phone" rules={[{ max: 32 }]}>
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          {/* M133 — Section 3 spec field */}
          <Form.Item name="altPhone" label="Alternative phone" rules={[{ max: 32 }]}>
            <Input placeholder="Optional second personal number" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="workEmail"
            label="Work email"
            tooltip="Typically @company.com — used for payslip + letter delivery"
            rules={[{ type: 'email', message: 'Enter a valid email' }, { max: 160 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="workPhone" label="Work phone" rules={[{ max: 32 }]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  const jobTab = (
    <>
      <Row gutter={16}>
        <Col span={8}>
          {/* M150 — reconciliation key against the customer's legacy HRIS. */}
          <Form.Item
            name="externalHrId"
            label="External HR ID"
            tooltip="The number this person carries in your previous/parallel HR system (e.g. GHRS). Must be unique — used to reconcile against the source register."
            rules={[{ max: 40 }]}
          >
            <Input placeholder="e.g. 2004209" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="hireDate"
            label="Hire date"
            rules={[{ required: true, message: 'Hire date is required' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          {/* M134 — Section 4 spec fields */}
          <Form.Item
            name="seniorityDate"
            label="Seniority date"
            tooltip="Tenure anchor for benefits + leave. Leave blank to use hire date. Rehires can carry forward their original date here."
            rules={[{
              validator: (_, v: dayjs.Dayjs | undefined) =>
                !v || !v.isAfter(dayjs(), 'day')
                  ? Promise.resolve()
                  : Promise.reject(new Error('Seniority date cannot be in the future')),
            }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="orgUnitId"
            label="Department"
            tooltip="From the department master. Add a missing one under Master Data > Departments."
          >
            <Select
              allowClear
              showSearch
              placeholder="—"
              optionFilterProp="label"
              options={orgUnits}
              onChange={(v: string) =>
                // The name is stored alongside the id: reports, exports and the
                // payroll files read departmentName, and it must not drift from
                // the unit that was actually chosen.
                form.setFieldsValue({
                  departmentName: orgUnits.find((o) => o.value === v)?.label.split(' — ')[1],
                })
              }
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="positionId"
            label="Position"
            tooltip="From the position master. Add a missing one under Master Data > Positions."
          >
            <Select
              allowClear
              showSearch
              placeholder="—"
              optionFilterProp="label"
              options={positions}
              onChange={(v: string) =>
                form.setFieldsValue({
                  positionTitle: positions.find((o) => o.value === v)?.label.split(' — ')[1],
                })
              }
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          {/* M150 — local-language title, reproduced on contracts and orders. */}
          <Form.Item
            name="positionTitleLocal"
            label="Position (local language)"
            tooltip="Job title as written on the labour contract and internal orders."
            rules={[{ max: 300 }]}
          >
            <Input />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          {/* M150 — mandatory on Azerbaijani labour-contract filings. */}
          <Form.Item
            name="occupationClassification"
            label="Occupation classification"
            tooltip="State occupational classifier entry (“Məşğulluq təsnifatı”) — required on labour-contract filings."
            rules={[{ max: 160 }]}
          >
            <Input placeholder="e.g. baş mühəndis" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="positionClassification"
            label="Position classification"
            tooltip="Internal grade bucket — e.g. Specialist, Manager, Worker, Director. Free text: use your own taxonomy."
            rules={[{ max: 60 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={POSITION_CLASSIFICATIONS} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="employeeCategory"
            label="Employee category"
            tooltip="Configurable bucket — e.g. white-collar / blue-collar, salaried / hourly, executive / IC."
            rules={[{ max: 60 }]}
          >
            <Input placeholder="Free text" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          {/* M150 — selects which compensation rate applies downstream. */}
          <Form.Item
            name="workType"
            label="Work type"
            tooltip="Where the work is physically performed. Selects which rate and schedule pattern apply — the amounts themselves live in Compensation."
          >
            <Select allowClear placeholder="—" options={WORK_TYPES} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="workLocationId"
            label="Work location"
            tooltip="Primary physical site — drives geofencing, shift defaults, and location allowances."
          >
            <Select
              allowClear
              showSearch
              placeholder="— none —"
              optionFilterProp="label"
              options={locationOptions}
            />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="costCentre" label="Cost centre" rules={[{ max: 64 }]}>
            <Input />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          {/* M150 — register label. Timesheet booking remains authoritative. */}
          <Form.Item
            name="projectName"
            label="Project"
            tooltip="Project this person is charged to, as named in your personnel register. Timesheet project bookings remain the authoritative cost dimension."
            rules={[{ max: 200 }]}
          >
            <Input />
          </Form.Item>
        </Col>
        <Col span={8}>
          {/* M150 — feeds seniority leave brackets (Art. 116.1). */}
          <Form.Item
            name="professionalExperienceYears"
            label="Professional experience (years)"
            tooltip="Total professional experience. Feeds seniority-based leave entitlement and grading reviews."
          >
            <InputNumber min={0} max={70} step={0.5} style={{ width: '100%' }} placeholder="e.g. 8.5" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="jobDescriptionStatus"
            label="Job description status"
            tooltip="Whether a signed job description is on file. Compliance checklists read this."
            rules={[{ max: 120 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={JOB_DESCRIPTION_STATUSES} />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  const approvalsTab = (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Leave any of these blank to route to the line manager"
        description="These override the default routing only when the approver is somebody other than this employee's line manager. Nobody can be set as their own approver."
      />
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="timesheetApproverId"
            label="Timesheet approver"
            tooltip="Approves this employee's timesheets."
          >
            <EmployeePicker placeholder="— line manager —" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="expenseApproverId"
            label="Expense approver"
            tooltip="Approves this employee's expense claims."
          >
            <EmployeePicker placeholder="— line manager —" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="hrTimesheetVerifierId"
            label="HR timesheet verifier"
            tooltip="HR-side check after the approver signs off and before payroll picks the timesheet up."
          >
            <EmployeePicker placeholder="— none —" style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  /** PRD Appendix A tab 3 — the employment contract, captured during the hire. */
  const contractTab = (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Filling this opens the contract with the employee"
        description="Leave it blank to create the person only and add the contract later from the profile. An end date is optional — leave it empty for an indefinite contract."
      />
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="contractStartDate" label="Contract start date">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="contractEndDate"
            label="Contract end date"
            tooltip="Leave empty for an indefinite contract."
            dependencies={['contractStartDate']}
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value) {
                  const start = getFieldValue('contractStartDate')
                  if (!value || !start || !value.isBefore(start)) return Promise.resolve()
                  return Promise.reject(
                    new Error('Contract end date cannot be earlier than the start date'),
                  )
                },
              }),
            ]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="contractType" label="Contract type">
            <Select
              allowClear
              placeholder="— same as employment type —"
              options={[
                { value: 'PERMANENT', label: 'Permanent' },
                { value: 'FIXED_TERM', label: 'Fixed term' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  /** PRD Appendix A tab 4 — opening salary. Server-enforced HR-admin only. */
  const compensationTab = (
    <>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="Salary is restricted"
        description="Only an HR administrator may set pay. If you do not have that permission, leave this blank — the employee is still created, and pay can be added afterwards from their profile."
      />
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="monthlyBaseSalary"
            label="Monthly base salary (AZN), gross"
            rules={[{ type: 'number', min: 0.01, message: 'Salary must be greater than zero' }]}
          >
            <InputNumber style={{ width: '100%' }} min={0} step={100} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="salaryEffectiveFrom"
            label="Effective from"
            tooltip="Defaults to the contract start date, or the hire date."
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  /** PRD Appendix A tab 7 — where this hire came from. */
  const recruitmentTab = (
    <>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="sourceOfHire"
            label="Source of hire"
            tooltip="Recruitment channel this person came through. Populated automatically for hires made through Recruitment."
            rules={[{ max: 80 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={CANDIDATE_SOURCES} />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  const scheduleTab = (
    <>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Agreed pattern, as worded in the contract"
        description="These are reproduced verbatim on contracts and orders. Actual worked time, overtime and absence are computed by Attendance from clock records — nothing here changes those calculations."
      />
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="workScheduleText"
            label="Work schedule"
            rules={[{ max: 200 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={WORK_SCHEDULES}
              onChange={(v: string) => applySchedule(v)} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="workTimeText" label="Work time" rules={[{ max: 60 }]}>
            <Input readOnly title="Derived from the work schedule (PRD §6)." />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="lunchTimeText" label="Lunch time" rules={[{ max: 60 }]}>
            <Input readOnly title="Derived from the work schedule (PRD §6)." />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item
            name="offshoreWorkScheduleText"
            label="Offshore work schedule"
            tooltip="Rotation pattern when offshore differs from the onshore schedule."
            rules={[{ max: 120 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={OFFSHORE_DAILY_SCHEDULES} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="summarizedPeriodMethod"
            label="Summarized working-time period"
            tooltip="Accounting period for summarized working time (Art. 62) — e.g. “1 mnth”, or a fixed-date scheme."
            rules={[{ max: 80 }]}
          >
            <Select allowClear showSearch placeholder="—" optionFilterProp="label"
              options={TIME_ACCOUNTING_METHODS} />
          </Form.Item>
        </Col>
      </Row>
    </>
  )

  // forceRender keeps every pane's Form.Items mounted. Without it Ant Design
  // unmounts hidden tabs, which drops their values from the submitted payload
  // and skips their validation entirely.
  const tabItems = [
    { key: 'personal', label: 'Personal & contact', children: personalTab, forceRender: true },
    { key: 'job', label: 'Employment & job', children: jobTab, forceRender: true },
    { key: 'contract', label: 'Contract', children: contractTab, forceRender: true },
    { key: 'compensation', label: 'Compensation', children: compensationTab, forceRender: true },
    { key: 'schedule', label: 'Work schedule', children: scheduleTab, forceRender: true },
    { key: 'approvals', label: 'Approvals', children: approvalsTab, forceRender: true },
    { key: 'recruitment', label: 'Recruitment', children: recruitmentTab, forceRender: true },
  ]

  return (
    <FormPageShell title={editing ? 'Edit employee' : 'New employee'} backTo={LIST_PATH}>
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : (
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          onFinishFailed={onFinishFailed}
          scrollToFirstError
        >
          <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

          {editing && (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="Salary, allowances, leave entitlement, contracts and termination are edited elsewhere"
              description="They are owned by the payroll, benefits, leave and lifecycle modules — open this employee's profile to view or change them on the Compensation, Contracts and Documents tabs. Keeping them there is what preserves payroll traceability and the approval audit trail."
            />
          )}

          <Form.Item style={{ marginBottom: 0 }}>
            <Space>
              <Button onClick={() => navigate(LIST_PATH)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={saving}>
                {editing ? 'Save changes' : 'Create employee'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      )}
    </FormPageShell>
  )
}
