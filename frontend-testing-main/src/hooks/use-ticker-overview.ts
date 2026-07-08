import { IDashboardPayload } from '@/models/market-dashboard.model'
import { useQuery } from '@tanstack/react-query'

export const useTickerOverview = (
  _ticker: string,
  _payload: IDashboardPayload[],
  queryKeys: string[]
) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: queryKeys,
    queryFn: async () => {
      // Temporarily disabled - endpoint doesn't exist
      // const { data } = await tickerOverviewService.getTickerOverview(
      //   payload,
      //   ticker
      // )
      // return data
      return null
    },
    retry: false,
    enabled: false, // Disable the query
  })

  return {
    data,
    isLoading,
    isError,
  }
}
