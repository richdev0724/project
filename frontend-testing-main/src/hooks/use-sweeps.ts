import { hotContractService } from '@/api/hot-contracts.service'
import { useQuery } from '@tanstack/react-query'

export const useSweeps = (payload: any) => {
  return useQuery({
    queryKey: ['marketDataAnalysis', 'sweeps'],
    queryFn: async () => {
      const { data } = await hotContractService.getSweeps(payload)
      return data
    },
  })
}
