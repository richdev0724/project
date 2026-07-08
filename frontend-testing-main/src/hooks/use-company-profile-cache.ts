import { useState, useEffect } from 'react'
import { companyProfileService } from '@/api/company-profile.service'

interface CacheStats {
  totalCached: number
  lastUpdated: string
  cacheSize: number
}

export const useCompanyProfileCache = () => {
  const [cacheStats, setCacheStats] = useState<CacheStats>({
    totalCached: 0,
    lastUpdated: 'Never',
    cacheSize: 0,
  })

  const [isClearingCache, setIsClearingCache] = useState(false)

  // Update cache stats
  const updateCacheStats = () => {
    const stats = companyProfileService.getCacheStats()
    setCacheStats(stats)
  }

  // Clear expired cache entries
  const clearExpiredCache = async () => {
    setIsClearingCache(true)
    try {
      const clearedCount = companyProfileService.clearExpiredCache()
      updateCacheStats()
      return clearedCount
    } finally {
      setIsClearingCache(false)
    }
  }

  // Clear all cache
  const clearAllCache = () => {
    companyProfileService.clearAllCache()
    updateCacheStats()
  }

  // Preload profiles for common tickers
  const preloadCommonProfiles = async () => {
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
    ]

    await companyProfileService.preloadProfiles(commonTickers)
    updateCacheStats()
  }

  // Format cache size for display
  const formatCacheSize = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  // Update stats on mount
  useEffect(() => {
    updateCacheStats()
  }, [])

  return {
    cacheStats,
    updateCacheStats,
    clearExpiredCache,
    clearAllCache,
    preloadCommonProfiles,
    formatCacheSize,
    isClearingCache,
  }
}
