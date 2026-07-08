import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  unifiedCompanyProfileService,
  CompanyProfile,
} from '@/api/unified-company-profile.service'
import { useEffect, useCallback, useState } from 'react'

/**
 * INSTANT LOADING HOOK: Company profile with multi-layer caching
 */
export const useInstantCompanyProfile = (ticker: string) => {
  const queryClient = useQueryClient()

  const { data, isLoading, error } = useQuery<CompanyProfile | null>({
    queryKey: ['instant-company-profile', ticker],
    queryFn: () => unifiedCompanyProfileService.getCompanyProfile(ticker),
    enabled: !!ticker,
    staleTime: Infinity, // Never stale - we handle freshness internally
    gcTime: 1000 * 60 * 60 * 24 * 30, // Keep for 30 days
    retry: false, // No retries - we handle fallbacks internally
  })

  // Preload related tickers for instant access
  const preloadRelated = useCallback((relatedTickers: string[]) => {
    unifiedCompanyProfileService.preloadProfiles(relatedTickers)
  }, [])

  // Force refresh (bypasses all caches)
  const forceRefresh = useCallback(async () => {
    if (!ticker) return null

    // Clear all caches for this ticker
    queryClient.removeQueries({ queryKey: ['instant-company-profile', ticker] })

    // Fetch fresh data
    const freshData =
      await unifiedCompanyProfileService.getCompanyProfile(ticker)

    // Update query cache
    queryClient.setQueryData(['instant-company-profile', ticker], freshData)

    return freshData
  }, [ticker, queryClient])

  return {
    profile: data,
    isLoading,
    error,
    preloadRelated,
    forceRefresh,
    isInstant: !isLoading && !!data, // True if data is available instantly
  }
}

/**
 * BULK PRELOADING HOOK: Preload multiple profiles for instant access
 */
export const useBulkPreload = () => {
  const preloadProfiles = useCallback(async (tickers: string[]) => {
    await unifiedCompanyProfileService.preloadProfiles(tickers)
  }, [])

  const preloadCommonTickers = useCallback(async () => {
    const commonTickers = [
      'AAPL',
      'MSFT',
      'GOOGL',
      'AMZN',
      'TSLA',
      'META',
      'NVDA',
      'NFLX',
      'AMD',
      'INTC',
      'CRM',
      'ADBE',
      'PYPL',
      'UBER',
      'LYFT',
      'SQ',
      'SPY',
      'QQQ',
      'IWM',
      'VTI',
      'VOO',
      'ARKK',
      'TQQQ',
      'SQQQ',
      'JPM',
      'BAC',
      'WMT',
      'PG',
      'KO',
      'PFE',
      'ABBV',
      'MRK',
      'JNJ',
      'UNH',
      'HD',
      'MA',
      'V',
      'DIS',
      'NKE',
      'CMCSA',
    ]
    await unifiedCompanyProfileService.preloadProfiles(commonTickers)
  }, [])

  const preloadSectorTickers = useCallback(async (sector: string) => {
    const sectorTickers: { [key: string]: string[] } = {
      technology: [
        'AAPL',
        'MSFT',
        'GOOGL',
        'AMZN',
        'META',
        'NVDA',
        'AMD',
        'INTC',
        'CRM',
        'ADBE',
      ],
      finance: [
        'JPM',
        'BAC',
        'WFC',
        'GS',
        'MS',
        'C',
        'AXP',
        'BLK',
        'SPGI',
        'MCO',
      ],
      healthcare: [
        'JNJ',
        'PFE',
        'ABBV',
        'MRK',
        'UNH',
        'CVS',
        'ABT',
        'TMO',
        'DHR',
        'BMY',
      ],
      consumer: [
        'WMT',
        'PG',
        'KO',
        'PEP',
        'NKE',
        'MCD',
        'SBUX',
        'TGT',
        'HD',
        'LOW',
      ],
      energy: [
        'XOM',
        'CVX',
        'COP',
        'EOG',
        'SLB',
        'KMI',
        'PSX',
        'VLO',
        'MPC',
        'WMB',
      ],
      etfs: [
        'SPY',
        'QQQ',
        'IWM',
        'VTI',
        'VOO',
        'ARKK',
        'TQQQ',
        'SQQQ',
        'XLF',
        'XLK',
      ],
    }

    const tickers = sectorTickers[sector.toLowerCase()] || []
    if (tickers.length > 0) {
      await unifiedCompanyProfileService.preloadProfiles(tickers)
    }
  }, [])

  return {
    preloadProfiles,
    preloadCommonTickers,
    preloadSectorTickers,
  }
}

/**
 * CACHE MANAGEMENT HOOK: Monitor and manage cache
 */
export const useCompanyProfileCache = () => {
  const [stats, setStats] = useState(
    unifiedCompanyProfileService.getCacheStats()
  )

  const refreshStats = useCallback(() => {
    setStats(unifiedCompanyProfileService.getCacheStats())
  }, [])

  const clearCache = useCallback(() => {
    unifiedCompanyProfileService.clearCache()
    refreshStats()
  }, [refreshStats])

  useEffect(() => {
    const interval = setInterval(refreshStats, 5000)
    return () => clearInterval(interval)
  }, [refreshStats])

  return {
    stats,
    refreshStats,
    clearCache,
  }
}

/**
 * AUTO-PRELOAD HOOK: Automatically preload profiles based on user behavior
 */
export const useAutoPreload = () => {
  const { preloadProfiles } = useBulkPreload()

  // Preload profiles when user searches
  const onSearch = useCallback(
    (searchTerm: string) => {
      if (searchTerm.length >= 2) {
        // This would integrate with your search service
        // For now, we'll preload common matches
        const commonMatches = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA']
        preloadProfiles(commonMatches)
      }
    },
    [preloadProfiles]
  )

  // Preload profiles when user views a ticker
  const onTickerView = useCallback(() => {
    // Preload related tickers (same sector, similar market cap, etc.)
    // This would integrate with your analytics/recommendation service
    const relatedTickers = ['AAPL', 'MSFT', 'GOOGL'] // Placeholder
    preloadProfiles(relatedTickers)
  }, [preloadProfiles])

  return {
    onSearch,
    onTickerView,
  }
}
