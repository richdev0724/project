import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { useDarkPoolScanner } from '@/hooks/use-dark-pool-scanner'
import TopDarkPoolProgressBar from './top-dark-pool-progress-bar'
import Loader from '@/components/loader'

interface DarkPoolScannerResult {
  status: string
  data?: any[]
  lastScanTime?: string
}

/**
 * Component that renders the Top Dark Pool table.
 *
 * @returns The Top Dark Pool table component.
 */
const TopDarkPool = () => {
  const { data, isLoading } = useDarkPoolScanner() as {
    data: DarkPoolScannerResult | undefined
    isLoading: boolean
  }
  // Dark Pool Scanner Status

  // If loading, show loading indicator
  if (isLoading || data?.status === 'running') {
    return (
      <div className='px-2'>
        <div className='flex items-center justify-between py-2'>
          <div>
            <h2>Top Dark Pool</h2>
          </div>
        </div>
        <div className='mt-[8px] flex h-[323px] items-center justify-center'>
          <Loader />
        </div>
      </div>
    )
  }

  // If no data yet, show empty state
  if (!data) {
    return (
      <div className='px-2'>
        <div className='flex items-center justify-between py-2'>
          <div>
            <h2>Top Dark Pool</h2>
          </div>
        </div>
        <div className='mt-[8px] flex h-[323px] items-center justify-center'>
          <span className='text-lg text-gray-400'>No data available</span>
        </div>
      </div>
    )
  }

  const darkPoolData = data.data

  const formatNumber = (num: number) => {
    if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
    if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
    if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
    return num.toFixed(1)
  }

  const calculatePercentage = (value: number, maxValue: number) => {
    return (value / maxValue) * 100
  }

  const maxPremium = darkPoolData?.[0]?.total_premium || 0

  return (
    <div className='px-2'>
      <div className='flex items-center justify-between py-2'>
        <div>
          <h2>Top Dark Pool</h2>
        </div>
      </div>
      <div className='mt-[8px]'>
        <Table className='text-[13px]'>
          <TableBody>
            {darkPoolData?.map((trade: any, index: number) => (
              <TableRow key={index} className='items-center border-none'>
                <TableCell className='h-9 w-[40px] items-start text-end font-medium'>
                  {trade.ticker}
                </TableCell>
                <TableCell className='h-9 w-[80px] text-center'>
                  <span className='min-w-2'>${trade.price.toFixed(2)}</span>
                </TableCell>
                <TableCell className='h-9'>
                  <TopDarkPoolProgressBar
                    percentage={calculatePercentage(
                      trade.total_premium,
                      maxPremium
                    )}
                  />
                </TableCell>
                <TableCell className='h-9 w-[20px] text-right font-medium'>
                  ${formatNumber(trade.total_premium)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

export default TopDarkPool
