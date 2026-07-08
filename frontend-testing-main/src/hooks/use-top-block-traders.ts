import { hotContractService } from '@/api/hot-contracts.service'
import { TopBlock } from '@/app/pages/options/data/schema'
import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'

export type TopBlockTrades = {
  sip_date_friendly: string
  price: string
  symbol: string
  net_premium: string
  size: string
  trf_timestamp: string | null
  trf_date_friendly: string | null
  trf_timestamp_friendly: string | null
}

const formatNumber = (num: number): string => {
  if (num >= 1_000_000) {
    return `${(num / 1_000_000).toFixed(1)}M`
  }

  if (num >= 1_000) {
    return `${(num / 1_000).toFixed(1)}k`
  }

  return num.toString()
}

const transformBlockData = (items: TopBlock[] = []): TopBlockTrades[] =>
  items.map((item) => ({
    symbol: item.symbol,
    price: item.price,
    size: formatNumber(item.size),
    net_premium: formatNumber(parseFloat(item.net_premium)),
    sip_date_friendly: formatSipDate(item.sip_date_friendly),
    trf_timestamp: item.trf_timestamp,
    trf_date_friendly: item.trf_date_friendly,
    trf_timestamp_friendly: item.trf_timestamp_friendly,
  }))

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

export const useTopBlockTrades = (symbol: string) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'topBlockTrades'],
    queryFn: async () => {
      return await hotContractService.getTopBlockTrades(symbol)
    },
    enabled: !!symbol,
  })

  const topBlockData = useMemo(
    () => ({
      trades: transformBlockData(data?.data),
    }),
    [data]
  )
  return {
    isLoading,
    isError,
    topBlockData,
  }
}
