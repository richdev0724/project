import { useQuery } from '@tanstack/react-query'

interface LitPoolLevel {
  ticker: string
  price: number
  size: number
  total_premium: number
  trades: number
  market_session: string
  trade_date: string
}

interface LitPoolLevelsResponse {
  ticker: string
  levels: LitPoolLevel[]
  count: number
}

export const useTickerLitPoolLevels = (ticker: string, limit: number = 10) => {
  return useQuery<LitPoolLevelsResponse>({
    queryKey: ['litPoolLevels', ticker, limit],
    queryFn: async () => {
      const res = await fetch(`/api/lit-pool/levels/${ticker}?limit=${limit}`)
      if (!res.ok) {
        throw new Error(`Failed to fetch lit pool levels: ${res.statusText}`)
      }
      return res.json()
    },
    enabled: !!ticker,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  })
}
