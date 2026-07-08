import { ExpirationSelectDropdown } from '@/components/custom/expiration-select-dropdown'
import { useState, useEffect } from 'react'
import GammaExposureChart from './gamma-exposure-chart'
import { GAMMA_EXPOSURE_TICKERS } from '@/constants'
import { useExpirationDates } from '@/hooks/use-gamma-exposure'

const GammaExposure = () => {
  // Rendering GammaExposure component

  const [expiration, setExpiration] = useState<string>('')
  const [selectedTicker, setSelectedTicker] = useState<string>(
    GAMMA_EXPOSURE_TICKERS[0].value
  )

  const { data: expirationDates } = useExpirationDates(selectedTicker)

  useEffect(() => {
    // Current ticker and expiration dates
    // Received expiration dates
    if (expirationDates?.length > 0 && !expiration) {
      // Setting initial expiration date
      setExpiration(expirationDates[0])
    }
  }, [expirationDates, expiration])

  const handleTickerSelection = (value: string) => {
    // User selected new ticker
    setSelectedTicker(value)
    setExpiration('') // Reset expiration when ticker changes
  }

  return (
    <div className='h-[404px] w-full pl-5 pr-7'>
      <div className='flex w-full items-center justify-between text-base'>
        <span className='font-normal'>Gamma Exposure</span>
        <span className='font-normal'>
          {selectedTicker} is{' '}
          <span className='font-medium text-[#28BF94]'>Bullish</span>
        </span>
      </div>
      <GammaExposureChart symbol={selectedTicker} expiration={expiration} />
      <div className='mt-20 flex items-center justify-between text-sm'>
        <div className='flex gap-4 text-xs'>
          {GAMMA_EXPOSURE_TICKERS.map((timeframe, index) => (
            <div
              key={index}
              onClick={() => handleTickerSelection(timeframe.value)}
              className='cursor-pointer'
            >
              <span className='inline-block'>{timeframe.label}</span>
              {timeframe.value === selectedTicker && (
                <div className='h-[3px] w-full rounded-b-[1px] bg-[#FFFFFF]'></div>
              )}
            </div>
          ))}
        </div>
        <ExpirationSelectDropdown
          setExpiration={(date) => {
            // User selected new expiration date
            setExpiration(date)
          }}
          expiration={expiration}
          expirationDates={expirationDates || []}
        />
      </div>
    </div>
  )
}

export default GammaExposure
