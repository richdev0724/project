import { apiService } from './api.service'

export interface EarningsItem {
  symbol: string
  full_name: string
  marketcap: string
  time?: string
}

export interface EarningsByDateResponse {
  date: string
  premarket: EarningsItem[]
  afterhours: EarningsItem[]
  success: boolean
}

export interface TickerEarningsData {
  actual_eps: string
  ending_fiscal_quarter: string
  expected_move: string
  expected_move_perc: string
  long_straddle_1d: string
  long_straddle_1w: string
  post_earnings_move_1d: string
  post_earnings_move_1w: string
  post_earnings_move_2w: string
  post_earnings_move_3d: string
  pre_earnings_move_1d: string
  pre_earnings_move_1w: string
  pre_earnings_move_2w: string
  pre_earnings_move_3d: string
  report_date: string
  report_time: string
  short_straddle_1d: string
  short_straddle_1w: string
  source: string
  street_mean_est: string
}

export interface TickerEarningsResponse {
  data: TickerEarningsData[]
}

export const earningsService = {
  async getEarningsByDate(
    date: string
  ): Promise<{ premarket: EarningsItem[]; afterhours: EarningsItem[] }> {
    return apiService.get(`/earnings/${date}`)
  },

  async getTickerEarnings(ticker: string): Promise<TickerEarningsResponse> {
    return apiService.get(`/earnings/ticker/${ticker}`)
  },
}
