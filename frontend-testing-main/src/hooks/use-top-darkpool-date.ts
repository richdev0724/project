import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useTopDarkPoolDate = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'topDarkpoolDate'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getTopDarkPoolDates(payload)
      return data
    },
  })
}
