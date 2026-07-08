import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useSymbolLevels = (payload: any) => {
  return useQuery({
    queryKey: ['chart', 'symbolLevels'],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getSymbolLevels(payload)
      return data
    },
  })
}
