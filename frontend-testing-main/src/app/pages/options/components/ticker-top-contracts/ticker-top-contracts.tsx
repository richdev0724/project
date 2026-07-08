import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import TopContractProgressBar from '@/app/pages/dashboard/components/top-contracts/top-contract-progress-bar'
import { useTickerOverviewQueryKeys } from '@/hooks/use-query-keys'
import { useTickerOverview } from '@/hooks/use-ticker-overview'
import { latestTradeDateAtom } from '@/store/store'
import { useAtom } from 'jotai'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import FlowContractModal from '../../../flow/components/flow-contract-modal'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import Loader from '@/components/loader'
import moment from 'moment'

/**
 * Component that renders the Top Contracts table.
 *
 * @returns The Top Contracts table component.
 */
const TickerTopContracts = () => {
  const { symbol } = useParams()
  const queryKeys = useTickerOverviewQueryKeys()
  const [selectedOption, setSelectedOption] = useState<string>('premium')
  const [latest_trade_date] = useAtom(latestTradeDateAtom)

  // Mock data for development
  const mockData = {
    top_contract_modal: {
      contracts: {
        buckets: [
          {
            ticker: {
              buckets: [
                {
                  key: [symbol, 420, '2024-06-21', 'call'],
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
                  key: [symbol, 415, '2024-05-17', 'put'],
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
                  key: [symbol, 425, '2024-07-19', 'call'],
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
                  key: [symbol, 410, '2024-06-14', 'put'],
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
                  key: [symbol, 430, '2024-05-31', 'call'],
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
                  key: [symbol, 435, '2024-08-16', 'call'],
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
                  key: [symbol, 405, '2024-07-26', 'put'],
                  below_or_at_bid_percentage: { value: 38 },
                  at_mid_percentage: { value: 32 },
                  at_or_above_ask_percentage: { value: 30 },
                },
              ],
            },
            total_net_premium: { value: 1850000 },
          },
          {
            ticker: {
              buckets: [
                {
                  key: [symbol, 405, '2024-07-26', 'put'],
                  below_or_at_bid_percentage: { value: 38 },
                  at_mid_percentage: { value: 32 },
                  at_or_above_ask_percentage: { value: 30 },
                },
              ],
            },
            total_net_premium: { value: 1850000 },
          },
          {
            ticker: {
              buckets: [
                {
                  key: [symbol, 440, '2024-06-28', 'call'],
                  below_or_at_bid_percentage: { value: 34 },
                  at_mid_percentage: { value: 33 },
                  at_or_above_ask_percentage: { value: 33 },
                },
              ],
            },
            total_net_premium: { value: 3150000 },
          },
          {
            ticker: {
              buckets: [
                {
                  key: [symbol, 400, '2024-09-20', 'put'],
                  below_or_at_bid_percentage: { value: 36 },
                  at_mid_percentage: { value: 31 },
                  at_or_above_ask_percentage: { value: 33 },
                },
              ],
            },
            total_net_premium: { value: 2450000 },
          },
        ],
      },
    },
  }

  const { data: topContractModalData, isLoading } = useTickerOverview(
    symbol ?? '',
    [
      {
        widget: 'top_contract_modal',
        payload: {
          timespan: undefined,
          latest_trade_date: latest_trade_date || undefined,
          sortBy: selectedOption,
          ticker: undefined,
        },
      },
    ],
    queryKeys.topContractModalWidgetKey(selectedOption)
  )

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

  // Use mock data for development, fallback to API data in production
  const displayData = import.meta.env.DEV ? mockData : topContractModalData

  return (
    <div className='px-4 py-4'>
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
            {displayData &&
            displayData.top_contract_modal &&
            displayData.top_contract_modal.contracts.buckets.length > 0 ? (
              displayData.top_contract_modal.contracts.buckets.map(
                (contract: any, index: number) => {
                  return (
                    <TableRow key={index} className='items-center border-none'>
                      <TableCell className='h-9 w-[40px] text-end font-medium'>
                        {contract.ticker.buckets[0].key[0]}
                      </TableCell>
                      <TableCell className='flex h-9 w-[150px] items-center'>
                        <span className='min-w-10 text-end'>
                          {contract.ticker.buckets[0].key[1]}
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
                            contract.ticker.buckets[0]
                              .below_or_at_bid_percentage.value
                          }
                          midPercentage={
                            contract.ticker.buckets[0].at_mid_percentage.value
                          }
                          askOrAboveAskPercentage={
                            contract.ticker.buckets[0]
                              .at_or_above_ask_percentage.value
                          }
                          type={contract.ticker.buckets[0].key[3]}
                        />
                      </TableCell>
                      <TableCell className='h-9 w-[20px] text-right font-medium'>
                        ${formatData(contract.total_net_premium.value)}
                      </TableCell>
                    </TableRow>
                  )
                }
              )
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

export default TickerTopContracts
