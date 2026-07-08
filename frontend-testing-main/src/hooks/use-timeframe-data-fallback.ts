import { useState, useEffect, useCallback, useRef } from 'react'
import { apiService } from '@/api/api.service'

export interface TimeframeData {
  ticker: string
  timeframe: string
  timestamp: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  vwap: number
}

interface UseTimeframeDataOptions {
  ticker: string
  timeframe: string
  enabled?: boolean
}

interface UseTimeframeDataReturn {
  data: TimeframeData[]
  isLoading: boolean
  error: string | null
  refetch: () => void
}

/**
 * Temporary fallback hook that uses the existing market prices API
 * until the new timeframe system is fully deployed
 */
export const useTimeframeData = ({
  ticker,
  timeframe,
  enabled = true,
}: UseTimeframeDataOptions): UseTimeframeDataReturn => {
  const [data, setData] = useState<TimeframeData[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isMountedRef = useRef(true)
  const lastFetchRef = useRef<string>('')

  const fetchData = useCallback(async () => {
    if (!enabled || !ticker || !timeframe) {
      return
    }

    // Prevent duplicate requests for the same ticker/timeframe combination
    const requestKey = `${ticker}-${timeframe}`
    if (lastFetchRef.current === requestKey && isLoading) {
      return
    }
    lastFetchRef.current = requestKey

    try {
      if (!isMountedRef.current) return

      setIsLoading(true)
      setError(null)

      // Fetching timeframe data using fallback API

      // Use the existing market prices API as fallback
      const response = await apiService.get('/polygon/market-prices', {
        params: {
          tickers: ticker,
          timeframe: timeframe,
          // For longer timeframes, don't specify date to get historical data
          // Remove the hardcoded date to let backend handle it properly
        },
      })

      // API Response received

      if (!isMountedRef.current) return

      if (response && response[ticker] && response[ticker].results) {
        const rawData = response[ticker].results
        // Raw data length processed

        // Transform to TimeframeData format
        const transformedData: TimeframeData[] = rawData.map((item: any) => ({
          ticker: ticker.toUpperCase(),
          timeframe,
          timestamp: new Date(item.t).toISOString(),
          open: item.o,
          high: item.h,
          low: item.l,
          close: item.c,
          volume: item.v,
          vwap: item.vw || item.c,
        }))

        if (isMountedRef.current) {
          setData(transformedData)
          // Successfully set data points
        }
      } else {
        // No data structure found
        if (isMountedRef.current) {
          setError('No data available')
        }
      }
    } catch (err: any) {
      // Error fetching timeframe data
      if (isMountedRef.current) {
        setError(err.message || 'Failed to fetch timeframe data')
      }
    } finally {
      if (isMountedRef.current) {
        setIsLoading(false)
      }
    }
  }, [ticker, timeframe, enabled]) // Removed 'limit' dependency as it's not used

  useEffect(() => {
    fetchData()
  }, [fetchData])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      isMountedRef.current = false
    }
  }, [])

  const refetch = useCallback(() => {
    fetchData()
  }, [fetchData])

  return {
    data,
    isLoading,
    error,
    refetch,
  }
}

/**
 * Hook for managing multiple timeframes
 */
export const useMultipleTimeframes = (
  ticker: string,
  timeframes: string[],
  limit: number = 1000
) => {
  const [data, setData] = useState<Record<string, TimeframeData[]>>({})
  const [isLoading, setIsLoading] = useState<Record<string, boolean>>({})
  const [errors, setErrors] = useState<Record<string, string | null>>({})

  const fetchTimeframe = useCallback(
    async (timeframe: string) => {
      if (!ticker || !timeframe) return

      try {
        setIsLoading((prev) => ({ ...prev, [timeframe]: true }))
        setErrors((prev) => ({ ...prev, [timeframe]: null }))

        const response = await apiService.get('/polygon/market-prices', {
          params: {
            tickers: ticker,
            timeframe: timeframe,
            ...(timeframe === '1D' && { date: '2025-10-14' }),
          },
        })

        if (response && response[ticker] && response[ticker].results) {
          const rawData = response[ticker].results

          const transformedData: TimeframeData[] = rawData.map((item: any) => ({
            ticker: ticker.toUpperCase(),
            timeframe,
            timestamp: new Date(item.t).toISOString(),
            open: item.o,
            high: item.h,
            low: item.l,
            close: item.c,
            volume: item.v,
            vwap: item.vw || item.c,
          }))

          setData((prev) => ({ ...prev, [timeframe]: transformedData }))
        }
      } catch (err: any) {
        setErrors((prev) => ({
          ...prev,
          [timeframe]: err.message || 'Failed to fetch data',
        }))
        // Error fetching timeframe data
      } finally {
        setIsLoading((prev) => ({ ...prev, [timeframe]: false }))
      }
    },
    [ticker, limit]
  )

  const fetchAllTimeframes = useCallback(async () => {
    const promises = timeframes.map((timeframe) => fetchTimeframe(timeframe))
    await Promise.all(promises)
  }, [timeframes, fetchTimeframe])

  useEffect(() => {
    if (ticker && timeframes.length > 0) {
      fetchAllTimeframes()
    }
  }, [ticker, timeframes, fetchAllTimeframes])

  const refetchTimeframe = useCallback(
    (timeframe: string) => {
      fetchTimeframe(timeframe)
    },
    [fetchTimeframe]
  )

  const refetchAll = useCallback(() => {
    fetchAllTimeframes()
  }, [fetchAllTimeframes])

  return {
    data,
    isLoading,
    errors,
    refetchTimeframe,
    refetchAll,
  }
}
