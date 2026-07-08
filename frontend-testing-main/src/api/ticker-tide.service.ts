import { apiService } from './api.service'

export interface TickerTideDataPoint {
  timestamp: number
  date: string
  call_premium: number
  put_premium: number
  net_volume: number
  ticker_close: number | null
  original_timestamp: string
  net_delta?: string | number
  call_volume?: number
  put_volume?: number
}

export interface TickerTideResponse {
  success: boolean
  data: TickerTideDataPoint[]
  timestamp: string
}

export const tickerTideService = {
  /**
   * Get ticker-specific tide data (net call premium, net put premium, net volume)
   * @param ticker - Stock ticker symbol
   * @param date - Optional date in YYYY-MM-DD format
   * @param endDate - Optional end date in YYYY-MM-DD format
   * @returns Promise with time series data
   */
  async getTickerTide(
    ticker: string,
    date?: string,
    endDate?: string
  ): Promise<TickerTideResponse> {
    const params: Record<string, string> = {}
    if (date) params.date = date
    if (endDate) params.endDate = endDate

    const response = await apiService.get<TickerTideResponse>(
      `/ticker-tide/${ticker}`,
      {
        params,
      }
    )
    return response
  },
}
