import { apiService } from './api.service'

interface LogoCache {
  [ticker: string]: {
    url: string
    timestamp: number
  }
}

interface LogoResponse {
  logoUrl?: string
}

class LogoService {
  private cache: LogoCache = {}
  private readonly CACHE_DURATION = 24 * 60 * 60 * 1000 // 24 hours

  async getLogo(ticker: string): Promise<string | null> {
    if (!ticker) return null

    // Check in-memory cache first
    const cached = this.cache[ticker]
    if (cached && Date.now() - cached.timestamp < this.CACHE_DURATION) {
      return cached.url
    }

    try {
      // Try backend first
      const response = await apiService.get<LogoResponse>(`/logo/${ticker}`)
      if (response && response.logoUrl) {
        // Cache the result
        this.cache[ticker] = {
          url: response.logoUrl,
          timestamp: Date.now(),
        }
        return response.logoUrl
      }
    } catch (error) {
      // Backend logo service failed, will try fallback
    }

    // Fallback to direct FMP URL
    try {
      const fmpUrl = `https://images.financialmodelingprep.com/symbol/${ticker.toUpperCase()}.png`

      // Test if the URL works by making a HEAD request
      const testResponse = await fetch(fmpUrl, { method: 'HEAD' })
      if (testResponse.ok) {
        // Cache the result
        this.cache[ticker] = {
          url: fmpUrl,
          timestamp: Date.now(),
        }
        return fmpUrl
      }
    } catch (error) {
      // FMP fallback failed
    }

    return null
  }

  async preloadLogos(tickers: string[]): Promise<void> {
    const promises = tickers.map((ticker) => this.getLogo(ticker))
    await Promise.allSettled(promises)
  }

  clearCache(): void {
    this.cache = {}
  }

  clearSpecificLogo(ticker: string): void {
    delete this.cache[ticker]
  }

  getCacheStats(): { cachedLogos: number; cacheSize: number } {
    return {
      cachedLogos: Object.keys(this.cache).length,
      cacheSize: JSON.stringify(this.cache).length,
    }
  }
}

export const logoService = new LogoService()
