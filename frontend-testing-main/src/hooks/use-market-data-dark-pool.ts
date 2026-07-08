import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export type MarketDataQueryParams = {
  showLevels: boolean
  showBlockLevels: boolean
  ticker: string
  startDate: string
  endDate: string
  levelsAmount: number
  blockLevelsAmount: number
}

export const useMarketDataDarkPool = (payload: MarketDataQueryParams) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'marketDataDarkPool'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getMarketDataDarkPool(payload)
      return data
    },
  })
}
