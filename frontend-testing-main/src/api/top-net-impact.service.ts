import { apiService } from './api.service'

export interface TopNetImpactItem {
  ticker: string
  net_premium: number
}

export interface TopNetImpactResponse {
  success: boolean
  data: {
    bullish: TopNetImpactItem[]
    bearish: TopNetImpactItem[]
  }
  timestamp: string
}

export const topNetImpactService = {
  /**
   * Get top bullish and bearish net impact tickers
   * @returns Promise with bullish and bearish tickers
   */
  async getTopNetImpact(): Promise<TopNetImpactResponse> {
    const response =
      await apiService.get<TopNetImpactResponse>('/top-net-impact')
    return response
  },
}
