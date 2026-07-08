import { marketDashboardService } from '@/api'
import { IDashboardPayload } from '@/models/market-dashboard.model'
import { useQuery } from '@tanstack/react-query'

export const useDashboard = (
  payload: IDashboardPayload[],
  queryKeys: string[]
) => {
  const { data, isLoading, isError } = useQuery({
    queryKey: queryKeys,
    queryFn: async () => {
      const { data } = await marketDashboardService.getMarketDashboard(payload)
      return data
    },
  })

  return {
    data,
    isLoading,
    isError,
  }
}
