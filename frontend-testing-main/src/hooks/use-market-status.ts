import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

interface MarketStatus {
  exchanges?: {
    nasdaq?: string
    nyse?: string
  }
  market?: string
  serverTime?: string
}

const fetchMarketStatus = async (): Promise<MarketStatus> => {
  const { data } = await axios.get('/api/polygon/market-status')
  return data
}

export const useMarketStatus = () => {
  return useQuery<MarketStatus>({
    queryKey: ['market-status'],
    queryFn: fetchMarketStatus,
    refetchInterval: 5 * 60 * 1000, // Refetch every 5 minutes
    staleTime: 2 * 60 * 1000, // Consider data fresh for 2 minutes
    retry: 2,
  })
}

export const isMarketOpen = (
  marketStatus: MarketStatus | undefined
): boolean => {
  if (!marketStatus?.exchanges) return false

  // Check if either NASDAQ or NYSE is open
  return (
    marketStatus.exchanges.nasdaq === 'open' ||
    marketStatus.exchanges.nyse === 'open'
  )
}

export const isMarketClosed = (
  marketStatus: MarketStatus | undefined
): boolean => {
  if (!marketStatus?.exchanges) return true

  // Market is closed if both exchanges are closed
  return (
    marketStatus.exchanges.nasdaq === 'closed' &&
    marketStatus.exchanges.nyse === 'closed'
  )
}
