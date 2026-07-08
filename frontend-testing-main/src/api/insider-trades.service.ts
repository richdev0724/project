import { apiService } from './api.service'

export interface InsiderTrade {
  filingDate: string
  transactionDate: string
  reportingName: string
  typeOfOwner: string
  transactionType: string
  securitiesOwned: number
  securitiesTransacted: number
  price: number
  securityName: string
  link: string
}

class InsiderTradesService {
  async getInsiderTrades(
    ticker: string,
    limit: number = 50
  ): Promise<InsiderTrade[]> {
    const response = await apiService.get<InsiderTrade[]>(
      `/insider-trades/${ticker}?limit=${limit}`
    )
    return response
  }
}

export const insiderTradesService = new InsiderTradesService()
