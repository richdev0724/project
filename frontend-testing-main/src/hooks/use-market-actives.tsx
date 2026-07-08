import { marketDashboardService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useMarketActives = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'marketActives'],
    queryFn: async () => {
      const { data } = await marketDashboardService.getTopActiveTickers()
      return data
    },
  })
  return {
    marketActives: data,
    isLoading,
    isError,
  }
}
