import axios from 'axios'
import type { PoliticianTrade } from '../app/pages/politicians/components/PoliticianTradesTable'
import type { PortfolioData } from '../data/mockHoldings'

// Use empty string to use Vite proxy in development, or env variable for production
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export const politicianTradesService = {
  /**
   * Get all politician trades
   */
  async getAllTrades(): Promise<PoliticianTrade[]> {
    const response = await axios.get(`${API_BASE_URL}/api/politician-trades`)
    return response.data.data
  },

  /**
   * Get paginated politician trades
   */
  async getPaginatedTrades(
    limit: number,
    offset: number
  ): Promise<{
    data: PoliticianTrade[]
    count: number
    total: number
    hasMore: boolean
    offset: number
  }> {
    const response = await axios.get(
      `${API_BASE_URL}/api/politician-trades?limit=${limit}&offset=${offset}`
    )
    return {
      ...response.data,
      offset: offset,
    }
  },

  /**
   * Get trades for a specific politician
   */
  async getPoliticianTrades(
    politicianName: string
  ): Promise<PoliticianTrade[]> {
    const response = await axios.get(
      `${API_BASE_URL}/api/politician-trades/${encodeURIComponent(politicianName)}`
    )
    return response.data.data
  },

  /**
   * Get portfolio data for a specific politician
   */
  async getPoliticianPortfolio(politicianName: string): Promise<PortfolioData> {
    const response = await axios.get(
      `${API_BASE_URL}/api/politician-trades/${encodeURIComponent(politicianName)}/portfolio`
    )
    return response.data.data
  },

  /**
   * Clear the trades cache
   */
  async clearCache(): Promise<void> {
    await axios.post(`${API_BASE_URL}/api/politician-trades/clear-cache`)
  },
}
