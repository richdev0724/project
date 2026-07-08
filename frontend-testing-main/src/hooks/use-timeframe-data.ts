import { useState, useEffect, useCallback } from 'react'
import { getTimeframeData, TimeframeData } from '@/api/timeframe-data.service'

interface UseTimeframeDataOptions {
  ticker: string
  timeframe: string
  limit?: number
  enabled?: boolean
}

interface UseTimeframeDataReturn {
  data: TimeframeData[]
  isLoading: boolean
  error: string | null
  refetch: () => void
}

/**
 * Custom hook for fetching timeframe data
 */
export const useTimeframeData = ({
  ticker,
  timeframe,
  limit = 1000,
  enabled = true,
}: UseTimeframeDataOptions): UseTimeframeDataReturn => {
  const [data, setData] = useState<TimeframeData[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    if (!enabled || !ticker || !timeframe) {
      return
    }

    try {
      setIsLoading(true)
      setError(null)

      const result = await getTimeframeData(ticker, timeframe, limit)
      setData(result)
    } catch (err: any) {
      setError(err.message || 'Failed to fetch timeframe data')
    } finally {
      setIsLoading(false)
    }
  }, [ticker, timeframe, limit, enabled])

  useEffect(() => {
    fetchData()
  }, [fetchData])

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

        const result = await getTimeframeData(ticker, timeframe, limit)
        setData((prev) => ({ ...prev, [timeframe]: result }))
      } catch (err: any) {
        setErrors((prev) => ({
          ...prev,
          [timeframe]: err.message || 'Failed to fetch data',
        }))
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
