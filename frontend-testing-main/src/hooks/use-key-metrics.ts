import { useQuery } from '@tanstack/react-query'
import { keyMetricsService } from '@/api/key-metrics.service'

export const useKeyMetrics = (ticker: string) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['key-metrics', ticker],
    queryFn: () => keyMetricsService.getKeyMetrics(ticker),
    enabled: !!ticker,
    staleTime: 0,
    gcTime: 0,
  })

  return {
    metrics: data,
    isLoading,
    error,
  }
}
