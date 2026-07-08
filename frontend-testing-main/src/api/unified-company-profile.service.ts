import { apiService } from './api.service'

export interface CompanyProfile {
  ticker: string
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

interface CachedProfile {
  data: CompanyProfile
  timestamp: number
  version: string
}

class UnifiedCompanyProfileService {
  private memoryCache = new Map<string, CachedProfile>()
  private readonly CACHE_VERSION = '2.0.0'
  private readonly CACHE_EXPIRY = 30 * 24 * 60 * 60 * 1000 // 30 days
  private preloadQueue = new Set<string>()
  private isPreloading = false

  constructor() {
    this.loadFromLocalStorage()
    this.startPreloadWorker()
  }

  /**
   * INSTANT LOADING: Get company profile with multi-layer caching
   */
  async getCompanyProfile(ticker: string): Promise<CompanyProfile | null> {
    if (!ticker) return null
    const upperTicker = ticker.toUpperCase()

    // 1. MEMORY CACHE (instant)
    const memoryCached = this.memoryCache.get(upperTicker)
    if (memoryCached && this.isValid(memoryCached)) {
      return memoryCached.data
    }

    // 2. LOCALSTORAGE CACHE (near-instant)
    const localCached = this.getFromLocalStorage(upperTicker)
    if (localCached && this.isValid(localCached)) {
      this.memoryCache.set(upperTicker, localCached)
      return localCached.data
    }

    // 3. CLICKHOUSE DATABASE (fast)
    try {
      const dbProfile = await this.getFromClickHouse(upperTicker)
      if (dbProfile) {
        const cached = this.createCachedProfile(upperTicker, dbProfile)
        this.memoryCache.set(upperTicker, cached)
        this.saveToLocalStorage(upperTicker, cached)
        return dbProfile
      }
    } catch (error) {
      // Silently fail and try API
    }

    // 4. API FALLBACK (slow)
    try {
      const apiProfile = await this.getFromAPI(upperTicker)
      if (apiProfile) {
        const cached = this.createCachedProfile(upperTicker, apiProfile)
        this.memoryCache.set(upperTicker, cached)
        this.saveToLocalStorage(upperTicker, cached)
        // Store in ClickHouse for future instant access
        this.storeInClickHouse(apiProfile).catch(() => {
          // Silently fail
        })
        return apiProfile
      }
    } catch (error) {
      // Silently fail
    }

    return null
  }

  /**
   * INTELLIGENT PRELOADING: Preload profiles for instant access
   */
  async preloadProfiles(tickers: string[]): Promise<void> {
    const validTickers = tickers.filter(
      (t) => t && !this.memoryCache.has(t.toUpperCase())
    )

    if (validTickers.length === 0) return

    // Add to preload queue
    validTickers.forEach((ticker) =>
      this.preloadQueue.add(ticker.toUpperCase())
    )

    // Start preloading if not already running
    if (!this.isPreloading) {
      this.processPreloadQueue()
    }
  }

  /**
   * BACKGROUND PRELOADING WORKER
   */
  private async processPreloadQueue(): Promise<void> {
    if (this.isPreloading || this.preloadQueue.size === 0) return

    this.isPreloading = true

    while (this.preloadQueue.size > 0) {
      const ticker = this.preloadQueue.values().next().value
      if (!ticker) break
      this.preloadQueue.delete(ticker)

      try {
        await this.getCompanyProfile(ticker)
        // Small delay to prevent overwhelming the system
        await new Promise((resolve) => setTimeout(resolve, 100))
      } catch (error) {
        // Silently fail preload operations
      }
    }

    this.isPreloading = false
  }

  /**
   * CLICKHOUSE INTEGRATION
   */
  private async getFromClickHouse(
    ticker: string
  ): Promise<CompanyProfile | null> {
    try {
      const response = await apiService.get<CompanyProfile>(
        `/company-profiles/storage/${ticker}`
      )
      return response
    } catch (error) {
      return null
    }
  }

  private async storeInClickHouse(profile: CompanyProfile): Promise<void> {
    try {
      await apiService.post(
        '/company-profiles/storage',
        profile as unknown as Record<string, unknown>
      )
    } catch (error) {
      // Silently fail storage operations
    }
  }

  /**
   * API INTEGRATION
   */
  private async getFromAPI(ticker: string): Promise<CompanyProfile | null> {
    try {
      const response = await apiService.get<CompanyProfile>(
        `/news/company-profile/${ticker}`
      )
      return response ? { ...response, ticker } : null
    } catch (error) {
      return null
    }
  }

  /**
   * CACHE MANAGEMENT
   */
  private isValid(cached: CachedProfile): boolean {
    return (
      Date.now() - cached.timestamp < this.CACHE_EXPIRY &&
      cached.version === this.CACHE_VERSION
    )
  }

  private createCachedProfile(
    ticker: string,
    data: CompanyProfile
  ): CachedProfile {
    return {
      data: { ...data, ticker },
      timestamp: Date.now(),
      version: this.CACHE_VERSION,
    }
  }

  private loadFromLocalStorage(): void {
    try {
      const stored = localStorage.getItem('unified_company_profiles')
      if (stored) {
        const cache = JSON.parse(stored)
        if (cache.version === this.CACHE_VERSION) {
          Object.entries(cache.data).forEach(([ticker, profile]) => {
            this.memoryCache.set(ticker, profile as CachedProfile)
          })
        }
      }
    } catch (error) {
      // Silently fail cache loading
    }
  }

  private saveToLocalStorage(ticker: string, cached: CachedProfile): void {
    try {
      const existing = localStorage.getItem('unified_company_profiles')
      const cache = existing
        ? JSON.parse(existing)
        : { version: this.CACHE_VERSION, data: {} }
      cache.data[ticker] = cached
      localStorage.setItem('unified_company_profiles', JSON.stringify(cache))
    } catch (error) {
      // Silently fail cache saving
    }
  }

  private getFromLocalStorage(ticker: string): CachedProfile | null {
    try {
      const stored = localStorage.getItem('unified_company_profiles')
      if (stored) {
        const cache = JSON.parse(stored)
        return cache.data[ticker] || null
      }
    } catch (error) {
      // Silently fail cache retrieval
    }
    return null
  }

  /**
   * PRELOAD WORKER
   */
  private startPreloadWorker(): void {
    // Preload common tickers on startup
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

    setTimeout(() => {
      this.preloadProfiles(commonTickers)
    }, 1000)
  }

  /**
   * STATISTICS
   */
  getCacheStats() {
    return {
      memoryCache: this.memoryCache.size,
      preloadQueue: this.preloadQueue.size,
      isPreloading: this.isPreloading,
    }
  }

  clearCache(): void {
    this.memoryCache.clear()
    this.preloadQueue.clear()
    localStorage.removeItem('unified_company_profiles')
  }
}

export const unifiedCompanyProfileService = new UnifiedCompanyProfileService()
