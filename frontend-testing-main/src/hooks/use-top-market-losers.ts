import { marketDashboardService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useMarketLosers = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'marketLosers'],
    queryFn: async () => {
      const { data } = await marketDashboardService.getTopMarketLosers()
      return data
    },
  })
  return {
    marketLosers: data,
    isLoading,
    isError,
  }
}
