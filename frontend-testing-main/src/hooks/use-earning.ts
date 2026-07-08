import { earningsService } from '@/api/earnings.service'
import { useQuery } from '@tanstack/react-query'

export const useEarning = (symbol: string) => {
  return useQuery({
    queryKey: ['earnings', 'ticker', symbol],
    queryFn: async () => {
      const response = await earningsService.getTickerEarnings(symbol)
      return response.data
    },
    enabled: !!symbol,
  })
}
