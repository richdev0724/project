import { contractService } from '@/api/contracts.service'
import { useQuery } from '@tanstack/react-query'

interface GammaExposure {
  ticker: string
  expiration?: string
}

interface GammaExposureResponse {
  data: Array<{
    strike_price: number
    call_gamma: number
    put_gamma: number
    net_gamma: number
    positive_gex: number
    negative_gex: number
    call_volume: number
    put_volume: number
    net_volume: number
    call_oi: number
    put_oi: number
    net_oi: number
    call_premium: number
    put_premium: number
    net_premium: number
  }>
  lastPrice: number | null
}

export const useGammaExposure = ({ ticker, expiration }: GammaExposure) => {
  return useQuery<GammaExposureResponse>({
    queryKey: ['gamma-exposure', ticker, expiration],
    queryFn: async () => {
      if (!expiration) {
        // Waiting for expiration date selection
        return { data: [], lastPrice: null }
      }
      // Fetching gamma exposure data
      const response = await contractService.getGammaExposure({
        symbol: ticker,
        expiration,
      })

      // Validate response data
      if (!response?.data?.data || !Array.isArray(response.data.data)) {
        // Invalid gamma exposure data received
        throw new Error('Invalid data format received from server')
      }

      if (response.data.data.length === 0) {
        // No gamma exposure data available
        return { data: [], lastPrice: null }
      }

      // Received gamma exposure data
      return response.data
    },
    enabled: !!ticker && !!expiration, // Only fetch when both ticker and expiration are available
    staleTime: 30 * 1000, // Consider data fresh for 30 seconds
    gcTime: 5 * 60 * 1000, // Keep in cache for 5 minutes before garbage collection
    retry: (failureCount, error) => {
      // Only retry network errors, not data validation errors
      if (error?.message?.includes('Invalid data format')) {
        return false
      }
      return failureCount < 2 // Maximum 2 retries
    },
    retryDelay: 1000, // Wait 1 second between retries
  })
}

export const useExpirationDates = (ticker: string) => {
  return useQuery({
    queryKey: ['gamma-exposure', 'expirations', ticker],
    queryFn: async () => {
      // Starting expiration dates fetch
      // Requesting data from backend

      const { data } = await contractService.getExpirationDates(ticker)

      // Received expiration dates
      // Dates received

      // Expiration dates analysis
      // Analysis details
      // Future dates count
      // Past dates count
      // Nearest expiration
      // Furthest expiration

      return data
    },
    enabled: !!ticker,
  })
}
