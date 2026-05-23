interface LogoProps {
  size?: number
  withWordmark?: boolean
  wordmarkColor?: string
}

export function Logo({ size = 36, withWordmark = false, wordmarkColor }: LogoProps) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 12 }}>
      <img
        src="/brand/millers-mark-192.png"
        alt="Millers"
        width={size}
        height={size}
        style={{ display: 'block' }}
      />
      {withWordmark && (
        <span
          style={{
            fontWeight: 700,
            fontSize: size * 0.5,
            letterSpacing: 0.2,
            color: wordmarkColor ?? 'inherit',
          }}
        >
          Millers HCM
        </span>
      )}
    </span>
  )
}
