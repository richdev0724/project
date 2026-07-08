import { marketDataAnalysisService } from '@/api'
import { OptionTrade } from '@/app/pages/flow/data/schema'
import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useState } from 'react'

interface PollingOptions {
  pageSize: number
  pageIndex: number
  orderBy: string
  intervalMs?: number // Default 5 seconds
  enabled?: boolean
}

/**
 * Hook to fetch flow data with automatic polling for real-time updates
 *
 * This hook polls the API at regular intervals to get new trades and updates
 * the flow data automatically with a configurable delay to avoid overloading.
 */
export const useFlowPolling = ({
  pageSize,
  pageIndex,
  orderBy,
  intervalMs = 5000, // 5 seconds default
  enabled = false,
}: PollingOptions) => {
  const [flowData, setFlowData] = useState<OptionTrade[]>([])
  const [totalCount, setTotalCount] = useState<number>(0)
  const [lastUpdateTime, setLastUpdateTime] = useState<string>('')

  // Get the timestamp of the most recent trade to fetch only newer trades
  const getLatestTimestamp = useCallback(() => {
    if (flowData.length > 0) {
      // Get the most recent trade timestamp
      const latestTrade = flowData[0] // Assuming data is sorted by timestamp desc
      return latestTrade._source.sip_timestamp
    }
    return null
  }, [flowData])

  // Query for initial data load
  const { data: initialData, isLoading: isInitialLoading } = useQuery({
    queryKey: ['flowData', 'initial', pageSize, pageIndex, orderBy],
    queryFn: async () => {
      const { data } = await marketDataAnalysisService.getFlowData({
        page: pageIndex,
        pageSize,
        orderBy,
      })
      return data
    },
    enabled: true,
  })

  // Query for polling new data
  const { data: pollingData } = useQuery({
    queryKey: ['flowData', 'polling', lastUpdateTime],
    queryFn: async () => {
      const latestTimestamp = getLatestTimestamp()

      if (!latestTimestamp) return null

      // Fetch only trades newer than our latest trade
      const { data } = await marketDataAnalysisService.getLatestFlowData(
        latestTimestamp,
        50 // Get up to 50 new trades
      )

      return data
    },
    enabled: enabled && flowData.length > 0,
    refetchInterval: intervalMs,
    refetchIntervalInBackground: true,
  })

  // Set initial data
  useEffect(() => {
    if (initialData?.results && initialData.results.length > 0) {
      setFlowData(initialData.results)
      setTotalCount(initialData.totalCount)
    }
  }, [initialData])

  // Handle polling data updates
  useEffect(() => {
    if (pollingData?.results && pollingData.results.length > 0) {
      setFlowData((prevData) => {
        // Get existing trade IDs to avoid duplicates
        const existingIds = new Set(prevData.map((trade) => trade._source.id))

        // Filter out trades we already have
        const newTrades = pollingData.results.filter(
          (trade: OptionTrade) => !existingIds.has(trade._source.id)
        )

        if (newTrades.length > 0) {
          // Add new trades to the beginning and maintain page size
          const updatedData = [...newTrades, ...prevData].slice(0, pageSize)
          return updatedData
        }

        return prevData
      })

      setLastUpdateTime(new Date().toISOString())
    }
  }, [pollingData, pageSize])

  const startPolling = useCallback(() => {
    setLastUpdateTime(new Date().toISOString())
  }, [])

  const stopPolling = useCallback(() => {}, [])

  return {
    flowData,
    totalCount,
    isLoading: isInitialLoading,
    isPollingActive: enabled,
    startPolling,
    stopPolling,
    lastUpdateTime,
  }
}
