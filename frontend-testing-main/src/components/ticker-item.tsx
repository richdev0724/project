import { useTickerSnapshot } from '@/hooks/use-ticker-snapshot'
import {
  IconTrendingDown,
  IconTrendingUp,
  IconXboxX,
} from '@tabler/icons-react'
import { Link } from 'react-router-dom'

interface TickerItemProps {
  ticker: string
  onDelete: () => void
}

const TickerItem = ({ ticker, onDelete }: TickerItemProps) => {
  // Fetch ticker data using the custom hook
  const { data } = useTickerSnapshot(ticker)

  // Determine icon and class based on change percentage
  const tickerChange = data?.todaysChangePerc
  let IconComponent = null
  let iconClassName = ''

  if (tickerChange && tickerChange > 0) {
    IconComponent = <IconTrendingUp />
    iconClassName = 'text-success animate-pulse font-semibold'
  } else if (tickerChange && tickerChange < 0) {
    IconComponent = <IconTrendingDown />
    iconClassName = 'text-danger animate-pulse font-semibold'
  }

  return (
    <>
      <Link to={`/options/${ticker}`} target='_blank'>
        <div className='flex items-center space-x-2'>
          <span className='font-semibold'>{ticker}</span>
          <span
            className={`${tickerChange && tickerChange > 0 ? 'font-semibold text-success' : 'font-semibold text-danger'}`}
          >
            {tickerChange?.toFixed(2)}%
          </span>
          {IconComponent && (
            <span className={iconClassName}>{IconComponent}</span>
          )}
        </div>
      </Link>
      <div className='flex'>
        <button
          className='rounded-sm pl-2 text-gray-600 transition-colors hover:text-danger'
          onClick={onDelete}
        >
          <IconXboxX height={18} width={18} />
        </button>
      </div>
    </>
  )
}

export default TickerItem
