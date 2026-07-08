import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useRecentMacro = (days: number) => {
  return useQuery({
    queryKey: ['misc', 'recent-macro'],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getRecentMacros(days)
      return data
    },
  })
}
