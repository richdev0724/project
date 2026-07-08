import { ExpirationSelectDropdown } from '@/components/custom/expiration-select-dropdown'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useExpirationDates } from '@/hooks/use-gamma-exposure'
import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import StrikePriceChart from './strike-price-chart'

const TickerStrikePrice = () => {
  const { symbol } = useParams()
  const [side, setSide] = useState<
    'open_interest' | 'daily_volume' | 'net_premium'
  >('open_interest')

  const [expiration, setExpiration] = useState<string>('')
  const { data: expirationDates = [] } = useExpirationDates(symbol ?? '')

  useEffect(() => {
    if (expirationDates?.length > 0 && !expiration) {
      setExpiration(expirationDates[0])
    }
  }, [expirationDates, expiration])

  const handleSideChange = (
    value: 'open_interest' | 'daily_volume' | 'net_premium'
  ) => {
    setSide(value)
  }

  return (
    <div className='-mt-1 h-[404px] w-full px-4 pb-4 pt-[8px]'>
      <div className='mb-2 flex items-center justify-between pl-2 text-sm'>
        <div className='flex w-fit items-center'>
          <div>
            <span className='whitespace-nowrap font-normal'>
              Strike Price |
            </span>
          </div>
          <div className='ml-2'>
            <Select value={side} onValueChange={handleSideChange}>
              <SelectTrigger className='h-auto justify-end !border-none !bg-transparent px-0 text-sm font-medium text-[#28BF94] hover:!bg-transparent focus:!bg-transparent focus-visible:!outline-none focus-visible:!ring-0 focus-visible:!ring-offset-0 [&_svg]:!text-white [&_svg]:!opacity-100'>
                <SelectValue placeholder='Select option' />
              </SelectTrigger>
              <SelectContent className='!border-0 bg-background !shadow-none [&>div>*[data-highlighted]]:!bg-transparent [&>div>*[data-state=checked]]:!bg-transparent [&>div>*]:!rounded-none [&>div>*]:!bg-transparent [&>div>*]:hover:!bg-transparent [&>div>*]:focus:!bg-transparent'>
                <SelectItem
                  value='open_interest'
                  className='focus:bg-transparent focus:text-current'
                >
                  Open Interest
                </SelectItem>
                <SelectItem
                  value='daily_volume'
                  className='focus:bg-transparent focus:text-current'
                >
                  Volume
                </SelectItem>
                <SelectItem
                  value='net_premium'
                  className='focus:bg-transparent focus:text-current'
                >
                  Premium
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <div>
          <span className='text-sm font-normal'>{symbol}</span>
        </div>
      </div>
      {symbol && expiration && (
        <div className='h-[300px]'>
          <StrikePriceChart
            side={side}
            expiration={expiration}
            symbol={symbol}
          />
        </div>
      )}
      {symbol && (
        <div className='flex justify-end'>
          <ExpirationSelectDropdown
            setExpiration={setExpiration}
            expiration={expiration}
            expirationDates={expirationDates}
          />
        </div>
      )}
    </div>
  )
}

export default TickerStrikePrice
