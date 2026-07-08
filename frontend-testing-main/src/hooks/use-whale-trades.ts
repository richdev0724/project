import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useCallback, useEffect, useRef } from 'react'

export interface WhaleTrade {
  id: string
  symbol: string
  price: string
  size: number
  total_premium: string
  market_session: string
  trade_date: string
  timestamp: string
  exchange_id?: string
}

export interface WhaleTradesResponse {
  success: boolean
  data: WhaleTrade[]
  pagination: {
    page: number
    pageSize: number
    totalCount: number
    totalPages: number
    hasNext: boolean
    hasPrev: boolean
  }
  filters: {
    minPremium: number
    session?: string
    ticker?: string
  }
}

export interface WhaleTradesParams {
  page?: number
  pageSize?: number
  minPremium?: number
  maxPremium?: number | null
  minSize?: number | null
  maxSize?: number | null
  minPrice?: number | null
  maxPrice?: number | null
  session?: string
  ticker?: string
  sortBy?: string
}

const fetchWhaleTrades = async (
  params: WhaleTradesParams = {}
): Promise<WhaleTradesResponse> => {
  const searchParams = new URLSearchParams()

  if (params.page) searchParams.append('page', params.page.toString())
  if (params.pageSize)
    searchParams.append('pageSize', params.pageSize.toString())
  if (params.minPremium)
    searchParams.append('minPremium', params.minPremium.toString())
  if (params.maxPremium)
    searchParams.append('maxPremium', params.maxPremium.toString())
  if (params.minSize) searchParams.append('minSize', params.minSize.toString())
  if (params.maxSize) searchParams.append('maxSize', params.maxSize.toString())
  if (params.minPrice)
    searchParams.append('minPrice', params.minPrice.toString())
  if (params.maxPrice)
    searchParams.append('maxPrice', params.maxPrice.toString())
  if (params.session) searchParams.append('session', params.session)
  if (params.ticker) searchParams.append('ticker', params.ticker)
  if (params.sortBy) searchParams.append('sortBy', params.sortBy)

  const response = await fetch(
    `http://localhost:3001/api/whale-trades?${searchParams.toString()}`
  )

  if (!response.ok) {
    throw new Error(`Failed to fetch whale trades: ${response.statusText}`)
  }

  return response.json()
}

export const useWhaleTrades = (params: WhaleTradesParams = {}) => {
  const queryClient = useQueryClient()
  const [isStreaming, setIsStreaming] = useState(false)
  const [streamingData, setStreamingData] = useState<WhaleTrade[]>([])
  const [connectionStatus, setConnectionStatus] = useState<
    'disconnected' | 'connecting' | 'connected'
  >('disconnected')
  const eventSourceRef = useRef<EventSource | null>(null)

  const {
    page = 1,
    pageSize = 50,
    minPremium = 10000,
    maxPremium,
    minSize,
    maxSize,
    minPrice,
    maxPrice,
    session,
    ticker,
    sortBy,
  } = params

  const queryKey = [
    'whaleTrades',
    page,
    pageSize,
    minPremium,
    maxPremium,
    minSize,
    maxSize,
    minPrice,
    maxPrice,
    session,
    ticker,
    sortBy,
  ]

  const query = useQuery<WhaleTradesResponse>({
    queryKey,
    queryFn: () => fetchWhaleTrades(params),
    refetchOnWindowFocus: true,
    staleTime: 2000, // Consider data stale after 2 seconds
  })

  // Start real-time streaming
  const startStreaming = useCallback(() => {
    if (isStreaming || eventSourceRef.current) {
      return
    }

    setIsStreaming(true)
    setConnectionStatus('connecting')

    const eventSource = new EventSource(
      'http://localhost:3001/api/whale-trades/stream'
    )
    eventSourceRef.current = eventSource

    eventSource.onopen = () => {
      setConnectionStatus('connected')
    }

    eventSource.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)

        switch (message.type) {
          case 'connected':
            break

          case 'whaleTrade':
            setStreamingData((prevData) => {
              // Check if trade already exists to prevent duplicates
              const existingIndex = prevData.findIndex(
                (trade) => trade.id === message.data.id
              )
              if (existingIndex >= 0) {
                return prevData // Skip duplicate
              }

              // Add new trade to the beginning for individual table update
              const newData = [message.data, ...prevData]
              return newData.slice(0, pageSize)
            })

            // Throttle query invalidation to avoid performance issues
            setTimeout(() => {
              queryClient.invalidateQueries({ queryKey: ['whaleTrades'] })
            }, 2000)
            break

          case 'heartbeat':
            // Keep connection alive
            break

          case 'error':
            // Error message received from stream
            break

          case 'streamEnded':
            stopStreaming()
            break
        }
      } catch (_error) {
        // Error parsing stream message
      }
    }

    eventSource.onerror = () => {
      setConnectionStatus('disconnected')
      stopStreaming()
    }
  }, [isStreaming, pageSize, queryClient, queryKey])

  // Stop streaming
  const stopStreaming = useCallback(() => {
    if (!isStreaming && !eventSourceRef.current) {
      return
    }

    setIsStreaming(false)
    setConnectionStatus('disconnected')

    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }

    // Clear streaming data
    setStreamingData([])
  }, [isStreaming])

  // Clean up on unmount
  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
    }
  }, [])

  const refetch = useCallback(() => {
    queryClient.invalidateQueries({ queryKey })
    return query.refetch()
  }, [queryClient, queryKey, query])

  // Combine streaming data with initial query data
  const combinedData =
    isStreaming && streamingData.length > 0
      ? streamingData
      : query.data?.data || []

  return {
    data: combinedData,
    pagination: query.data?.pagination,
    filters: query.data?.filters,
    isLoading: query.isLoading,
    error: query.error,
    isStreaming,
    connectionStatus,
    startStreaming,
    stopStreaming,
    refetch,
    totalCount: query.data?.pagination?.totalCount || 0,
  }
}

// Hook for whale trades statistics
export const useWhaleTradesStats = () => {
  return useQuery({
    queryKey: ['whaleTradesStats'],
    queryFn: async () => {
      const response = await fetch(
        'http://localhost:3001/api/whale-trades/stats'
      )
      if (!response.ok) {
        throw new Error('Failed to fetch whale trades stats')
      }
      return response.json()
    },
    refetchInterval: 30000, // Refetch every 30 seconds
    staleTime: 15000, // Consider stale after 15 seconds
  })
}

// Hook for top whale trades
export const useTopWhaleTrades = (limit: number = 10) => {
  return useQuery({
    queryKey: ['topWhaleTrades', limit],
    queryFn: async () => {
      const response = await fetch(
        `http://localhost:3001/api/whale-trades/top?limit=${limit}`
      )
      if (!response.ok) {
        throw new Error('Failed to fetch top whale trades')
      }
      return response.json()
    },
    refetchInterval: 10000, // Refetch every 10 seconds
    staleTime: 5000, // Consider stale after 5 seconds
  })
}
