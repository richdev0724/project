import { apiService } from '.'
import { IDashboardPayload } from '@/models/market-dashboard.model'
import axios from 'axios'

interface TickerDetails {
  ticker: string
  name: string
  description: string
  logo_url: string | null
  icon_url: string | null
  total_employees: number
  ceo: string | null
  address: string | null
  market_cap: number
  is_etf: boolean
}

class TickerOverviewService {
  private api: typeof apiService
  controller: string = 'ticker/overview/'

  constructor() {
    this.api = apiService
  }

  async getTickerDetails(ticker: string): Promise<TickerDetails> {
    const response = await axios.get<TickerDetails>(
      `/api/polygon/ticker-details/${ticker}`
    )
    return response.data
  }

  async getTickerOverview(payload: IDashboardPayload[], ticker: string) {
    return this.api.post<any>(`${this.controller}${ticker}`, payload)
  }

  async getStrikeMetrics<T extends Record<string, unknown>>(
    ticker: string,
    payload: T,
    type: string
  ) {
    return this.api.post<any>(`/ticker/${type}/${ticker}`, payload)
  }

  async getMonoMatrix(ticker: string, range: string) {
    return this.api.get<any>(`ticker/mono-matrix/${ticker}?range=${range}`)
  }

  async getDailyChart(ticker: string, timeFrame: string) {
    return this.api.get<any>(
      `ticker/daily-chart/${ticker}?timeframe=${timeFrame}`
    )
  }
}

export const tickerOverviewService = new TickerOverviewService()
