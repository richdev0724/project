import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useTopOpeningDate = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'topOpeningDate'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getTopOpeningDate(payload)
      return data
    },
  })
}
