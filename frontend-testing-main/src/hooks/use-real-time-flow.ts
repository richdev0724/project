import { useEffect, useState, useCallback } from 'react'
import { flushSync } from 'react-dom'
import { io, Socket } from 'socket.io-client'
import { OptionTrade } from '@/app/pages/flow/data/schema'
import { marketDataAnalysisService } from '@/api'

interface UseRealTimeFlowOptions {
  pageSize: number
  pageIndex: number
  orderBy: string
  enabled?: boolean
}

interface UseRealTimeFlowReturn {
  flowData: OptionTrade[]
  totalCount: number
  isLoading: boolean
  isConnected: boolean
  startRealTime: () => void
  stopRealTime: () => void
  connectionStatus: 'disconnected' | 'connecting' | 'connected' | 'error'
}

/**
 * Hook for real-time flow data using Socket.IO
 * Provides instant updates as trades are inserted into the database
 */
export const useRealTimeFlow = ({
  pageSize,
  pageIndex,
  orderBy,
  enabled = false,
}: UseRealTimeFlowOptions): UseRealTimeFlowReturn => {
  const [socket, setSocket] = useState<Socket | null>(null)
  const [flowData, setFlowData] = useState<OptionTrade[]>([])
  const [totalCount, setTotalCount] = useState<number>(0)
  const [isLoading, setIsLoading] = useState<boolean>(true)
  const [isConnected, setIsConnected] = useState<boolean>(false)
  const [connectionStatus, setConnectionStatus] = useState<
    'disconnected' | 'connecting' | 'connected' | 'error'
  >('disconnected')

  // Initialize socket connection
  useEffect(() => {
    const newSocket = io('http://localhost:3001', {
      autoConnect: false,
      transports: ['websocket', 'polling'],
    })

    newSocket.on('connect', () => {
      setIsConnected(true)
      setConnectionStatus('connected')
    })

    newSocket.on('disconnect', () => {
      setIsConnected(false)
      setConnectionStatus('disconnected')
    })

    newSocket.on('connect_error', () => {
      // Socket connection error occurred
      setConnectionStatus('error')
    })

    newSocket.on('newTrade', handleNewTrade)

    setSocket(newSocket)

    return () => {
      if (newSocket) {
        newSocket.disconnect()
      }
    }
  }, [])

  // Handle incoming new trades
  const handleNewTrade = useCallback(
    (newTrade: OptionTrade) => {
      // Use flushSync to force immediate UI update and prevent batching
      flushSync(() => {
        setFlowData((prevData) => {
          // Check if we already have this trade
          const existingIds = new Set(prevData.map((trade) => trade._source.id))

          if (!existingIds.has(newTrade._source.id)) {
            // Add new trade to the beginning and maintain page size
            const updatedData = [newTrade, ...prevData].slice(0, pageSize)
            return updatedData
          }

          return prevData
        })
      })

      // Also update total count
      setTotalCount((prev) => prev + 1)
    },
    [pageSize]
  )

  // Load initial data
  const loadInitialData = useCallback(async () => {
    try {
      setIsLoading(true)
      const { data } = await marketDataAnalysisService.getFlowData({
        page: pageIndex + 1,
        pageSize,
        orderBy,
      })

      if (data?.results) {
        setFlowData(data.results)
        setTotalCount(data.totalCount || 0)
      }
    } catch (_error) {
      // Error loading initial flow data
    } finally {
      setIsLoading(false)
    }
  }, [pageIndex, pageSize, orderBy])

  // Load initial data when component mounts or params change
  useEffect(() => {
    loadInitialData()
  }, [loadInitialData])

  // Start real-time monitoring
  const startRealTime = useCallback(() => {
    if (socket && !isConnected) {
      setConnectionStatus('connecting')
      socket.connect()
    }

    if (socket && isConnected) {
      socket.emit('startFlowMonitoring')
    }
  }, [socket, isConnected])

  // Stop real-time monitoring
  const stopRealTime = useCallback(() => {
    if (socket && isConnected) {
      socket.emit('stopFlowMonitoring')
    }

    if (socket) {
      socket.disconnect()
    }
  }, [socket, isConnected])

  // Auto-connect when enabled
  useEffect(() => {
    if (enabled && socket && !isConnected) {
      startRealTime()
    } else if (!enabled && socket && isConnected) {
      stopRealTime()
    }
  }, [enabled, socket, isConnected, startRealTime, stopRealTime])

  return {
    flowData,
    totalCount,
    isLoading,
    isConnected,
    startRealTime,
    stopRealTime,
    connectionStatus,
  }
}
