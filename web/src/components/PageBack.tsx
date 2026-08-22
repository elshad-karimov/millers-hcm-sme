import { createContext, useContext, useEffect } from 'react'

/**
 * Lets a page tell the layout it already has its own way back.
 *
 * The layout puts a generic Back above every screen. A few pages carry a more
 * useful control of their own — "Back to list" on the forms, which always
 * returns to the register rather than to wherever the user happened to come
 * from — and two of them side by side is clutter.
 *
 * A page declares its own control rather than the layout keeping a list of
 * routes: a route list would be one more thing to remember when a page is
 * added, and it would be wrong silently.
 */
const OwnBackContext = createContext<((has: boolean) => void) | null>(null)

export const OwnBackProvider = OwnBackContext.Provider

/**
 * Call from any page that renders its own back control. The layout hides the
 * generic Back for as long as that page is mounted, and shows it again on the
 * next route.
 */
export function useOwnBackControl(): void {
  const declare = useContext(OwnBackContext)
  useEffect(() => {
    declare?.(true)
    return () => declare?.(false)
  }, [declare])
}
