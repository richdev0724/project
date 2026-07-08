import { useQuery, useQueryClient } from '@tanstack/react-query'
import { companyProfileService } from '@/api/company-profile.service'

export const useCompanyProfile = (ticker: string) => {
  const queryClient = useQueryClient()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['company-profile', ticker],
    queryFn: () => companyProfileService.getCompanyProfile(ticker),
    enabled: !!ticker,
    staleTime: 0,
    gcTime: 0,
    retry: 2,
    retryDelay: 1000,
  })

  // Function to force refresh the profile
  const refreshProfile = async () => {
    if (!ticker) return null

    try {
      const refreshedData = await companyProfileService.refreshProfile(ticker)
      if (refreshedData) {
        // Update the query cache with fresh data
        queryClient.setQueryData(['company-profile', ticker], refreshedData)
      }
      return refreshedData
    } catch (_error) {
      // Failed to refresh company profile
      return null
    }
  }

  // Function to get cache stats
  const getCacheStats = () => {
    return companyProfileService.getCacheStats()
  }

  // Function to clear expired cache
  const clearExpiredCache = () => {
    return companyProfileService.clearExpiredCache()
  }

  return {
    profile: data,
    isLoading,
    error,
    refetch,
    refreshProfile,
    getCacheStats,
    clearExpiredCache,
  }
}

// Hook for preloading multiple company profiles
export const usePreloadCompanyProfiles = () => {
  const preloadProfiles = async (tickers: string[]) => {
    await companyProfileService.preloadProfiles(tickers)
  }

  return { preloadProfiles }
}
