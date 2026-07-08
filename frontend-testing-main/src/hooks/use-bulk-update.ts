import { useState, useEffect, useCallback } from 'react'
import { apiService } from '@/api/api.service'

interface BulkUpdateResponse {
  success: boolean
  error?: string
}

interface BulkUpdateStatus {
  isRunning: boolean
  totalTickers: number
  processedTickers: number
  successfulUpdates: number
  failedUpdates: number
  startTime: string | null
  endTime: string | null
  errors: Array<{
    ticker: string
    error: string
    timestamp: string
  }>
  currentBatch: number
  totalBatches: number
  duration: number
  progress: number
  estimatedTimeRemaining: number | null
}

interface BulkUpdateStats {
  totalUpdates: number
  successRate: number
  averageTimePerTicker: number
  lastUpdate: string | null
  errorCount: number
}

interface BulkUpdateConfig {
  batchSize: number
  delayBetweenBatches: number
  maxConcurrentRequests: number
  sampleSize: number | null
}

export const useBulkUpdate = () => {
  const [status, setStatus] = useState<BulkUpdateStatus | null>(null)
  const [stats, setStats] = useState<BulkUpdateStats | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  // Fetch current status
  const fetchStatus = useCallback(async () => {
    try {
      const response = await apiService.get<BulkUpdateStatus>(
        '/company-profiles/bulk-update/status'
      )
      if (response) {
        setStatus(response)
      }
    } catch (error) {
      // Silent fail - status will remain null
    }
  }, [])

  // Fetch statistics
  const fetchStats = useCallback(async () => {
    try {
      const response = await apiService.get<BulkUpdateStats>(
        '/company-profiles/bulk-update/statistics'
      )
      if (response) {
        setStats(response)
      }
    } catch (error) {
      // Silent fail - stats will remain null
    }
  }, [])

  // Start bulk update
  const startBulkUpdate = useCallback(
    async (config: BulkUpdateConfig) => {
      setIsLoading(true)
      try {
        const response = await apiService.post<BulkUpdateResponse>(
          '/company-profiles/bulk-update',
          config as unknown as Record<string, unknown>
        )
        if (response.success) {
          await fetchStatus()
          return { success: true, message: 'Bulk update started successfully' }
        } else {
          throw new Error(response.error || 'Failed to start bulk update')
        }
      } catch (error) {
        const errorMessage =
          error instanceof Error ? error.message : 'Failed to start bulk update'
        return {
          success: false,
          error: errorMessage,
        }
      } finally {
        setIsLoading(false)
      }
    },
    [fetchStatus]
  )

  // Stop bulk update
  const stopBulkUpdate = useCallback(async () => {
    setIsLoading(true)
    try {
      const response = await apiService.post<BulkUpdateResponse>(
        '/company-profiles/bulk-update/stop',
        undefined
      )
      if (response.success) {
        await fetchStatus()
        return { success: true, message: 'Bulk update stopped successfully' }
      } else {
        throw new Error(response.error || 'Failed to stop bulk update')
      }
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to stop bulk update'
      return {
        success: false,
        error: errorMessage,
      }
    } finally {
      setIsLoading(false)
    }
  }, [fetchStatus])

  // Refresh status and stats
  const refresh = useCallback(async () => {
    await Promise.all([fetchStatus(), fetchStats()])
  }, [fetchStatus, fetchStats])

  // Auto-refresh when update is running
  useEffect(() => {
    if (status?.isRunning) {
      const interval = setInterval(fetchStatus, 2000) // Poll every 2 seconds
      return () => clearInterval(interval)
    }
  }, [status?.isRunning, fetchStatus])

  // Initial load
  useEffect(() => {
    refresh()
  }, [refresh])

  // Utility functions
  const formatDuration = useCallback((seconds: number) => {
    if (seconds < 60) return `${seconds}s`
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
  }, [])

  const formatTimeRemaining = useCallback(
    (seconds: number | null) => {
      if (!seconds) return 'Calculating...'
      return formatDuration(seconds)
    },
    [formatDuration]
  )

  const getSuccessRate = useCallback(() => {
    if (!status || status.processedTickers === 0) return 0
    return (status.successfulUpdates / status.processedTickers) * 100
  }, [status])

  const getProgressPercentage = useCallback(() => {
    if (!status || status.totalTickers === 0) return 0
    return (status.processedTickers / status.totalTickers) * 100
  }, [status])

  return {
    // State
    status,
    stats,
    isLoading,

    // Actions
    startBulkUpdate,
    stopBulkUpdate,
    refresh,

    // Computed values
    successRate: getSuccessRate(),
    progressPercentage: getProgressPercentage(),

    // Utilities
    formatDuration,
    formatTimeRemaining,
  }
}
