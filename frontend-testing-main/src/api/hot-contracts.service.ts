import { apiService } from '.'

class HotContractService {
  private api: typeof apiService
  controller: string = 'contract'

  constructor() {
    this.api = apiService
  }

  async getSweeps(body: any) {
    return this.api.post<any>(`optionTrade/hot-contracts/sweeps`, body)
  }

  async getBiggestPremium(body: any) {
    return this.api.post<any>(`optionTrade/hot-contracts/biggest-premium`, body)
  }

  async getRecentOpening(body: any) {
    return this.api.get<any>(`optionTrade/recent/opening/${body}`)
  }

  async getTopBlockTrades(symbol: string) {
    return this.api.get<any>(`stockTrade/block-trades/${symbol}`)
  }
}

export const hotContractService = new HotContractService()
