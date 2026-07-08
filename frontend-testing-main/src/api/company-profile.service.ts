import { apiService } from './api.service'

export interface CompanyProfile {
  companyName: string
  description: string
  industry: string
  sector: string
  ceo: string
  website: string
  employees: number
  marketCap: number
  country: string
  city: string
  state: string
  address: string
  phone: string
  exchange: string
  currency: string
  isEtf: boolean
  isActivelyTrading: boolean
}

interface CachedCompanyProfile extends CompanyProfile {
  ticker: string
  cachedAt: string
  lastUpdated: string
  version: number
}

interface CacheStats {
  totalCached: number
  lastUpdated: string
  cacheSize: number
}

class CompanyProfileService {
  private memoryCache = new Map<string, CachedCompanyProfile>()
  private readonly CACHE_KEY_PREFIX = 'company-profile-cache-'
  private readonly CACHE_STATS_KEY = 'company-profile-cache-stats'
  private readonly CACHE_VERSION = 1
  private readonly CACHE_EXPIRY_DAYS = 30 // Cache for 30 days, but we'll update monthly

  constructor() {
    this.loadCacheStats()
  }

  /**
   * Get company profile with intelligent caching
   */
  async getCompanyProfile(ticker: string): Promise<CompanyProfile | null> {
    if (!ticker) return null

    const upperTicker = ticker.toUpperCase()

    // 1. Check memory cache first
    const memoryCached = this.memoryCache.get(upperTicker)
    if (memoryCached && this.isCacheValid(memoryCached)) {
      return this.formatProfile(memoryCached)
    }

    // 2. Check localStorage cache
    const localCached = this.getFromLocalStorage(upperTicker)
    if (localCached && this.isCacheValid(localCached)) {
      // Update memory cache
      this.memoryCache.set(upperTicker, localCached)
      return this.formatProfile(localCached)
    }

    // 3. Fetch from API
    try {
      const response = await apiService.get<CompanyProfile>(
        `/news/company-profile/${upperTicker}`
      )

      if (response) {
        const cachedProfile = this.createCachedProfile(upperTicker, response)

        // Store in both caches
        this.memoryCache.set(upperTicker, cachedProfile)
        this.saveToLocalStorage(upperTicker, cachedProfile)
        this.updateCacheStats()

        return response
      }
    } catch (error) {
      // Return stale cache if available
      if (localCached) {
        return this.formatProfile(localCached)
      }
    }

    return null
  }

  /**
   * Preload company profiles for multiple tickers
   */
  async preloadProfiles(tickers: string[]): Promise<void> {
    const promises = tickers.map(async (ticker) => {
      try {
        await this.getCompanyProfile(ticker)
      } catch (error) {
        // Silently fail for preload operations
      }
    })

    await Promise.allSettled(promises)
  }

  /**
   * Force refresh a company profile (bypasses cache)
   */
  async refreshProfile(ticker: string): Promise<CompanyProfile | null> {
    const upperTicker = ticker.toUpperCase()

    try {
      const response = await apiService.get<CompanyProfile>(
        `/news/company-profile/${upperTicker}`
      )

      if (response) {
        const cachedProfile = this.createCachedProfile(upperTicker, response)

        // Update both caches
        this.memoryCache.set(upperTicker, cachedProfile)
        this.saveToLocalStorage(upperTicker, cachedProfile)
        this.updateCacheStats()

        return response
      }
    } catch (error) {
      // Silently fail
    }

    return null
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): CacheStats {
    const stats = this.getCacheStatsFromStorage()
    return {
      totalCached: this.memoryCache.size,
      lastUpdated: stats.lastUpdated || 'Never',
      cacheSize: this.getCacheSize(),
    }
  }

  /**
   * Clear expired cache entries
   */
  clearExpiredCache(): number {
    let clearedCount = 0

    // Clear from memory cache
    for (const [ticker, profile] of this.memoryCache.entries()) {
      if (!this.isCacheValid(profile)) {
        this.memoryCache.delete(ticker)
        clearedCount++
      }
    }

    // Clear from localStorage
    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(this.CACHE_KEY_PREFIX)) {
        const ticker = key.replace(this.CACHE_KEY_PREFIX, '')
        const profile = this.getFromLocalStorage(ticker)
        if (profile && !this.isCacheValid(profile)) {
          keysToRemove.push(key)
          clearedCount++
        }
      }
    }

    keysToRemove.forEach((key) => localStorage.removeItem(key))

    if (clearedCount > 0) {
      this.updateCacheStats()
    }

    return clearedCount
  }

  /**
   * Clear all cache
   */
  clearAllCache(): void {
    this.memoryCache.clear()

    const keysToRemove: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(this.CACHE_KEY_PREFIX)) {
        keysToRemove.push(key)
      }
    }

    keysToRemove.forEach((key) => localStorage.removeItem(key))
    localStorage.removeItem(this.CACHE_STATS_KEY)
  }

  // Private helper methods

  private createCachedProfile(
    ticker: string,
    profile: CompanyProfile
  ): CachedCompanyProfile {
    const now = new Date().toISOString()
    return {
      ...profile,
      ticker,
      cachedAt: now,
      lastUpdated: now,
      version: this.CACHE_VERSION,
    }
  }

  private formatProfile(cachedProfile: CachedCompanyProfile): CompanyProfile {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { ticker, cachedAt, lastUpdated, version, ...profile } = cachedProfile
    return profile
  }

  private isCacheValid(profile: CachedCompanyProfile): boolean {
    const now = new Date()
    const cachedDate = new Date(profile.cachedAt)
    const daysDiff =
      (now.getTime() - cachedDate.getTime()) / (1000 * 60 * 60 * 24)

    return (
      daysDiff < this.CACHE_EXPIRY_DAYS &&
      profile.version === this.CACHE_VERSION
    )
  }

  private getFromLocalStorage(ticker: string): CachedCompanyProfile | null {
    try {
      const cached = localStorage.getItem(`${this.CACHE_KEY_PREFIX}${ticker}`)
      return cached ? JSON.parse(cached) : null
    } catch (error) {
      return null
    }
  }

  private saveToLocalStorage(
    ticker: string,
    profile: CachedCompanyProfile
  ): void {
    try {
      localStorage.setItem(
        `${this.CACHE_KEY_PREFIX}${ticker}`,
        JSON.stringify(profile)
      )
    } catch (error) {
      // Silently fail
    }
  }

  private loadCacheStats(): void {
    // Initialize cache stats if they don't exist
    if (!localStorage.getItem(this.CACHE_STATS_KEY)) {
      this.updateCacheStats()
    }
  }

  private updateCacheStats(): void {
    const stats = {
      lastUpdated: new Date().toISOString(),
      totalCached: this.memoryCache.size,
    }

    try {
      localStorage.setItem(this.CACHE_STATS_KEY, JSON.stringify(stats))
    } catch (error) {
      // Silently fail
    }
  }

  private getCacheStatsFromStorage(): { lastUpdated: string } {
    try {
      const stats = localStorage.getItem(this.CACHE_STATS_KEY)
      return stats ? JSON.parse(stats) : { lastUpdated: 'Never' }
    } catch (error) {
      return { lastUpdated: 'Never' }
    }
  }

  private getCacheSize(): number {
    let size = 0
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(this.CACHE_KEY_PREFIX)) {
        const value = localStorage.getItem(key)
        if (value) {
          size += value.length
        }
      }
    }
    return size
  }
}

export const companyProfileService = new CompanyProfileService()
