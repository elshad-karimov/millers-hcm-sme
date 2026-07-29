/**
 * Shared server-side employee picker — replaces the hardcoded 500-cap pattern.
 * Uses debounced search on the backend so it works with 10k+ employee tenants.
 * Automatically resolves the label for the selected value when it's not in the search results.
 */
import { useEffect, useState, useCallback, useMemo } from 'react'
import { Select } from 'antd'
import { employeesApi, type Employee, type EmployeeListParams } from '../api/employees'

interface EmployeePickerProps {
  value?: string | null
  onChange?: (id: string | undefined) => void
  placeholder?: string
  disabled?: boolean
  allowClear?: boolean
  style?: React.CSSProperties
  className?: string
  // Pass-through filters to constrain which employees are selectable
  status?: EmployeeListParams['status']
  employmentType?: EmployeeListParams['employmentType']
  departmentOrgUnitId?: EmployeeListParams['departmentOrgUnitId']
  managerId?: EmployeeListParams['managerId']
  leaveGroupId?: EmployeeListParams['leaveGroupId']
  costCentre?: EmployeeListParams['costCentre']
}

interface EmployeeOption {
  label: string
  value: string
  employee: Employee
}

export function EmployeePicker({
  value,
  onChange,
  placeholder = 'Select employee',
  disabled,
  allowClear = true,
  style,
  className,
  status,
  employmentType,
  departmentOrgUnitId,
  managerId,
  leaveGroupId,
  costCentre,
}: EmployeePickerProps) {
  const [options, setOptions] = useState<EmployeeOption[]>([])
  const [loading, setLoading] = useState(false)
  const [searchText, setSearchText] = useState('')
  const [labelCache, setLabelCache] = useState<Map<string, string>>(new Map())

  // Build the base filter params from props
  const baseFilters: EmployeeListParams = useMemo(() => {
    const filters: EmployeeListParams = {}
    if (status !== undefined) filters.status = status
    if (employmentType !== undefined) filters.employmentType = employmentType
    if (departmentOrgUnitId !== undefined) filters.departmentOrgUnitId = departmentOrgUnitId
    if (managerId !== undefined) filters.managerId = managerId
    if (leaveGroupId !== undefined) filters.leaveGroupId = leaveGroupId
    if (costCentre !== undefined) filters.costCentre = costCentre
    return filters
  }, [status, employmentType, departmentOrgUnitId, managerId, leaveGroupId, costCentre])

  // Format employee label consistently
  const formatLabel = (emp: Employee) =>
    `${emp.employeeNo} — ${emp.firstName} ${emp.lastName}`

  // Convert employees to options
  const toOptions = (employees: Employee[]): EmployeeOption[] =>
    employees.map((emp) => ({
      label: formatLabel(emp),
      value: emp.id,
      employee: emp,
    }))

  // Fetch employees with search + filters
  const fetchEmployees = useCallback(
    async (search?: string) => {
      setLoading(true)
      try {
        const response = await employeesApi.list({
          ...baseFilters,
          search: search || undefined,
          size: 50, // No 500 cap — server-side search returns top matches
        })
        const opts = toOptions(response.content)
        setOptions(opts)
        // Cache labels for these employees
        response.content.forEach((emp) => {
          setLabelCache((prev) => new Map(prev).set(emp.id, formatLabel(emp)))
        })
      } catch (err) {
        console.error('Failed to load employees:', err)
        setOptions([])
      } finally {
        setLoading(false)
      }
    },
    [baseFilters],
  )

  // Resolve the label for the selected value if it's not in the options/cache
  useEffect(() => {
    if (!value) return
    if (labelCache.has(value)) return
    if (options.some((opt) => opt.value === value)) return

    // Fetch this specific employee
    employeesApi
      .get(value)
      .then((emp) => {
        setLabelCache((prev) => new Map(prev).set(emp.id, formatLabel(emp)))
        // Add to options if it matches the current filters (so it shows in the dropdown)
        setOptions((prev) => {
          if (prev.some((opt) => opt.value === emp.id)) return prev
          return [...prev, { label: formatLabel(emp), value: emp.id, employee: emp }]
        })
      })
      .catch((err) => {
        console.error('Failed to resolve selected employee:', err)
      })
  }, [value, labelCache, options])

  // Load initial page on mount or when filters change
  useEffect(() => {
    fetchEmployees()
  }, [fetchEmployees])

  // Debounced search handler
  useEffect(() => {
    if (!searchText) {
      fetchEmployees()
      return
    }
    const timer = setTimeout(() => {
      fetchEmployees(searchText)
    }, 300)
    return () => clearTimeout(timer)
  }, [searchText, fetchEmployees])

  const handleSearch = (text: string) => {
    setSearchText(text)
  }

  const handleChange = (newValue: string | undefined) => {
    onChange?.(newValue)
  }

  // If we have a value but no label yet (initial render before fetch completes),
  // show the value as a placeholder label
  const displayOptions = useMemo(() => {
    if (!value) return options
    if (options.some((opt) => opt.value === value)) return options
    if (labelCache.has(value)) {
      // Add a placeholder option for the selected value
      return [
        { label: labelCache.get(value)!, value, employee: null as any },
        ...options.filter((opt) => opt.value !== value),
      ]
    }
    // Waiting for the fetch — show value as temporary label
    return [
      { label: value, value, employee: null as any },
      ...options.filter((opt) => opt.value !== value),
    ]
  }, [value, options, labelCache])

  return (
    <Select
      showSearch
      filterOption={false}
      value={value}
      onChange={handleChange}
      onSearch={handleSearch}
      placeholder={placeholder}
      disabled={disabled}
      allowClear={allowClear}
      loading={loading}
      style={style}
      className={className}
      options={displayOptions}
    />
  )
}
