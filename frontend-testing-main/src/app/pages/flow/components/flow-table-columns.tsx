import FlowIcon from '@/assets/icons/flowIcon'
import { Badge } from '@/components/ui/badge'
import { EMPTY_CELL } from '@/constants'
import { ColumnDef } from '@tanstack/react-table'
import moment from 'moment-timezone'
import { Link } from 'react-router-dom'
import { OptionTrade } from '../data/schema'
import { BookmarkManager } from './bookmark-manager'
import FlowContractModal from './flow-contract-modal'
import { DataTableColumnHeader } from './flow-table-column-header'
import { RiBarChartGroupedFill } from 'react-icons/ri'

export const formatValue = (value: any) => {
  const numericValue = parseFloat(value)
  return numericValue % 1 === 0
    ? numericValue.toString()
    : numericValue.toFixed(2)
}

export const formatNumber = (num: number) => {
  if (!num) {
    return '0'
  }

  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

export const columns: ColumnDef<OptionTrade>[] = [
  {
    accessorKey: 'id',
    header: () => (
      <div className='flex w-[30px]'>
        <DataTableColumnHeader title='' />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex w-[30px] items-center justify-start'>
        <BookmarkManager row={row} />
        <FlowIcon fill='#8B8D8D' />
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'ticker',
    header: () => (
      <div className='flex w-[30px] items-center justify-center'>
        <DataTableColumnHeader title='Ticker' />
      </div>
    ),
    cell: ({ row }) => {
      const ticker = row.original._source.ticker
      return (
        <Link
          className='flex w-[30px] items-center justify-start text-[10px] font-medium text-white hover:underline'
          to={`/ticker-overview/${ticker}`}
          target='_blank'
        >
          {ticker}
        </Link>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'bid',
    header: () => (
      <div className='flex w-[30px] items-center justify-center'>
        <DataTableColumnHeader title='Side' />
      </div>
    ),
    cell: ({ row }) => {
      const price = parseFloat(row.original._source.price)
      const bidPrice = parseFloat(row.original._source.bid)
      const askPrice = parseFloat(row.original._source.ask)

      let textClass = ''
      let borderClass = ''
      let label = ''

      // Check if bid/ask are 0 but spot price exists
      const midPrice = parseFloat(row.original._source.mid_price)

      // If bid equals ask (no spread), show NONE
      if (
        !isNaN(bidPrice) &&
        !isNaN(askPrice) &&
        bidPrice !== 0 &&
        askPrice !== 0 &&
        bidPrice === askPrice
      ) {
        textClass = 'text-gray-200'
        label = 'NONE'
      } else if (
        !isNaN(price) &&
        price !== 0 &&
        (bidPrice === 0 || askPrice === 0 || isNaN(bidPrice) || isNaN(askPrice))
      ) {
        textClass = 'text-gray-200'
        label = 'NONE'
      } else if (
        !isNaN(price) &&
        !isNaN(bidPrice) &&
        !isNaN(askPrice) &&
        bidPrice !== 0 &&
        askPrice !== 0
      ) {
        if (!isNaN(midPrice)) {
          if (price > midPrice && price <= askPrice) {
            textClass = 'text-[#24d438]'
            label = 'ASK'
          } else if (price < midPrice && price >= bidPrice) {
            textClass = 'text-[#DC4034]'
            label = 'BID'
          } else if (price > askPrice) {
            textClass = 'text-successLight'
            borderClass = 'border border-successLight'
            label = 'A ASK'
          } else if (price < bidPrice) {
            textClass = 'text-danger'
            borderClass = 'border border-danger'
            label = 'B BID'
          } else if (price === midPrice) {
            textClass = 'text-primary'
            label = 'MID'
          } else {
            // Fallback for any other case inside the spread
            textClass = 'text-muted'
            borderClass = 'border border-muted'
            label = 'NO SIDE'
          }
        } else {
          // If midPrice is not available, fallback to old logic
          if (price > askPrice) {
            textClass = 'text-successLight'
            borderClass = 'border border-successLight'
            label = 'A ASK'
          } else if (price === askPrice) {
            textClass = 'text-[#24d438]'
            label = 'ASK'
          } else if (price < bidPrice) {
            textClass = 'text-danger'
            borderClass = 'border border-danger'
            label = 'B BID'
          } else if (price === bidPrice) {
            textClass = 'text-[#DC4034]'
            label = 'BID'
          } else {
            // Inside spread
            const bidDistance = price - bidPrice
            const askDistance = askPrice - price

            if (bidDistance === askDistance) {
              textClass = 'text-primary'
              label = 'MID'
            } else if (bidDistance > askDistance) {
              textClass = 'text-successLight'
              borderClass = 'border border-successLight'
              label = 'A ASK'
            } else {
              textClass = 'text-danger'
              borderClass = 'border border-danger'
              label = 'B BID'
            }
          }
        }
      } else {
        // Fallback for invalid numbers
        textClass = 'text-muted'
        borderClass = 'border border-muted'
        label = 'NO SIDE'
      }

      return (
        <div className='flex w-[30px] items-center justify-center p-0'>
          <div
            className={`rounded-sm px-2 py-0 text-[10px] font-normal ${textClass} ${borderClass}`}
          >
            {label}
          </div>
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'contract',
    header: () => (
      <div className='flex w-[110px] justify-center text-primary'>
        <DataTableColumnHeader title='Contract' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.strike
      const type = row.original._source.type
      const expiration = row.original._source.expiration

      return value ? (
        <div className='flex w-[110px] items-center text-[10px] font-normal'>
          <span className='min-w-12 text-end'>{formatValue(value)}</span>
          <span
            className={`min-w-8 text-center text-[10px] font-medium ${
              type === 'call' ? 'text-callPrimaryColor' : 'text-putPrimaryColor'
            }`}
          >
            {type === 'call' ? 'call' : 'put'}
          </span>
          <FlowContractModal>
            <div className='group flex items-center gap-1'>
              <span className='min-w-12 cursor-pointer text-start text-[10px] text-white transition-colors group-hover:text-gray-300 group-hover:underline'>
                {expiration ? moment(expiration).format('MM/DD/YYYY') : ''}
              </span>
              <RiBarChartGroupedFill className='h-3 w-3 font-thin text-white transition-colors group-hover:text-gray-300' />
            </div>
          </FlowContractModal>
        </div>
      ) : (
        <div className='w-[90px] text-center text-[10px]'>{EMPTY_CELL}</div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'dte',
    header: () => (
      <div className='flex w-[10px] justify-center'>
        <DataTableColumnHeader title='DTE' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.dte
      return (
        <div className='flex w-[10px] items-center justify-center text-center text-[10px] font-normal'>
          {value === 0
            ? '0d'
            : value < 0
              ? `${value}d`
              : `${value.toLocaleString()}d`}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'underlying_asset_price',
    header: () => (
      <div className='flex w-[50px] justify-center'>
        <DataTableColumnHeader title='Stock' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.underlying_asset_price
      return (
        <div className='w-[50px] items-center justify-center text-center text-[10px] font-normal'>
          {value !== undefined && value !== null && value !== 0
            ? `$${Number(value).toFixed(2)}`
            : EMPTY_CELL}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'bid_ask',
    header: () => (
      <div className='flex w-[80px] items-center justify-center'>
        <DataTableColumnHeader title='Bid/Ask' />
      </div>
    ),
    cell: ({ row }) => {
      const bidValue = row.original._source.bid
      const askValue = row.original._source.ask

      return (
        <div className='w-[80px] items-center justify-center text-center text-[10px] font-normal'>
          <span>${bidValue ?? '0.00'}</span>
          {' - '}
          <span>${askValue ?? '0.00'}</span>
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'price',
    header: () => (
      <div className='flex w-[35px] items-center justify-center'>
        <DataTableColumnHeader title='Spot' />
      </div>
    ),
    cell: ({ row }) => {
      const value = parseFloat(row.original._source.price)
      return (
        <div className='w-[35px] items-center justify-center text-center text-[10px] font-normal'>
          {value ? `${value.toFixed(2)}` : EMPTY_CELL}
          {/* {value ? `${value.toFixed(2)}` : '0.45'} */}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'size',
    header: () => (
      <div className='flex w-[45px] items-center justify-center'>
        <DataTableColumnHeader title='Size' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.size
      const isLargeSize = value > 1000
      // const openInterest = row.original._source.open_interest
      return (
        <div
          className={`w-[45px] items-center justify-center text-center text-[10px] ${
            isLargeSize ? 'font-bold text-[#FF9500]' : 'font-normal'
          }`}
        >
          {value}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'net_premium',
    header: () => (
      <div className='flex w-[50px] items-center justify-center'>
        <DataTableColumnHeader title='Premium' />
      </div>
    ),
    cell: ({ row }) => {
      const value = parseFloat(row.original._source.net_premium)
      const premiumValue = formatNumber(value)
      const isOverThreshold = value > 4990000 // Over $4.99M

      // Check if it ends with M or K
      const hasM = premiumValue.includes('M')
      const hasK = premiumValue.includes('K')

      let numberPart = ''

      if (hasM) {
        const parts = premiumValue.split('M')
        numberPart = parts[0]
      } else if (hasK) {
        const parts = premiumValue.split('K')
        numberPart = parts[0]
      } else {
        numberPart = premiumValue
      }

      return (
        <div
          className={`w-[50px] items-center justify-center text-center text-[10px] ${
            isOverThreshold ? 'rounded-sm ring-1 ring-[#FF9500]' : ''
          }`}
        >
          <span
            className={`${isOverThreshold ? 'font-bold text-[#FF9500]' : ''}`}
          >
            ${numberPart}
          </span>
          {hasM && (
            <span
              className={`font-bold ${isOverThreshold ? 'text-[#FF9500]' : 'text-[#D47FFF]'}`}
            >
              M
            </span>
          )}
          {hasK && <span className='font-bold text-green-500'>K</span>}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'daily_volume',
    header: () => (
      <div className='flex w-[60px] items-center justify-center'>
        <DataTableColumnHeader title='Volume' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.daily_volume

      return (
        <div
          className={`w-[60px] items-center justify-center text-center text-[10px] font-normal`}
        >
          {value ? value.toString() : EMPTY_CELL}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  // {
  //   accessorKey: 'daily_volume',
  //   header: () => (
  //     <div className='flex w-[40px] items-center justify-center'>
  //       <DataTableColumnHeader column={column} title='Volume' />
  //     </div>
  //   ),
  //   cell: ({ row }) => {
  //     let value = parseFloat(row.getValue('daily_volume'))
  //     if (!value) {
  //       // Generate a random three-digit number
  //       value = Math.floor(100 + Math.random() * 900)
  //     }

  //     return (
  //       <div className=' flex w-[40px] items-center justify-center text-center font-normal'>
  //         {value.toLocaleString()}
  //       </div>
  //     )
  //   },
  //   enableSorting: false,
  //   enableHiding: false,
  // },

  {
    accessorKey: 'open_interest',
    header: () => (
      <div className='flex w-[50px] items-center justify-center'>
        <DataTableColumnHeader title='OI' />
      </div>
    ),
    cell: ({ row }) => {
      const value = row.original._source.open_interest
      return (
        <div
          className={`w-[50px] items-center justify-center text-center text-[10px] font-normal`}
        >
          {value.toLocaleString()}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  // {
  //   accessorKey: 'open_interest',
  //   header: () => (
  //     <div className='flex w-[60px] items-center justify-center'>
  //       <DataTableColumnHeader column={column} title='OI' />
  //     </div>
  //   ),
  //   cell: ({ row }) => {
  //     let value = parseFloat(row.getValue('open_interest'))
  //     // if (!value) {
  //     //   // Generate a random three-digit number
  //     //   value = Math.floor(100 + Math.random() * 900)
  //     // }

  //     return (
  //       <div className='flex w-[60px] items-center justify-center font-normal'>
  //         {value.toLocaleString()}
  //       </div>
  //     )
  //   },
  //   enableSorting: false,
  //   enableHiding: false,
  //   },

  {
    accessorKey: 'legs',
    header: () => (
      <div className='flex w-[40px] items-center justify-center'>
        <DataTableColumnHeader title='Legs' />
      </div>
    ),
    cell: ({ row }) => {
      const conditions = row.original._source.conditions || []

      // Check for ML conditions: 232, 233, 234, 235, 236, 238, 239, 246
      const mlConditions = [
        '232',
        '233',
        '234',
        '235',
        '236',
        '238',
        '239',
        '246',
      ]
      const hasML = conditions.some((condition) =>
        mlConditions.includes(condition)
      )

      // If has ML conditions, show ML; otherwise assume single leg (SL)
      const displayText = hasML ? 'ML/' : 'SL/'

      return (
        <div className='flex w-[40px] items-center justify-center text-[10px] font-medium'>
          {displayText}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },

  {
    accessorKey: 'flags',
    header: () => (
      <div className='flex w-[140px] items-center justify-start'>
        <DataTableColumnHeader title='Flags' />
      </div>
    ),
    cell: ({ row }) => {
      const price = parseFloat(row.original._source.price)
      const bidPrice = parseFloat(row.original._source.bid)
      const askPrice = parseFloat(row.original._source.ask)
      const midPrice = parseFloat(row.original._source.mid_price)
      const contractType = row.original._source.type
      const commonStyles = 'text-[8px] font-normal px-1 py-0 rounded-sm'
      const label = []

      // Determine side based on same logic as side column
      let side = ''
      if (
        !isNaN(price) &&
        price !== 0 &&
        (bidPrice === 0 || askPrice === 0 || isNaN(bidPrice) || isNaN(askPrice))
      ) {
        side = 'NONE'
      } else if (
        !isNaN(price) &&
        !isNaN(bidPrice) &&
        !isNaN(askPrice) &&
        bidPrice !== 0 &&
        askPrice !== 0
      ) {
        if (!isNaN(midPrice)) {
          if (price > midPrice && price <= askPrice) {
            side = 'ASK'
          } else if (price < midPrice && price >= bidPrice) {
            side = 'BID'
          } else if (price > askPrice) {
            side = 'A ASK'
          } else if (price < bidPrice) {
            side = 'B BID'
          } else if (price === midPrice) {
            side = 'MID'
          }
        } else {
          // Fallback logic if no mid price
          if (price > askPrice) {
            side = 'A ASK'
          } else if (price === askPrice) {
            side = 'ASK'
          } else if (price < bidPrice) {
            side = 'B BID'
          } else if (price === bidPrice) {
            side = 'BID'
          } else {
            const bidDistance = price - bidPrice
            const askDistance = askPrice - price
            if (bidDistance === askDistance) {
              side = 'MID'
            } else if (bidDistance > askDistance) {
              side = 'A ASK'
            } else {
              side = 'B BID'
            }
          }
        }
      }

      /**
       * Bullish flag conditions:
       * - Call with ASK or A ASK side
       * - Put with BID or B BID side
       */
      if (
        (contractType === 'call' && (side === 'ASK' || side === 'A ASK')) ||
        (contractType === 'put' && (side === 'BID' || side === 'B BID'))
      ) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'bullish'}>
              BULLISH
            </Badge>
          </div>
        )
      }

      /**
       * Bearish flag conditions:
       * - Call with BID or B BID side
       * - Put with ASK or A ASK side
       */
      if (
        (contractType === 'call' && (side === 'BID' || side === 'B BID')) ||
        (contractType === 'put' && (side === 'ASK' || side === 'A ASK'))
      ) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'bearish'}>
              BEARISH
            </Badge>
          </div>
        )
      }
      /**
       * Aggressive flag conditions:
       * - Show AGGRESSIVE only for B BID or A ASK trades
       */
      if (side === 'B BID' || side === 'A ASK') {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'aggressive'}>
              AGGRESSIVE
            </Badge>
          </div>
        )
      }

      /**
       * Neutral flag conditions:
       * - Show NEUTRAL for NONE or MID trades
       */
      if (side === 'NONE' || side === 'MID') {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'neutral'}>
              NEUTRAL
            </Badge>
          </div>
        )
      }

      // Hide SIZE > OI flag for now
      // if (row.original._source.size > row.original._source.open_interest) {
      //   label.push(
      //     <div className='flex justify-start items-center mr-1 text-center border-0'>
      //       <Badge className={`${commonStyles}`} variant={'sizeOi'}>
      //         SIZE {'>'} OI
      //       </Badge>
      //     </div>
      //   )
      // }

      if (row.original._source.multi_leg === 1) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'multiLeg'}>
              MULTI-LEG
            </Badge>
          </div>
        )
      }

      if (row.original._source.is_opening) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'openingTrade'}>
              OPENING TRADE
            </Badge>
          </div>
        )
      }

      if (row.original._source.is_floor === 1) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'floor'}>
              FLOOR
            </Badge>
          </div>
        )
      }

      if (row.original._source.is_sweep === 1) {
        label.push(
          <div className='mr-1 flex items-center justify-start border-0 text-center'>
            <Badge className={`${commonStyles}`} variant={'sweep'}>
              SWEEP
            </Badge>
          </div>
        )
      }

      return (
        <div className='flex h-6 w-full items-center justify-start border-0 text-center text-[10px]'>
          {label}
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'sip_timestamp',
    header: () => (
      <div className='flex w-[100px] items-center justify-center'>
        <DataTableColumnHeader title='Time Executed' />
      </div>
    ),
    cell: ({ row }) => {
      const sipTimeStamp = row.original._source.sip_timestamp

      if (!sipTimeStamp)
        return (
          <div className='flex w-[100px] items-center justify-center text-center text-[10px] font-normal'>
            {EMPTY_CELL}
          </div>
        )

      const date = moment.tz(sipTimeStamp, 'America/New_York').format('MM/DD')
      const time = moment
        .tz(sipTimeStamp, 'America/New_York')
        .format('HH:mm:ss')

      return (
        <div className='flex w-[100px] items-center justify-center space-x-1 text-[10px] font-normal'>
          <span className='w-8 text-end'>{date}</span>
          <span className='w-2 text-center'>•</span>
          <span className='w-14 text-start'>{time}</span>
        </div>
      )
    },
    enableSorting: false,
    enableHiding: false,
  },
]
