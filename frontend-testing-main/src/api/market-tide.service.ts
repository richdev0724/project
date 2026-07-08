import { apiService } from './api.service'

export interface MarketTideDataPoint {
  timestamp: number
  date: string
  call_premium: number
  put_premium: number
  net_volume: number
  spy_close: number | null
  original_timestamp: string
}

export interface MarketTideResponse {
  success: boolean
  data: MarketTideDataPoint[]
  timestamp: string
}

export const marketTideService = {
  /**
   * Get market tide data (net call premium, net put premium, net volume)
   * @param date - Optional date in YYYY-MM-DD format
   * @param endDate - Optional end date in YYYY-MM-DD format
   * @returns Promise with time series data
   */
  async getMarketTide(
    date?: string,
    endDate?: string
  ): Promise<MarketTideResponse> {
    const params: Record<string, string> = {}
    if (date) params.date = date
    if (endDate) params.endDate = endDate

    const response = await apiService.get<MarketTideResponse>('/market-tide', {
      params,
    })
    return response
  },
}
