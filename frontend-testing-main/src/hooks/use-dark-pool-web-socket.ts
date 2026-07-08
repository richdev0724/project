// import { marketDataAnalysisService } from '@/api'
import { FlowSchema } from '@/app/pages/flow/data/schema'
import { isOptionsTradeLoadingAtom } from '@/store/store'
import { useAtom } from 'jotai'
import { useCallback, useEffect, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'

/**
 * Hook to fetch and manage dark pool data using websockets.
 */
export const useDarkPoolWithWebSocket = ({
  pageSize,
}: {
  pageSize: number
  pageIndex: number
}) => {
  const queryClient = useQueryClient()
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [_isOptionsTradeLoading, setIsOptionsTradeLoading] = useAtom(
    isOptionsTradeLoadingAtom
  )
  const [darkPoolData, setDarkPoolData] = useState<FlowSchema[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [ws, setWs] = useState<WebSocket | null>(null)

  // Function to connect to WebSocket
  const connectWebSocket = useCallback(() => {
    const wsUrl = 'ws://localhost:3001/api/whale-flow/stream'
    const newWs = new WebSocket(wsUrl)

    newWs.onopen = () => {
      setIsLoading(false)
      setError(null)
    }

    newWs.onclose = () => {
      setWs(null)
    }

    newWs.onerror = () => {
      // WebSocket error occurred
      setError('Failed to connect to WebSocket')
      setIsLoading(false)
    }

    newWs.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        if (message.type === 'whaleFlow') {
          setDarkPoolData((prevData) => {
            // Add new trade to the beginning of the array
            const newData = [message.data, ...prevData]
            // Keep only the latest pageSize items
            return newData.slice(0, pageSize)
          })

          // Invalidate relevant queries
          queryClient.invalidateQueries({
            queryKey: ['marketDataAnalysis', 'darkPoolScanner'],
          })
          queryClient.invalidateQueries({
            queryKey: ['marketDataAnalysis', 'darkPoolWithWebSocket'],
          })
          queryClient.invalidateQueries({
            queryKey: ['marketDataAnalysis', 'darkPoolTopActivity'],
          })
        }
      } catch (_error) {
        // Error processing WebSocket message
      }
    }

    setWs(newWs)
  }, [pageSize, queryClient])

  // Connect to WebSocket on mount
  useEffect(() => {
    setIsLoading(true)
    connectWebSocket()

    return () => {
      if (ws) {
        ws.close()
      }
    }
  }, [connectWebSocket])

  // Reconnect WebSocket if it closes
  useEffect(() => {
    if (!ws) {
      const reconnectTimeout = setTimeout(() => {
        connectWebSocket()
      }, 5000)

      return () => clearTimeout(reconnectTimeout)
    }
  }, [ws, connectWebSocket])

  const startListeningToOptionsTrade = useCallback(() => {
    setIsOptionsTradeLoading(true)
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ action: 'subscribe' }))
    }
  }, [ws, setIsOptionsTradeLoading])

  const stopListeningToOptionsTrade = useCallback(() => {
    setIsOptionsTradeLoading(false)
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ action: 'unsubscribe' }))
    }
  }, [ws, setIsOptionsTradeLoading])

  return {
    darkPoolData,
    totalCount: darkPoolData.length,
    isLoading,
    error,
    startListeningToOptionsTrade,
    stopListeningToOptionsTrade,
  }
}
