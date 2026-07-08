import { RecentMacroResponse } from '@/models/market-data-analysis.model'
import { apiService } from '.'
import { MarketDataQueryParams } from '@/hooks/use-market-data-dark-pool'
import { IDashboardPayload } from '@/models/market-dashboard.model'

class MarketDashboardService {
  private api: typeof apiService
  controller: string = 'dashboard'

  constructor() {
    this.api = apiService
  }

  async getMarketDashboard(payload: IDashboardPayload[]) {
    return this.api.post<any>(`${this.controller}`, payload)
  }

  async getTopMarketGainers() {
    return this.api.get<any>(`${this.controller}/market/gainers`)
  }

  async getTopMarketLosers() {
    return this.api.get<any>(`${this.controller}/market/losers`)
  }

  async getTopActiveTickers() {
    return this.api.get<any>(`${this.controller}/market/active`)
  }
}

export const marketDashboardService = new MarketDashboardService()

class MarketDataAnalysisService {
  private api: typeof apiService

  constructor() {
    this.api = apiService
  }

  async getRecentMacros(days: number) {
    return this.api.get<RecentMacroResponse>(
      `macroLiquiditySnapshot/get-macro-liquidity-snapshot-by-day/${days}`
    )
  }

  async getMarketDataDarkPool(body: MarketDataQueryParams) {
    return this.api.post<any>(`darkLitReports/market-data/dark-pool`, body)
  }

  async getDarkPoolTopActivity(data?: any) {
    return this.api.post<any>(`darkLitReports/dark-pool/top-activity`, data)
  }

  async getTopSweeps(body?: any) {
    return this.api.post<any>(`optionTrade/top/orders/sweeps`, body)
  }

  async getTopDarkPoolBlocks(body?: any) {
    return this.api.post<any>(`stockTrade/top/dark-pool/blocks`, body)
  }

  async getContractsTopActivity(data?: any) {
    return this.api.post<any>(`ChainSentiment/contracts/top-activity`, data)
  }

  async getLitPoolTopActivity(data?: any) {
    return this.api.post<any>(`darkLitReports/lit-pool/top-activity`, data)
  }

  async getLiquiditySignalsTopActivity(data?: any) {
    return this.api.post<any>(
      `liquiditySignal/liquidity-signals/top-activity`,
      data
    )
  }

  async getTopDarkPoolDates(data?: any) {
    return this.api.post<any>(`stockTrade/top/dark-pool/date`, data)
  }

  async getTopBigFlowDates(data?: any) {
    return this.api.post<any>(`option_contract/top/big-flow/date`, data)
  }

  async getTopContractDate(data?: any) {
    return this.api.post<any>(`option_contract/top/contract/date`, data)
  }

  async getTopOpeningDate(data?: any) {
    return this.api.post<any>(`option_contract/top/opening/date`, data)
  }

  async getSymbolLevels(data?: any) {
    return this.api.post<any>(`ticker/chart/symbol/levels`, data)
  }

  async getTopDarkPoolOrders(data?: any) {
    return this.api.post<any>(
      `stockTrade/chart/symbol/dark-pool/top-orders`,
      data
    )
  }

  async getOptionLiquiditySnapshot(data?: any) {
    return this.api.post<any>(
      'optionLiquiditySnapshot/chart/symbol/option/liquidity-snapshot',
      data
    )
  }

  async getFlowData(data?: any) {
    const { page = 1, pageSize = 100, date, ...filters } = data || {}

    // Use our new backend endpoint for ClickHouse data
    const params = new URLSearchParams({
      page: page.toString(),
      pageSize: pageSize.toString(),
    })

    // Add date parameter if provided
    if (date) {
      params.append('date', date)
    }

    // Add filter parameters
    Object.keys(filters).forEach((key) => {
      const value = filters[key]
      if (value !== null && value !== undefined && value !== '') {
        if (Array.isArray(value) && value.length > 0) {
          params.append(key, value.join(','))
        } else if (!Array.isArray(value)) {
          params.append(key, value.toString())
        }
      }
    })

    // Sending filters to backend
    // Request URL constructed

    const response = await fetch(`http://localhost:3001/api/flow?${params}`)

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const result = await response.json()

    if (result.success) {
      return {
        data: result.data,
      }
    } else {
      throw new Error(result.error || 'Failed to fetch flow data')
    }
  }

  async getLatestFlowData(afterTimestamp: number, pageSize: number = 50) {
    const params = new URLSearchParams({
      afterTimestamp: afterTimestamp.toString(),
      pageSize: pageSize.toString(),
    })

    const response = await fetch(
      `http://localhost:3001/api/flow/latest?${params}`
    )

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const result = await response.json()

    if (result.success) {
      return {
        data: result.data,
      }
    } else {
      throw new Error(result.error || 'Failed to fetch latest flow data')
    }
  }

  // Prefetching removed for simplification

  async getDarkPoolData(data?: any) {
    return this.api.post<any>(`stockTrade/dark-pool-list`, data)
  }

  async getDarkPoolScannerData() {
    return this.api.get<any>('dark-pool/scanner')
  }
}

export const marketDataAnalysisService = new MarketDataAnalysisService()
