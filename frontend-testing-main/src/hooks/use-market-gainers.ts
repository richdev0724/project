import { marketDashboardService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useMarketGainers = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'marketGainers'],
    queryFn: async () => {
      const { data } = await marketDashboardService.getTopMarketGainers()
      return data
    },
  })
  return {
    marketGainers: data,
    isLoading,
    isError,
  }
}
