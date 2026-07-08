export default function PercentageCapsule({
  leftPercentage = 33,
  middlePercentage = 33,
  rightPercentage = 34,
  leftColor = '#22c55e',
  middleColor = '#0ea5e9',
  rightColor = '#ef4444',
  className = '',
}) {
  return (
    <div
      className={`h-[11px] w-full overflow-hidden rounded-full ${className}`}
      style={{ opacity: 1 }}
    >
      <div className='flex h-full'>
        <div
          className='h-full transition-all duration-300'
          style={{
            width: `${leftPercentage}%`,
            backgroundColor: leftColor,
          }}
        />
        {middlePercentage > 0 && (
          <div
            className='h-full transition-all duration-300'
            style={{
              width: `${middlePercentage}%`,
              backgroundColor: middleColor,
            }}
          />
        )}
        <div
          className='h-full transition-all duration-300'
          style={{
            width: `${rightPercentage}%`,
            backgroundColor: rightColor,
          }}
        />
      </div>
    </div>
  )
}
