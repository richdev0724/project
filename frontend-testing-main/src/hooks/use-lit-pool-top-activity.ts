import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useLitPoolTopActivity = (payload?: any) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'litPoolTopActivity'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getLitPoolTopActivity(payload)
      return data
    },
  })
  return {
    litPoolTopActivity: data,
    isLoading,
    isError,
  }
}
