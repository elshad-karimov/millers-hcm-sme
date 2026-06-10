import { useEffect, useState } from 'react'
import { AutoComplete } from 'antd'
import {
  reasonMasterApi,
  type Reason,
  type ReasonCategory,
} from '../api/reasonMaster'

/**
 * M259 — Reusable Select / AutoComplete backed by the §22 reason master.
 *
 * <p>Renders an AutoComplete instead of a plain Select so the operator
 * can still type an ad-hoc reason when the master doesn't cover the
 * case (rare, but happens). The backend stores whatever string lands —
 * the master just guides toward consistency for the common cases.
 *
 * <p>One fetch per category per page-load, cached at module scope so
 * opening multiple lifecycle modals doesn't refetch.
 */

const cache: Partial<Record<ReasonCategory, Reason[]>> = {}

export function ReasonSelect({
  category,
  value,
  onChange,
  placeholder,
  disabled,
}: {
  category: ReasonCategory
  value?: string
  onChange?: (v: string) => void
  placeholder?: string
  disabled?: boolean
}) {
  const [options, setOptions] = useState<Reason[]>(cache[category] ?? [])

  useEffect(() => {
    if (cache[category]) {
      setOptions(cache[category]!)
      return
    }
    reasonMasterApi
      .list(category)
      .then((rows) => {
        cache[category] = rows
        setOptions(rows)
      })
      .catch(() => {
        // Silent — if the lookup fails the operator can still type
        // a freeform reason, no need to alarm them.
      })
  }, [category])

  return (
    <AutoComplete
      value={value}
      onChange={(v) => onChange?.(v)}
      options={options.map((r) => ({
        value: r.label,
        label: r.label,
      }))}
      placeholder={placeholder}
      disabled={disabled}
      filterOption={(input, option) =>
        String(option?.label ?? '')
          .toLowerCase()
          .includes(input.toLowerCase())
      }
      allowClear
    />
  )
}
