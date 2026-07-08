export default function TopBullishNetPremiumProgressBar({
  currentValue = 0,
  largestValue = 100,
  className = '',
}) {
  // Calculate the percentage based on the current and largest values
  const percentage = largestValue > 0 ? (currentValue / largestValue) * 100 : 0
  const clampedPercentage = Math.min(Math.max(percentage, 0), 100)

  return (
    <div className='h-[15px] w-full rounded-[3px] bg-[#1A1F24]'>
      <div
        className={`h-full overflow-hidden rounded-[3px] ${className}`}
        style={{
          width: `${clampedPercentage}%`,
        }}
      />
    </div>
  )
}
