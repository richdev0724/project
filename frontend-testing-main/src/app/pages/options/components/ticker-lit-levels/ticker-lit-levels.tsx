import TopLitFlowProgressBar from '@/app/pages/dashboard/components/top-lit-flow/top-lit-flow-progress-bar'
import Loader from '@/components/loader'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { useTickerLitPoolLevels } from '@/hooks/use-ticker-lit-pool-levels'
import { useParams } from 'react-router-dom'

const TickerLitLevels = () => {
  const { symbol = '' } = useParams()
  const { data, isLoading, isError } = useTickerLitPoolLevels(symbol, 10)

  const formatNumber = (num: number) => {
    if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
    if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
    if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
    return num.toFixed(1)
  }

  const calculatePercentage = (value: number, maxValue: number) => {
    return (value / maxValue) * 100
  }

  if (isLoading) {
    return (
      <div className='px-4 py-4'>
        <div className='flex items-center justify-between'>
          <h2>{`${symbol} Lit Levels`}</h2>
        </div>
        <div className='mt-[20px] flex h-[323px] items-center justify-center'>
          <Loader />
        </div>
      </div>
    )
  }

  if (isError || !data || data.levels.length === 0) {
    return (
      <div className='px-4 py-4'>
        <div className='flex items-center justify-between'>
          <h2>{`${symbol} Lit Levels`}</h2>
        </div>
        <div className='mt-[20px] flex h-[323px] items-center justify-center'>
          <span className='text-lg text-gray-400'>
            No lit pool data available
          </span>
        </div>
      </div>
    )
  }

  const maxPremium = data.levels[0]?.total_premium || 0

  return (
    <div className='px-4 py-4'>
      <div className='flex items-center justify-between'>
        <h2>{`${symbol} Lit Levels`}</h2>
      </div>
      <div className='mt-[20px]'>
        <Table className='text-[13px]'>
          <TableBody>
            {data.levels.map((level, index) => (
              <TableRow key={index} className='items-center border-none'>
                <TableCell className='h-9 w-[40px] items-start text-end font-medium'>
                  {symbol}
                </TableCell>
                <TableCell className='h-9 w-[80px] text-center'>
                  <span className='min-w-2'>${level.price.toFixed(2)}</span>
                </TableCell>
                <TableCell className='h-9'>
                  <TopLitFlowProgressBar
                    percentage={calculatePercentage(
                      level.total_premium,
                      maxPremium
                    )}
                  />
                </TableCell>
                <TableCell className='h-9 w-[20px] text-right font-medium'>
                  ${formatNumber(level.total_premium)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

export default TickerLitLevels
