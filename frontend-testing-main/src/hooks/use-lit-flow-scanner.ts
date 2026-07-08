import { useQuery, useQueryClient } from '@tanstack/react-query'
import { socket } from '@/utilities/socket'
import { useEffect } from 'react'

interface LitFlowScannerResult {
  status: string
  data?: any[]
  lastScanTime?: string
}

const fetchLitFlowScanner = async (): Promise<LitFlowScannerResult> => {
  // Get today's date in local timezone
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    .toISOString()
    .split('T')[0]

  try {
    const res = await fetch(`/api/lit-flow/scanner?date=${today}`)
    if (!res.ok) {
      throw new Error(`Failed to fetch lit flow data: ${res.statusText}`)
    }
    return res.json()
  } catch (error) {
    return { status: 'error', data: [] }
  }
}

export const useLitFlowScanner = () => {
  const queryClient = useQueryClient()
  // Get today's date in local timezone
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    .toISOString()
    .split('T')[0]

  // Handle WebSocket updates
  useEffect(() => {
    const handleLitFlowData = (data: LitFlowScannerResult) => {
      if (!data) return
      // Only invalidate if we have valid data
      if (data.data && Array.isArray(data.data) && data.data.length > 0) {
        queryClient.setQueryData(
          ['marketDataAnalysis', 'litFlowScanner', today],
          data
        )
      }
    }

    socket.on('litFlowData', handleLitFlowData)
    return () => {
      socket.off('litFlowData', handleLitFlowData)
    }
  }, [queryClient, today])

  return useQuery<LitFlowScannerResult>({
    queryKey: ['marketDataAnalysis', 'litFlowScanner', today],
    queryFn: fetchLitFlowScanner,
    refetchInterval: (query) => {
      const data = query.state.data
      // If status is running, poll every 3s
      if (data?.status === 'running') return 3000
      // If we have data but it's older than 10 minutes, refresh
      if (data?.data && Array.isArray(data.data) && data.data.length > 0)
        return 30000 // Poll every 30s to keep data fresh
      return false
    },
    refetchOnWindowFocus: true,
    staleTime: 5 * 60 * 1000, // Consider data fresh for 5 minutes
    gcTime: 30 * 60 * 1000, // Keep in cache for 30 minutes
    retry: (failureCount) => {
      // Only retry up to 3 times
      return failureCount < 3
    },
  })
}
