import { tickerOverviewService } from '@/api/ticker-data.service'
import { useQuery } from '@tanstack/react-query'

export const useTickerMetrics = <T extends Record<string, unknown>>(
  ticker: string,
  type: string,
  payload: T,
  queryKeys: string[],
  options?: { enabled?: boolean }
) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: queryKeys,
    queryFn: async () => {
      const { data } = await tickerOverviewService.getStrikeMetrics(
        ticker,
        payload,
        type
      )
      return data
    },
    enabled: options?.enabled,
    retry: false,
  })

  return {
    data,
    isLoading,
    isError,
  }
}

export const useTickerMetricsV2 = <T extends string>(
  ticker: string,
  params: T
) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['tickerMetrics', ticker, params],
    queryFn: async () => {
      const { data } = await tickerOverviewService.getDailyChart(ticker, params)
      return data
    },
    retry: false,
  })

  return {
    data,
    isLoading,
    isError,
  }
}
