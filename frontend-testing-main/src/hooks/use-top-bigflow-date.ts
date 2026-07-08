import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useTopBigFlowDate = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'topBigFlowDate'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getTopBigFlowDates(payload)
      return data
    },
  })
}
