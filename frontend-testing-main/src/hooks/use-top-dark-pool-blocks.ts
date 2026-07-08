import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { marketDataAnalysisService } from '@/api'
import { Block } from '@/app/pages/dashboard/data/schema'

export type BlockDataItem = {
  symbol: string
  price: string
  volume: string
  premium: string
  date: string
}

type MarketDataQueryParams = {
  // Define the structure of your query parameters
  [key: string]: any
}

/**
 * Utility function to format large numbers
 * @param num - Number to format
 * @returns Formatted number string
 */
const formatNumber = (num: number): string => {
  if (num >= 1_000_000) {
    return `${(num / 1_000_000).toFixed(1)}M`
  }

  if (num >= 1_000) {
    return `${(num / 1_000).toFixed(1)}k`
  }

  return num.toString()
}

/**
 * Format SIP timestamp to a more readable date format
 * @param timestampFriendly - Timestamp string to format
 * @returns Formatted date string
 */
const formatSipDate = (timestampFriendly?: string): string => {
  if (!timestampFriendly) return 'N/A'

  const datePart = timestampFriendly.split(' ')[0]
  if (!datePart) return 'N/A'

  const [year, month, day] = datePart.split('-')

  // Safely parse month and day
  const monthNum = parseInt(month, 10)
  const dayNum = parseInt(day, 10)

  // Validate parsed values
  if (isNaN(monthNum) || isNaN(dayNum)) return 'N/A'

  return `${monthNum}/${dayNum}/${year.slice(-2)}`
}

/**
 * Transform market data items into block data items
 * @param items - Original market data items
 * @returns Transformed block data items
 */
const transformBlockData = (items: Block[] = []): BlockDataItem[] =>
  items.map((item) => ({
    symbol: item.symbol,
    price: item.price,
    volume: formatNumber(item.size),
    premium: formatNumber(parseFloat(item.net_premium)),
    date: formatSipDate(item.sip_timestamp_friendly),
  }))

/**
 * Custom hook to fetch and transform top dark pool blocks
 * @param data - Query parameters for fetching dark pool blocks
 * @returns Query result with transformed block data
 */
export const useTopDarkPoolBlocks = (payload: MarketDataQueryParams) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'topDarkPoolBlocks'],
    queryFn: async () => {
      return await marketDataAnalysisService.getTopDarkPoolBlocks(payload)
    },
    enabled: !!payload,
  })

  const blockData = useMemo(
    () => ({
      blocks: transformBlockData(data?.data),
    }),
    [data]
  )

  return {
    isLoading,
    isError,
    blockData,
  }
}
