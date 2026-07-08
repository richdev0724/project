import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useDarkPoolTopActivity = () => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'darkPoolTopActivity'],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getDarkPoolTopActivity()
      return data
    },
  })
}
