import { useQuery } from '@tanstack/react-query'

interface NetPremiumsData {
  data: any
}

export const useNetPremiums = (ticker: string, days: number = 5) => {
  return useQuery<NetPremiumsData | null>({
    queryKey: ['net-premiums', ticker, days],
    queryFn: () => {
      // Temporarily disabled - endpoint doesn't exist
      // return netPremiumsService.getNetPremiums(ticker, days)
      return null
    },
    enabled: false, // Disable the query
    staleTime: 5 * 60 * 1000, // 5 minutes
    refetchInterval: 5 * 60 * 1000, // Refetch every 5 minutes
  })
}
