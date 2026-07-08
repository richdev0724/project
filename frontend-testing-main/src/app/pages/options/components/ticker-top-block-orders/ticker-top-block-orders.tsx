import FlowContractModal from '@/app/pages/flow/components/flow-contract-modal'
import Loader from '@/components/loader'
import { TableCell, TableHead, TableRow } from '@/components/ui/table'
import { useTickerOverviewQueryKeys } from '@/hooks/use-query-keys'
import { useTickerOverview } from '@/hooks/use-ticker-overview'
import { latestTradeDateAtom } from '@/store/store'
import { useAtom } from 'jotai'
import moment from 'moment'
import { useParams } from 'react-router-dom'
import { useState } from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { generateModalScreenshot, copyImageToClipboard, downloadImage } from '@/services/screenshot.service'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

const TickerTopBlockOrders = () => {
  const { symbol } = useParams()
  const [latest_trade_date] = useAtom(latestTradeDateAtom)
  const queryKeys = useTickerOverviewQueryKeys()
  const [currentPage, setCurrentPage] = useState(1)
  const [selectedOption, setSelectedOption] = useState<string>('premium')
  const itemsPerPage = 10

  // Mock data for development
  const mockData = {
    top_block_orders: {
      results: [
        {
          _source: {
            ticker: symbol,
            strike: '420',
            type: 'call',
            expiration: '2024-06-21',
            daily_volume: '1500',
            open_interest: '25000',
            mid_price: '4.25',
            net_premium: 637500,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '415',
            type: 'put',
            expiration: '2024-05-17',
            daily_volume: '750',
            open_interest: '15000',
            mid_price: '8.50',
            net_premium: 680000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '425',
            type: 'call',
            expiration: '2024-07-19',
            daily_volume: '2000',
            open_interest: '35000',
            mid_price: '12.75',
            net_premium: 2550000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '410',
            type: 'put',
            expiration: '2024-05-31',
            daily_volume: '580',
            open_interest: '18000',
            mid_price: '5.40',
            net_premium: 486000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '430',
            type: 'call',
            expiration: '2024-06-07',
            daily_volume: '1100',
            open_interest: '22000',
            mid_price: '3.20',
            net_premium: 352000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '435',
            type: 'call',
            expiration: '2024-08-16',
            daily_volume: '1800',
            open_interest: '28000',
            mid_price: '6.75',
            net_premium: 1215000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '405',
            type: 'put',
            expiration: '2024-07-26',
            daily_volume: '1300',
            open_interest: '20000',
            mid_price: '4.90',
            net_premium: 637000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '440',
            type: 'call',
            expiration: '2024-06-28',
            daily_volume: '2200',
            open_interest: '32000',
            mid_price: '7.80',
            net_premium: 1716000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '400',
            type: 'put',
            expiration: '2024-09-20',
            daily_volume: '320',
            open_interest: '16000',
            mid_price: '3.85',
            net_premium: 365750,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '445',
            type: 'call',
            expiration: '2024-08-02',
            daily_volume: '1600',
            open_interest: '24000',
            mid_price: '5.60',
            net_premium: 896000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '450',
            type: 'put',
            expiration: '2024-09-13',
            daily_volume: '1400',
            open_interest: '19000',
            mid_price: '6.20',
            net_premium: 868000,
          },
        },
        {
          _source: {
            ticker: symbol,
            strike: '455',
            type: 'call',
            expiration: '2024-10-18',
            daily_volume: '1700',
            open_interest: '27000',
            mid_price: '7.40',
            net_premium: 1258000,
          },
        },
        ...Array.from({ length: 28 }, (_, i) => ({
          _source: {
            ticker: symbol,
            strike: String(460 + i * 5),
            type: i % 2 === 0 ? 'call' : 'put',
            expiration: `2024-${String(Math.floor(Math.random() * 5) + 6).padStart(2, '0')}-${String(Math.floor(Math.random() * 28) + 1).padStart(2, '0')}`,
            daily_volume: String(Math.floor(Math.random() * 2500) + 400),
            open_interest: String(Math.floor(Math.random() * 35000) + 12000),
            mid_price: (Math.random() * 9 + 3.5).toFixed(2),
            net_premium: Math.floor(Math.random() * 2500000) + 400000,
          },
        })),
      ],
    },
  }

  const { data: TopBlockOrdersData, isLoading } = useTickerOverview(
    symbol ?? '',
    [
      {
        widget: 'top_block_orders',
        payload: {
          timespan: undefined,
          ticker: undefined,
          latest_trade_date: latest_trade_date || undefined,
          sortBy: undefined,
        },
      },
    ],
    queryKeys.topBlockOrdersWidgetKey()
  )

  const formatData = (value: number) => {
    if (value >= 1000000000) {
      return (
        <>
          ${Math.floor(value / 1000000000)}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>B</span>
        </>
      )
    } else if (value >= 1000000) {
      return (
        <span className='text-purpleHighLightText'>
          ${(value / 1000000).toFixed(1).replace(/\.0$/, '')}
          <span>M</span>
        </span>
      )
    } else if (value >= 1000) {
      return (
        <>
          ${(value / 1000).toFixed(1).replace(/\.0$/, '')}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>K</span>
        </>
      )
    }
    return '$' + value.toString()
  }

  if (isLoading) {
    return (
      <div className='h-[380px] w-full'>
        <Loader />
      </div>
    )
  }

  const handleSelectChange = (value: string) => {
    setSelectedOption(value)
  }

  const handleExport = async (type: 'copy' | 'save') => {
    try {
      // Get the modal container element
      const modalElement = document.querySelector('[data-modal="block-orders"]') as HTMLElement
      
      if (!modalElement) {
        return
      }

      // Generate screenshot
      const result = await generateModalScreenshot(modalElement, {
        width: 400,
        height: 400,
        type: 'png'
      })

      if (!result.success || !result.data) {
        return
      }

      if (type === 'copy') {
        // Copy to clipboard
        const success = await copyImageToClipboard(result.data)
        if (success) {
          // Image copied successfully
        } else {
          // Copy failed
        }
      } else if (type === 'save') {
        // Download image
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
        const filename = `block-orders-${symbol}-${timestamp}.png`
        downloadImage(result.data, filename)
        // Image saved
      }
    } catch (error) {
      // Export failed
    }
  }

  // Use mock data for development, fallback to API data in production
  const displayData = import.meta.env.DEV ? mockData : TopBlockOrdersData

  const results = displayData?.top_block_orders?.results || []
  const totalPages = Math.ceil(results.length / itemsPerPage)
  const startIndex = (currentPage - 1) * itemsPerPage
  const endIndex = startIndex + itemsPerPage
  const currentItems = results.slice(startIndex, endIndex)

  // Calculate dynamic strike width based on max digits
  const maxStrikeLength = Math.max(
    ...results.map((r: any) => String(r._source.strike).length),
    0
  )
  const strikeWidth =
    maxStrikeLength <= 2 ? 'w-6' : maxStrikeLength <= 3 ? 'w-8' : 'w-10'

  return (
    <div className='flex h-[390px] w-full flex-col px-4 py-4 relative' data-modal="block-orders">
      <div className='flex items-center justify-between'>
        <div>
          <h1 className='mb-2 pl-2 text-start text-base'>
            <span className='font-normal'>{symbol} Top Block Orders</span>
          </h1>
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
                value='volume'
                className='focus:bg-transparent focus:text-current'
              >
                Volume
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className='flex-1'>
        <table
          className='mt-2 w-full text-[12px]'
          style={{ tableLayout: 'fixed' }}
        >
          <colgroup>
            <col style={{ width: '35%' }} />
            <col style={{ width: '15%' }} />
            <col style={{ width: '15%' }} />
            <col style={{ width: '15%' }} />
            <col style={{ width: '20%' }} />
          </colgroup>
          <thead className='bg-transparent'>
            <TableRow className='items-center border-none'>
              <TableHead className='h-6 pr-2 text-center text-xs font-light'>
                Contract
              </TableHead>
              <TableHead className='h-6 text-center text-xs font-light'>
                Volume
              </TableHead>
              <TableHead className='h-6 text-center text-xs font-light'>
                OI
              </TableHead>
              <TableHead className='h-6 px-3 text-start text-xs font-light'>
                Spot
              </TableHead>
              <TableHead className='h-6 px-2 text-end text-xs font-light'>
                Premium
              </TableHead>
            </TableRow>
          </thead>
          <tbody key={currentPage} className='duration-300 animate-in fade-in'>
            {currentItems.length > 0 ? (
              currentItems.map((order: any, index: number) => (
                <TableRow
                  key={`${currentPage}-${index}`}
                  className='items-center border-none'
                >
                  <TableCell className='py-1 pr-2'>
                    <div className='flex items-center gap-0.5'>
                      <span className={`${strikeWidth} text-right font-light`}>
                        {order._source.strike}
                      </span>
                      <span
                        className={`w-10 text-center font-medium ${
                          order._source.type === 'call'
                            ? 'text-callSecondaryColor'
                            : 'text-putSecondaryColor'
                        }`}
                      >
                        {order._source.type}
                      </span>
                      <FlowContractModal>
                        <span className='w-16 cursor-pointer text-left font-light underline'>
                          {moment(order._source.expiration).format('M/DD/YY')}
                        </span>
                      </FlowContractModal>
                    </div>
                  </TableCell>
                  <TableCell
                    className={`px-3 py-1 text-center ${
                      order._source.daily_volume >= 1000
                        ? 'font-medium text-yellowHighLightText'
                        : 'font-light text-white'
                    }`}
                  >
                    {order._source.daily_volume}
                  </TableCell>
                  <TableCell className='px-3 py-1 text-center font-light'>
                    {order._source.open_interest}
                  </TableCell>
                  <TableCell className='px-3 py-1 text-start font-light'>
                    ${Number(order._source.mid_price).toFixed(2)}
                  </TableCell>
                  <TableCell className='px-2 py-1 text-end font-medium'>
                    {formatData(order._source.net_premium)}
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={5} className='h-[200px] text-center'>
                  No records
                </TableCell>
              </TableRow>
            )}
          </tbody>
        </table>
      </div>
      <div className='mt-2 flex items-center justify-between text-sm'>
        {currentPage > 1 && (
          <button
            onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
            className='rounded px-3 py-1 transition-colors hover:bg-gray-800'
          >
            &lt;
          </button>
        )}
        <div className='flex-1'></div>
        {currentPage < totalPages && (
          <button
            onClick={() =>
              setCurrentPage((prev) => Math.min(prev + 1, totalPages))
            }
            className='rounded px-3 py-1 transition-colors hover:bg-gray-800'
          >
            &gt;
          </button>
        )}
      </div>
      
      {/* Export Icon - Top Left Corner */}
      <div className='absolute -top-[15px] -left-[15px]'>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button className='flex h-6 w-6 items-center justify-center focus:outline-none focus:ring-0 focus:ring-offset-0'>
              <svg
                width="12"
                height="12"
                viewBox="0 0 12 12"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="text-gray-400/35"
                style={{ transform: "rotate(-90deg)" }}
              >
                {/* Rounded corner L shape */}
                <path d="M2 2 L7 2 A3 3 0 0 1 10 5 L10 10" />
              </svg>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align='start' className='w-32'>
            <DropdownMenuItem onClick={() => handleExport('copy')}>
              Copy Image
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => handleExport('save')}>
              Save Image
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  )
}

export default TickerTopBlockOrders
