import { useInsiderTrades } from '@/hooks/use-insider-trades'
import moment from 'moment'
import Loader from '@/components/loader'
import { useParams } from 'react-router-dom'
import { TableCell, TableHead, TableRow } from '@/components/ui/table'

const TickerInsiderTrades = () => {
  const { symbol } = useParams()
  const { trades, isLoading, error } = useInsiderTrades(symbol || '', 50)

  if (!symbol) {
    return (
      <div className='flex h-[323px] w-full items-center justify-center'>
        <p className='text-gray-400'>No ticker symbol provided</p>
      </div>
    )
  }

  const formatNumber = (num: number) => {
    if (num >= 1000000) {
      return `${(num / 1000000).toFixed(2)}M`
    } else if (num >= 1000) {
      return `${(num / 1000).toFixed(1)}K`
    }
    return num.toLocaleString()
  }

  const formatPrice = (price: number) => {
    if (price === 0) return '$0.00'
    return `$${price.toFixed(2)}`
  }

  const formatValue = (shares: number, price: number) => {
    const value = Math.abs(shares * price)
    if (value === 0) return '$0'
    if (value >= 1000000) {
      return `$${(value / 1000000).toFixed(2)}M`
    } else if (value >= 1000) {
      return `$${(value / 1000).toFixed(0)}K`
    }
    return `$${value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
  }

  const getTransactionColor = (type: string) => {
    const normalizedType = type.toLowerCase()
    if (normalizedType.includes('sale') || normalizedType.includes('sell')) {
      return 'text-red-400'
    }
    if (normalizedType.includes('purchase') || normalizedType.includes('buy')) {
      return 'text-green-400'
    }
    return 'text-gray-300'
  }

  const capitalizeName = (name: string) => {
    // Split the name into parts
    const parts = name.trim().split(' ')

    // If we have at least 2 parts, assume format is "LASTNAME FIRSTNAME MIDDLENAME/INITIAL"
    // and reorder to "FIRSTNAME MIDDLENAME/INITIAL LASTNAME"
    if (parts.length >= 2) {
      const lastName = parts[0]
      const firstName = parts[1]
      const middleParts = parts.slice(2) // Could be middle name or initial

      // Reorder: First name, middle parts (if any), then last name
      const reordered = [firstName, ...middleParts, lastName].join(' ')

      // Capitalize each word
      return reordered
        .toLowerCase()
        .split(' ')
        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ')
    }

    // If only one part, just capitalize it
    return name.charAt(0).toUpperCase() + name.slice(1).toLowerCase()
  }

  const cleanTitle = (typeOfOwner: string) => {
    // Remove prefixes like "director, officer:", "officer:", "director,", "10 percent owner,"
    const cleaned = typeOfOwner
      .replace(/^director,?\s*/i, '')
      .replace(/^officer:\s*/i, '')
      .replace(/^10 percent owner,?\s*/i, '')
      .replace(/^insider,?\s*/i, '')
      .trim()

    // If the result is empty or just punctuation, return the original capitalized
    if (!cleaned || cleaned === ',') {
      // Capitalize first letter of each word
      return typeOfOwner.replace(/\b\w/g, (char) => char.toUpperCase())
    }

    // Capitalize first letter of cleaned result
    return cleaned.charAt(0).toUpperCase() + cleaned.slice(1)
  }

  const cleanTransactionType = (type: string) => {
    // Remove the code prefix (e.g., "S-Sale" -> "Sale", "P-Purchase" -> "Purchase")
    if (type.includes('-')) {
      return type.split('-')[1]
    }
    return type
  }

  return (
    <div className='h-[466px] w-full px-4 py-4'>
      <div className='mb-4 flex w-full items-center justify-between pl-2 text-base'>
        <div className='flex items-center gap-2'>
          <span className='font-normal'>{symbol} Insider Trades</span>
        </div>
      </div>
      {isLoading ? (
        <div className='flex h-[389px] items-center justify-center'>
          <Loader />
        </div>
      ) : error || !trades || trades.length === 0 ? (
        <div className='flex h-[389px] items-center justify-center'>
          <p className='text-gray-400'>
            {error ? 'Error loading insider trades' : 'No insider trades found'}
          </p>
        </div>
      ) : (
        <div className='relative max-h-[389px] w-full overflow-auto'>
          <table className='w-full caption-bottom text-sm'>
            <thead className='sticky top-0 z-20 bg-background'>
              <TableRow className='border-b border-gray-700 hover:bg-transparent'>
                <TableHead className='w-[60px] min-w-[60px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Ticker
                </TableHead>
                <TableHead className='w-[140px] min-w-[140px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Name
                </TableHead>
                <TableHead className='w-[180px] min-w-[180px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Title
                </TableHead>
                <TableHead className='w-[100px] min-w-[100px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Type
                </TableHead>
                <TableHead className='w-[110px] min-w-[110px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Security
                </TableHead>
                <TableHead className='w-[80px] min-w-[80px] bg-background px-3 py-2 text-right text-[10px] font-semibold text-gray-300'>
                  Size
                </TableHead>
                <TableHead className='w-[80px] min-w-[80px] bg-background px-3 py-2 text-right text-[10px] font-semibold text-gray-300'>
                  Price
                </TableHead>
                <TableHead className='w-[90px] min-w-[90px] bg-background px-3 py-2 text-right text-[10px] font-semibold text-gray-300'>
                  Value
                </TableHead>
                <TableHead className='w-[100px] min-w-[100px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  Trade Date
                </TableHead>
                <TableHead className='w-[100px] min-w-[100px] bg-background px-3 py-2 text-[10px] font-semibold text-gray-300'>
                  File Date
                </TableHead>
              </TableRow>
            </thead>
            <tbody>
              {trades.map((trade, index) => (
                <TableRow
                  key={index}
                  className='cursor-pointer border-b border-gray-800 transition-colors hover:bg-gray-800/30'
                  onClick={() =>
                    trade.link && window.open(trade.link, '_blank')
                  }
                >
                  <TableCell className='w-[60px] px-3 py-2 text-[11px] font-medium text-blue-400'>
                    {symbol}
                  </TableCell>
                  <TableCell
                    className='w-[140px] px-3 py-2 text-[11px]'
                    title={capitalizeName(trade.reportingName)}
                  >
                    <div className='truncate'>
                      {capitalizeName(trade.reportingName)}
                    </div>
                  </TableCell>
                  <TableCell
                    className='w-[180px] px-3 py-2 text-[11px] text-gray-400'
                    title={cleanTitle(trade.typeOfOwner)}
                  >
                    <div className='truncate'>
                      {cleanTitle(trade.typeOfOwner)}
                    </div>
                  </TableCell>
                  <TableCell
                    className={`w-[100px] px-3 py-2 text-[11px] font-medium ${getTransactionColor(trade.transactionType)}`}
                  >
                    {cleanTransactionType(trade.transactionType)}
                  </TableCell>
                  <TableCell className='w-[110px] px-3 py-2 text-[11px] text-gray-400'>
                    <div className='truncate' title={trade.securityName}>
                      {trade.securityName}
                    </div>
                  </TableCell>
                  <TableCell className='w-[80px] px-3 py-2 text-right text-[11px] font-medium'>
                    {formatNumber(Math.abs(trade.securitiesTransacted))}
                  </TableCell>
                  <TableCell className='w-[80px] px-3 py-2 text-right text-[11px]'>
                    {formatPrice(trade.price)}
                  </TableCell>
                  <TableCell className='w-[90px] px-3 py-2 text-right text-[11px] font-medium text-[#45CA89]'>
                    {formatValue(trade.securitiesTransacted, trade.price)}
                  </TableCell>
                  <TableCell className='w-[100px] whitespace-nowrap px-3 py-2 text-[11px] text-gray-300'>
                    {moment(trade.transactionDate).format('MMM D, YYYY')}
                  </TableCell>
                  <TableCell className='w-[100px] whitespace-nowrap px-3 py-2 text-[11px] text-gray-400'>
                    {moment(trade.filingDate).format('MMM D, YYYY')}
                  </TableCell>
                </TableRow>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default TickerInsiderTrades
