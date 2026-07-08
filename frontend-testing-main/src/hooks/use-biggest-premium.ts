import { hotContractService } from '@/api/hot-contracts.service'
import { useQuery } from '@tanstack/react-query'

export const useBiggestPremium = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'biggestPremium'],
    queryFn: async () => {
      const { data } = await hotContractService.getBiggestPremium(payload)
      return data
    },
  })
}
