import { apiService } from './api.service'

export interface NetPremiumsDataPoint {
  name: string // Date formatted as "M/D"
  calls: number // Net Call Premium
  puts: number // Net Put Premium
  callVolume: number
  putVolume: number
  netPremium: number // calls - puts
}

export interface NetPremiumsResponse {
  success: boolean
  ticker: string
  timeframe: string
  data: NetPremiumsDataPoint[]
  totalCalls: number
  totalPuts: number
  netPremium: number
}

class NetPremiumsService {
  async getNetPremiums(
    ticker: string,
    days: number = 5
  ): Promise<NetPremiumsResponse> {
    const response = await apiService.get<NetPremiumsResponse>(
      `/daily-premiums/${ticker}/historical-polygon?days=${days}`
    )
    return response
  }
}

export const netPremiumsService = new NetPremiumsService()
