import FlowContractModal from '@/app/pages/flow/components/flow-contract-modal'
import { TableCell, TableHead, TableRow } from '@/components/ui/table'
// import { useDashboard } from '@/hooks/use-dashboard'
// import { useMarketDashboardQueryKeys } from '@/hooks/use-query-keys'
// import { latestTradeDateAtom } from '@/store/store'
// import { useAtom } from 'jotai'
import moment from 'moment'
import { useState } from 'react'

const TopBlockOrders = () => {
  // const [latest_trade_date] = useAtom(latestTradeDateAtom)
  // const queryKeys = useMarketDashboardQueryKeys()
  const [currentPage, setCurrentPage] = useState(1)
  const itemsPerPage = 10

  const data = [
    {
      _source: {
        ticker: 'AAPL',
        strike: '190',
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
        ticker: 'TSLA',
        strike: '250',
        type: 'put',
        expiration: '2024-05-17',
        daily_volume: '800',
        open_interest: '15000',
        mid_price: '8.50',
        net_premium: 680000,
      },
    },
    {
      _source: {
        ticker: 'NVDA',
        strike: '800',
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
        ticker: 'META',
        strike: '450',
        type: 'call',
        expiration: '2024-06-14',
        daily_volume: '1200',
        open_interest: '20000',
        mid_price: '6.80',
        net_premium: 816000,
      },
    },
    {
      _source: {
        ticker: 'AMD',
        strike: '160',
        type: 'put',
        expiration: '2024-05-31',
        daily_volume: '900',
        open_interest: '18000',
        mid_price: '5.40',
        net_premium: 486000,
      },
    },
    {
      _source: {
        ticker: 'INTC',
        strike: '45',
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
        ticker: 'NFLX',
        strike: '600',
        type: 'put',
        expiration: '2024-07-05',
        daily_volume: '750',
        open_interest: '16000',
        mid_price: '15.30',
        net_premium: 1147500,
      },
    },
    {
      _source: {
        ticker: 'ADBE',
        strike: '550',
        type: 'call',
        expiration: '2024-05-24',
        daily_volume: '600',
        open_interest: '12000',
        mid_price: '9.75',
        net_premium: 585000,
      },
    },
    {
      _source: {
        ticker: 'CRM',
        strike: '280',
        type: 'call',
        expiration: '2024-06-28',
        daily_volume: '850',
        open_interest: '17000',
        mid_price: '7.20',
        net_premium: 612000,
      },
    },
    {
      _source: {
        ticker: 'PYPL',
        strike: '65',
        type: 'put',
        expiration: '2024-07-12',
        daily_volume: '700',
        open_interest: '14000',
        mid_price: '2.85',
        net_premium: 199500,
      },
    },
    {
      _source: {
        ticker: 'MSFT',
        strike: '420',
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
        ticker: 'GOOGL',
        strike: '150',
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
        ticker: 'SPY',
        strike: '500',
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
        ticker: 'QQQ',
        strike: '420',
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
        ticker: 'AMZN',
        strike: '180',
        type: 'call',
        expiration: '2024-10-18',
        daily_volume: '1700',
        open_interest: '27000',
        mid_price: '7.40',
        net_premium: 1258000,
      },
    },
    {
      _source: {
        ticker: 'IWM',
        strike: '200',
        type: 'put',
        expiration: '2024-11-15',
        daily_volume: '950',
        open_interest: '21000',
        mid_price: '4.30',
        net_premium: 408500,
      },
    },
    {
      _source: {
        ticker: 'DIA',
        strike: '380',
        type: 'call',
        expiration: '2024-12-20',
        daily_volume: '1100',
        open_interest: '23000',
        mid_price: '8.90',
        net_premium: 979000,
      },
    },
    {
      _source: {
        ticker: 'XLK',
        strike: '175',
        type: 'put',
        expiration: '2024-11-22',
        daily_volume: '800',
        open_interest: '19000',
        mid_price: '3.20',
        net_premium: 256000,
      },
    },
    {
      _source: {
        ticker: 'XLF',
        strike: '38',
        type: 'call',
        expiration: '2024-12-27',
        daily_volume: '1200',
        open_interest: '25000',
        mid_price: '1.95',
        net_premium: 234000,
      },
    },
    {
      _source: {
        ticker: 'EEM',
        strike: '42',
        type: 'put',
        expiration: '2024-10-25',
        daily_volume: '650',
        open_interest: '17000',
        mid_price: '2.30',
        net_premium: 149500,
      },
    },
  ]

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

  const results = data || []
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
    <div className='flex h-[380px] w-full flex-col px-4 py-4'>
      <h1 className='mb-2 pl-2 text-start text-base'>
        <span className='font-normal'>Top Block Orders</span>
      </h1>
      <div className='flex-1'>
        <table className='w-full text-[12px]' style={{ tableLayout: 'fixed' }}>
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
    </div>
  )
}

export default TopBlockOrders
