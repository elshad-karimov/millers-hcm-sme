import { useCallback, useEffect, useState } from 'react'
import { Select } from 'antd'
import { employeesApi, type Employee } from '../api/employees'

/**
 * Picks a person by their sign-in name.
 *
 * <p>Delegation targets a username, so the field asked for one as free text —
 * which meant knowing somebody's exact login before you could hand them your
 * approvals, and a typo delegated to nobody at all. This searches people by
 * name or employee number and submits their username.
 *
 * <p>Employees with no login are listed and disabled rather than hidden: the
 * answer to "why can't I pick Kamran?" should be visible, not absent. Give
 * them a login from User Management and they become selectable.
 */
export function LoginPicker({
  value,
  onChange,
  placeholder,
  autoFocus,
}: {
  value?: string
  onChange?: (username: string | undefined) => void
  placeholder?: string
  autoFocus?: boolean
}) {
  const [people, setPeople] = useState<Employee[]>([])
  const [loading, setLoading] = useState(false)

  const search = useCallback(async (text?: string) => {
    setLoading(true)
    try {
      const page = await employeesApi.list({ search: text || undefined, size: 50 })
      setPeople(page.content)
    } catch {
      setPeople([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void search() }, [search])

  return (
    <Select
      showSearch
      allowClear
      autoFocus={autoFocus}
      placeholder={placeholder}
      loading={loading}
      value={value}
      onChange={onChange}
      onSearch={(text) => void search(text)}
      // The server already searched; filtering again locally would hide
      // matches it returned for reasons the browser cannot see.
      filterOption={false}
      style={{ width: '100%' }}
      options={people.map((e) => ({
        label: e.username
          ? `${e.firstName} ${e.lastName} — ${e.username}`
          : `${e.firstName} ${e.lastName} — no login yet`,
        value: e.username ?? `__no-login-${e.id}`,
        disabled: !e.username,
      }))}
    />
  )
}
