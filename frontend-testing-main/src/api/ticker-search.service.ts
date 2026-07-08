import { apiService } from '.'

export interface TickerSearchResult {
  symbol: string
  name: string
  exchange: string
  exchangeShortName: string
  type: string
  country: string
  currency: string
  isActivelyTrading: boolean
}

class TickerSearchService {
  async searchTickers(query: string): Promise<TickerSearchResult[]> {
    if (!query || query.length < 1) {
      return []
    }

    try {
      const response = await apiService.get<TickerSearchResult[]>(
        `/ticker-search?query=${encodeURIComponent(query)}`
      )
      return response
    } catch (error) {
      return []
    }
  }

  async searchTickersByCompany(query: string): Promise<TickerSearchResult[]> {
    if (!query || query.length < 1) {
      return []
    }

    try {
      const response = await apiService.get<TickerSearchResult[]>(
        `/ticker-search/company?query=${encodeURIComponent(query)}`
      )
      return response
    } catch (error) {
      return []
    }
  }
}

export const tickerSearchService = new TickerSearchService()
