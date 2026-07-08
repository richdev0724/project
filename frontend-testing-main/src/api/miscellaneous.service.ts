import { GenericResponse } from '@/models/generic'
import { apiService } from '.'
import { TickerRoot } from '@/models/market-dashboard.model'

class MiscellaneousService {
  private api: typeof apiService
  controller: string = 'ticker'

  constructor() {
    this.api = apiService
  }

  async tickerSnapshot(symbol: string) {
    return this.api.get<GenericResponse<TickerRoot>>(
      `${this.controller}/snapshot/${symbol}`
    )
  }

  async getStockPrices(data: any) {
    return this.api.post<any>(`${this.controller}/stock/prices`, data)
  }

  async getEarnings(symbol: string) {
    return this.api.get<any>(`earning/symbol/${symbol}`)
  }

  async getNews(symbol: string, take: number) {
    return this.api.get<any>(`dashboard/ticker-news/${symbol}/${take}`)
  }

  async getPriceTargets(symbol: string) {
    return this.api.get<any>(`priceTarget/symbol/${symbol}`)
  }

  async postBookMarkOptionTrade(data: any) {
    return this.api.post<any>(`/optionsTradeBookmark`, data)
  }

  async getBookMarkOptionTrade() {
    return this.api.get<any>(`/optionsTradeBookmark/`)
  }

  async getTickerOptionExpirations(ticker: string) {
    return this.api.get<any>(`ticker/option-expiration-dates/?ticker=${ticker}`)
  }
}

export const miscellaneousService = new MiscellaneousService()
