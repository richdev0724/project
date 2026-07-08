import { apiService } from './api.service'

// PCR (Put/Call Ratio) is calculated using volume-based method (industry standard):
// PCR = Total Put Volume / Total Call Volume (previous trading day)
// Lower PCR (<0.6) = Bullish sentiment (greed)
// Higher PCR (>1.2) = Bearish sentiment (fear)

export interface PCRResponse {
  ticker: string
  pcr: number
  date: string
  cached: boolean
}

export interface PCRBulkResponse {
  results: Record<string, number>
  date: string
  count: number
}

export interface PCRCacheStats {
  size: number
  keys: string[]
}

class PCRService {
  async getPCR(ticker: string, date?: string): Promise<PCRResponse> {
    const params = date ? `?date=${date}` : ''
    const response = await apiService.get<PCRResponse>(
      `/pcr/${ticker}${params}`
    )
    return response
  }

  async calculatePCR(
    tickers: string[],
    date?: string
  ): Promise<PCRBulkResponse> {
    const response = await apiService.post<PCRBulkResponse>('/pcr/calculate', {
      tickers,
      date,
    })
    return response
  }

  async getCacheStats(): Promise<PCRCacheStats> {
    const response = await apiService.get<PCRCacheStats>('/pcr/cache/stats')
    return response
  }

  async clearCache(date?: string): Promise<{ message: string; date: string }> {
    const params = date ? `?date=${date}` : ''
    const response = await apiService.delete<{ message: string; date: string }>(
      `/pcr/cache${params}`
    )
    return response
  }
}

export const pcrService = new PCRService()
