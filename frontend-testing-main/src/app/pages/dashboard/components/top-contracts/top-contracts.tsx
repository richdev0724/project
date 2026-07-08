import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
// import { useDashboard } from '@/hooks/use-dashboard'
// import { useMarketDashboardQueryKeys } from '@/hooks/use-query-keys'
// import { latestTradeDateAtom } from '@/store/store'
// import { useAtom } from 'jotai'
import moment from 'moment'
import { useState } from 'react'
import FlowContractModal from '../../../flow/components/flow-contract-modal'
import TopContractProgressBar from './top-contract-progress-bar'
import Loader from '@/components/loader'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/**
 * Component that renders the Top Contracts table.
 *
 * @returns The Top Contracts table component.
 */
const TopContracts = () => {
  const [selectedOption, setSelectedOption] = useState<string>('premium')
  // const [latest_trade_date] = useAtom(latestTradeDateAtom)
  // const queryKeys = useMarketDashboardQueryKeys()
  const data = [
    {
      ticker: {
        buckets: [
          {
            key: ['AAPL', 190, '2024-06-21', 'call'],
            below_or_at_bid_percentage: { value: 35 },
            at_mid_percentage: { value: 30 },
            at_or_above_ask_percentage: { value: 35 },
          },
        ],
      },
      total_net_premium: { value: 2850000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['TSLA', 250, '2024-05-17', 'put'],
            below_or_at_bid_percentage: { value: 40 },
            at_mid_percentage: { value: 25 },
            at_or_above_ask_percentage: { value: 35 },
          },
        ],
      },
      total_net_premium: { value: 1950000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['NVDA', 800, '2024-07-19', 'call'],
            below_or_at_bid_percentage: { value: 30 },
            at_mid_percentage: { value: 35 },
            at_or_above_ask_percentage: { value: 35 },
          },
        ],
      },
      total_net_premium: { value: 3750000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['META', 450, '2024-06-14', 'call'],
            below_or_at_bid_percentage: { value: 33 },
            at_mid_percentage: { value: 34 },
            at_or_above_ask_percentage: { value: 33 },
          },
        ],
      },
      total_net_premium: { value: 2250000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['AMD', 160, '2024-05-31', 'put'],
            below_or_at_bid_percentage: { value: 35 },
            at_mid_percentage: { value: 30 },
            at_or_above_ask_percentage: { value: 35 },
          },
        ],
      },
      total_net_premium: { value: 1650000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['MSFT', 420, '2024-06-28', 'call'],
            below_or_at_bid_percentage: { value: 32 },
            at_mid_percentage: { value: 36 },
            at_or_above_ask_percentage: { value: 32 },
          },
        ],
      },
      total_net_premium: { value: 2950000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['GOOGL', 150, '2024-05-24', 'call'],
            below_or_at_bid_percentage: { value: 34 },
            at_mid_percentage: { value: 33 },
            at_or_above_ask_percentage: { value: 33 },
          },
        ],
      },
      total_net_premium: { value: 1850000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['SPY', 500, '2024-07-12', 'put'],
            below_or_at_bid_percentage: { value: 36 },
            at_mid_percentage: { value: 28 },
            at_or_above_ask_percentage: { value: 36 },
          },
        ],
      },
      total_net_premium: { value: 3250000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['QQQ', 420, '2024-06-07', 'call'],
            below_or_at_bid_percentage: { value: 33 },
            at_mid_percentage: { value: 34 },
            at_or_above_ask_percentage: { value: 33 },
          },
        ],
      },
      total_net_premium: { value: 2150000 },
    },
    {
      ticker: {
        buckets: [
          {
            key: ['AMZN', 180, '2024-05-31', 'put'],
            below_or_at_bid_percentage: { value: 35 },
            at_mid_percentage: { value: 30 },
            at_or_above_ask_percentage: { value: 35 },
          },
        ],
      },
      total_net_premium: { value: 1750000 },
    },
  ]
  const isLoading = false
  const isError = false

  const handleSelectChange = (value: string) => {
    setSelectedOption(value)
  }

  const formatData = (value: number) => {
    if (value >= 1000000000) {
      return (
        <>
          {Math.floor(value / 1000000000)}
          <span className='text-[#FF9500]'>B</span>
        </>
      )
    } else if (value >= 1000000) {
      return (
        <>
          {Math.floor(value / 1000000)}
          <span className='text-[#FF9500]'>M</span>
        </>
      )
    } else if (value >= 1000) {
      return (
        <>
          {Math.floor(value / 1000)}
          <span className='text-[#FF9500]'>K</span>
        </>
      )
    }
    return value.toString()
  }

  if (isLoading) {
    return (
      <div className='flex h-[444px]'>
        <Loader />
      </div>
    )
  }

  if (isError) {
    return <div></div>
  }

  return (
    <div className='px-2'>
      <div className='flex items-center justify-between'>
        <div>
          <h2>Top Contracts</h2>
        </div>
        <div>
          <Select value={selectedOption} onValueChange={handleSelectChange}>
            <SelectTrigger className='w-[110px] justify-end !border-none !bg-transparent px-0 hover:!bg-transparent focus:!bg-transparent focus-visible:!outline-none focus-visible:!ring-0 focus-visible:!ring-offset-0'>
              <SelectValue placeholder='Select option' />
            </SelectTrigger>
            <SelectContent className='!border-0 bg-background !shadow-none [&>div>*[data-highlighted]]:!bg-transparent [&>div>*[data-state=checked]]:!bg-transparent [&>div>*]:!rounded-none [&>div>*]:!bg-transparent [&>div>*]:hover:!bg-transparent [&>div>*]:focus:!bg-transparent'>
              <SelectItem
                value='premium'
                className='focus:bg-transparent focus:text-current'
              >
                Premium
              </SelectItem>
              <SelectItem
                value='size'
                className='focus:bg-transparent focus:text-current'
              >
                Size
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className='mt-[10px]'>
        <Table className='text-[13px]'>
          <TableBody>
            {data.length > 0 ? (
              data.map((contract: any) => {
                return (
                  <TableRow className='items-center border-none'>
                    <TableCell className='h-9 w-[40px] text-end font-medium'>
                      {contract.ticker.buckets[0].key[0]}
                    </TableCell>
                    <TableCell className='flex h-9 w-[150px] items-center'>
                      <span className='min-w-10 text-end'>
                        {Math.ceil(contract.ticker.buckets[0].key[1])}
                      </span>
                      <span
                        className={`min-w-10 text-center font-medium ${contract.ticker.buckets[0].key[3] === 'call' ? 'text-callPrimaryColor' : 'text-putPrimaryColor'}`}
                      >
                        {contract.ticker.buckets[0].key[3]}
                      </span>
                      <FlowContractModal>
                        <span className='min-w-12 cursor-pointer text-start underline'>
                          {moment(contract.ticker.buckets[0].key[2]).format(
                            'MM/DD/YY'
                          )}
                        </span>
                      </FlowContractModal>
                    </TableCell>
                    <TableCell className='h-9'>
                      <TopContractProgressBar
                        bidOrBelowBidPercentage={
                          contract.ticker.buckets[0].below_or_at_bid_percentage
                            .value
                        }
                        midPercentage={
                          contract.ticker.buckets[0].at_mid_percentage.value
                        }
                        askOrAboveAskPercentage={
                          contract.ticker.buckets[0].at_or_above_ask_percentage
                            .value
                        }
                        type={contract.ticker.buckets[0].key[3]}
                      />
                    </TableCell>
                    <TableCell className='h-9 w-[20px] text-right font-medium'>
                      ${formatData(contract.total_net_premium.value)}
                    </TableCell>
                  </TableRow>
                )
              })
            ) : (
              <TableRow>
                <TableCell colSpan={6} className='[200px] h-9 text-center'>
                  No records
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

export default TopContracts
