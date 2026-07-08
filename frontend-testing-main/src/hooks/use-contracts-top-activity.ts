import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useContractsTopActivity = (payload: any) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'contractsTopActivity'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getContractsTopActivity(payload)
      return data
    },
  })
  return {
    contractsTopActivity: data,
    isLoading,
    isError,
  }
}
