import { apiService } from './api.service'

export interface KeyMetrics {
  peRatio: number
  priceToBookRatio: number
  priceToSalesRatio: number
  enterpriseValue: number
  enterpriseValueOverEBITDA: number
  returnOnEquity: number
  returnOnAssets: number
  debtToEquity: number
  currentRatio: number
  dividendYield: number
  payoutRatio: number
  revenuePerShare: number
  bookValuePerShare: number
  operatingCashFlowPerShare: number
  freeCashFlowPerShare: number
  grossProfitMargin: number
  operatingProfitMargin: number
  netProfitMargin: number
  ebitda: number
  ebitdaMargin: number
  date: string
}

class KeyMetricsService {
  async getKeyMetrics(ticker: string): Promise<KeyMetrics | null> {
    const response = await apiService.get<KeyMetrics>(
      `/news/key-metrics/${ticker}`
    )
    return response
  }
}

export const keyMetricsService = new KeyMetricsService()
