import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const useLiquiditySignalsTopActivity = () => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['marketDataAnalysis', 'liquiditySignalsTopActivity'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getLiquiditySignalsTopActivity()
      return data
    },
  })
  return {
    liquiditySignalsTopActivity: data,
    isLoading,
    isError,
  }
}
