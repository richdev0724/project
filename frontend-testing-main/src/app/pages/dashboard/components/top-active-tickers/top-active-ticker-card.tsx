import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import TickerCardLogo from './ticker-card-logo'
import { IconMaximize } from '@tabler/icons-react'
import { ActiveTicker } from './top-active-tickers'
import { Button } from '@/components/custom/button'
import { Link } from 'react-router-dom'
import TickerInstitutionalLevelPopover from './ticker-institutional-level-popover'

interface ActiveTickerProps {
  item: ActiveTicker
}

export default function TopActiveTickerCard({ item }: ActiveTickerProps) {
  const borderColor =
    item.changesPercentage > 0 ? 'border-green-500' : 'border-danger'

  return (
    <Card
      className={`rounded-md border font-medium ${borderColor} flex w-full flex-col items-center space-y-2 p-1`}
    >
      <CardHeader className='w-full text-center'>
        <CardTitle className='flex flex-row items-center justify-center space-x-2'>
          <TickerCardLogo symbol={item.ticker} />
          <TickerInstitutionalLevelPopover ticker={item.ticker}>
            <span className='cursor-pointer text-sm font-medium hover:underline'>
              {item.ticker}
            </span>
          </TickerInstitutionalLevelPopover>
          <Button size='icon' variant='ghost'>
            <Link to={`/options/${item.ticker}`} target='_blank'>
              <IconMaximize size={25} />
            </Link>
          </Button>
        </CardTitle>
      </CardHeader>
      <CardContent className='flex flex-col items-center space-y-1'>
        <div className='flex flex-row items-center justify-center space-x-2 text-sm'>
          <span>${item?.price ?? item?.price.toFixed(2)}</span>
          <div
            className={`items-center ${
              item?.changesPercentage > 0 ? 'text-success' : 'text-danger '
            }`}
          >
            <span>
              {' '}
              {item?.changesPercentage ?? item?.changesPercentage.toFixed(2)}%%
            </span>
          </div>
        </div>
        <div className='flex flex-row items-center justify-center space-x-2'>
          <span className='text-dark text-xs font-medium'>Volume:</span>
          <span className='text-dark text-xs font-bold'>166M</span>
        </div>
      </CardContent>
    </Card>
  )
}
