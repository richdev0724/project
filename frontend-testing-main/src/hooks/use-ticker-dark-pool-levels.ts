import { useQuery } from '@tanstack/react-query'

interface DarkPoolLevel {
  ticker: string
  price: number
  size: number
  total_premium: number
  trades: number
  market_session: string
  trade_date: string
}

interface DarkPoolLevelsResponse {
  ticker: string
  levels: DarkPoolLevel[]
  count: number
}

export const useTickerDarkPoolLevels = (ticker: string, limit: number = 10) => {
  return useQuery<DarkPoolLevelsResponse>({
    queryKey: ['darkPoolLevels', ticker, limit],
    queryFn: async () => {
      const res = await fetch(`/api/dark-pool/levels/${ticker}?limit=${limit}`)
      if (!res.ok) {
        throw new Error(`Failed to fetch dark pool levels: ${res.statusText}`)
      }
      return res.json()
    },
    enabled: !!ticker,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  })
}
