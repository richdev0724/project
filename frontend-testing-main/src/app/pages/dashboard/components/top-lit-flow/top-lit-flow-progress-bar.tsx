export default function TopLitFlowProgressBar({
  percentage = 0,
  className = '',
}) {
  const clampedPercentage = Math.min(Math.max(percentage, 0), 100)

  return (
    <div
      className={`h-[15px] overflow-hidden rounded-[3px] ${className} bg-gradient-to-l from-topLitFlowPrimary to-topLitFlowPrimaryTransparent`}
      style={{
        width: `${clampedPercentage}%`,
      }}
    />
  )
}
