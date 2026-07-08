export default function TopContractProgressBar({
  askOrAboveAskPercentage = 90,
  midPercentage = 10,
  bidOrBelowBidPercentage = 20,
  leftColor = '#28BF94',
  middleColor = '#F5F7F7',
  rightColor = '#CD4B38',
  className = '',
  type = 'call',
}) {
  return (
    <div
      className={`h-[14px] w-[265px] overflow-hidden rounded-[3px] p-0 ${className}`}
      style={{ opacity: 1 }}
    >
      <div className='flex h-full'>
        {type === 'call' ? (
          <>
            <div
              className='h-full transition-all duration-300'
              style={{
                width: `${askOrAboveAskPercentage}%`,
                backgroundColor: leftColor,
              }}
            />
            {midPercentage > 0 && (
              <div
                className='h-full transition-all duration-300'
                style={{
                  width: `${midPercentage}%`,
                  backgroundColor: middleColor,
                }}
              />
            )}
            <div
              className='h-full transition-all duration-300'
              style={{
                width: `${bidOrBelowBidPercentage}%`,
                backgroundColor: rightColor,
              }}
            />
          </>
        ) : (
          <>
            <div
              className='h-full transition-all duration-300'
              style={{
                width: `${bidOrBelowBidPercentage}%`,
                backgroundColor: rightColor,
              }}
            />
            {midPercentage > 0 && (
              <div
                className='h-full transition-all duration-300'
                style={{
                  width: `${midPercentage}%`,
                  backgroundColor: middleColor,
                }}
              />
            )}
            <div
              className='h-full transition-all duration-300'
              style={{
                width: `${askOrAboveAskPercentage}%`,
                backgroundColor: leftColor,
              }}
            />
          </>
        )}
      </div>
    </div>
  )
}
