import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { useLitFlowScanner } from '@/hooks/use-lit-flow-scanner'
import TopLitFlowProgressBar from './top-lit-flow-progress-bar'
import Loader from '@/components/loader'

interface LitFlowScannerResult {
  status: string
  data?: any[]
  lastScanTime?: string
}

/**
 * Component that renders the Top Lit Flow table.
 *
 * @returns The Top Lit Flow table component.
 */
const TopLitFlow = () => {
  const { data, isLoading, error } = useLitFlowScanner() as {
    data: LitFlowScannerResult | undefined
    isLoading: boolean
    error: any
  }
  // Lit Flow Scanner Status

  // If loading, show loading indicator
  if (isLoading || data?.status === 'running') {
    return (
      <div className='px-2'>
        <div className='flex items-center justify-between py-2'>
          <div>
            <h2>Top Lit Flow</h2>
          </div>
        </div>
        <div className='mt-[8px] flex h-[323px] items-center justify-center'>
          <Loader />
        </div>
      </div>
    )
  }

  // Handle error state or empty data
  if (
    error ||
    !data ||
    !data.data ||
    !Array.isArray(data.data) ||
    data.data.length === 0
  ) {
    return (
      <div className='px-2'>
        <div className='flex items-center justify-between py-2'>
          <div>
            <h2>Top Lit Flow</h2>
          </div>
        </div>
        <div className='mt-[8px] flex h-[323px] items-center justify-center'>
          <span className='text-lg text-gray-400'>
            {error ? 'Error loading data' : 'No lit flow data available'}
          </span>
        </div>
      </div>
    )
  }

  const litFlowData = data.data

  const formatNumber = (num: number) => {
    if (num >= 1e9) return (num / 1e9).toFixed(1) + 'B'
    if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M'
    if (num >= 1e3) return (num / 1e3).toFixed(1) + 'K'
    return num.toFixed(1)
  }

  const calculatePercentage = (value: number, maxValue: number) => {
    return (value / maxValue) * 100
  }

  const maxPremium = litFlowData[0]?.total_premium || 0

  return (
    <div className='px-2'>
      <div className='flex items-center justify-between py-2'>
        <div>
          <h2>Top Lit Flow</h2>
        </div>
      </div>
      <div className='mt-[8px]'>
        <Table className='text-[13px]'>
          <TableBody>
            {litFlowData.map((trade: any, index: number) => (
              <TableRow key={index} className='items-center border-none'>
                <TableCell className='h-9 w-[40px] items-start text-end font-medium'>
                  {trade.ticker}
                </TableCell>
                <TableCell className='h-9 w-[80px] text-center'>
                  <span className='min-w-2'>${trade.price.toFixed(2)}</span>
                </TableCell>
                <TableCell className='h-9'>
                  <TopLitFlowProgressBar
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

export default TopLitFlow
