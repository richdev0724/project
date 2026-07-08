import { useQuery } from '@tanstack/react-query'
import { insiderTradesService } from '@/api/insider-trades.service'

export const useInsiderTrades = (ticker: string, limit: number = 50) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['insider-trades', ticker, limit],
    queryFn: () => insiderTradesService.getInsiderTrades(ticker, limit),
    staleTime: 1000 * 60 * 5, // 5 minutes - data is fresh for 5 minutes
    refetchInterval: 1000 * 60 * 5, // Refetch every 5 minutes
    refetchIntervalInBackground: false,
    enabled: !!ticker, // Only run query if ticker is provided
  })

  return {
    trades: data,
    isLoading,
    error,
  }
}
