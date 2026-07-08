import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useTopDarkPoolOrders = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'topDarkPoolOrders'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getTopDarkPoolOrders(payload)
      return data
    },
  })
}
