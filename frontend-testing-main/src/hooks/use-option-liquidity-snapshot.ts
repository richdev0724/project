import { marketDataAnalysisService } from '@/api'
import { useQuery } from '@tanstack/react-query'

interface OptionLiquiditySnapshot {
  [key: string]: any
}

export const useOptionLiquiditySnapshot = (
  payload: OptionLiquiditySnapshot
) => {
  return useQuery({
    queryKey: ['chart', 'optionLiquiditySnapshots'],
    queryFn: async () => {
      const { data } =
        await marketDataAnalysisService.getOptionLiquiditySnapshot(payload)
      return data
    },
  })
}
