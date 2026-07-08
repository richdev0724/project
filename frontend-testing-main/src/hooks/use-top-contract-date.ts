import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useTopContractDate = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'topContractDate'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getTopContractDate(payload)
      return data
    },
  })
}
