import { apiService } from '.'

interface GammaExposureParams {
  symbol: string
  expiration?: string
}

interface ContractData {
  details: {
    expiration_date: string
    strike_price: number
    contract_type: 'call' | 'put'
  }
  open_interest: number
  day: {
    volume: number
    close: number
  }
}

interface ChainResponse {
  data: ContractData[]
}

interface LastPriceResponse {
  data: {
    p: number // Last price
  }
}

class ContractService {
  private api: typeof apiService
  controller: string = 'contract'

  constructor() {
    this.api = apiService
  }

  async getGammaExposure({ symbol, expiration }: GammaExposureParams) {
    return this.api.get<any>(`/gamma-exposure/${symbol}`, {
      params: { expiration },
    })
  }

  async getExpirationDates(symbol: string) {
    return this.api.get<any>(`/gamma-exposure/${symbol}/expirations`)
  }

  async getFullChain(symbol: string): Promise<ContractData[]> {
    const response = await this.api.get<ChainResponse>(
      `${this.controller}/${symbol}/chain`
    )
    return response.data
  }

  async getLastPrice(symbol: string): Promise<number> {
    const response = await this.api.get<LastPriceResponse>(
      `${this.controller}/${symbol}/last-price`
    )
    return response.data.p
  }
}

export const contractService = new ContractService()
