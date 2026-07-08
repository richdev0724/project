import React, { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardDescription,
} from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Separator } from '@/components/ui/separator'
import { useToast } from '@/components/ui/use-toast'
import { apiService } from '@/api/api.service'
import {
  RefreshCw,
  Play,
  Square,
  BarChart3,
  CheckCircle,
  XCircle,
  AlertCircle,
} from 'lucide-react'

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

export const BulkUpdateManager: React.FC = () => {
  const [status, setStatus] = useState<BulkUpdateStatus | null>(null)
  const [stats, setStats] = useState<BulkUpdateStats | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [updateConfig, setUpdateConfig] = useState({
    batchSize: 50,
    delayBetweenBatches: 2000,
    maxConcurrentRequests: 10,
    sampleSize: null as number | null,
  })
  const { toast } = useToast()

  // Poll for status updates
  useEffect(() => {
    const pollStatus = async () => {
      try {
        const response = await apiService.get<BulkUpdateStatus>(
          '/company-profiles/bulk-update/status'
        )
        if (response) {
          setStatus(response)
        }
      } catch (error) {
        // Silently handle error
      }
    }

    // Initial fetch
    pollStatus()

    // Poll every 2 seconds if update is running
    const interval = setInterval(() => {
      if (status?.isRunning) {
        pollStatus()
      }
    }, 2000)

    return () => clearInterval(interval)
  }, [status?.isRunning])

  // Fetch statistics
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const response = await apiService.get<BulkUpdateStats>(
          '/company-profiles/bulk-update/statistics'
        )
        if (response) {
          setStats(response)
        }
      } catch (error) {
        // Silently handle error
      }
    }

    fetchStats()
  }, [status?.endTime])

  const startBulkUpdate = async () => {
    setIsLoading(true)
    try {
      const response = await apiService.post<BulkUpdateResponse>(
        '/company-profiles/bulk-update',
        updateConfig as Record<string, unknown>
      )
      if (response.success) {
        toast({
          title: 'Bulk Update Started',
          description:
            'Company profile update is now running in the background.',
        })
      } else {
        throw new Error(response.error || 'Failed to start bulk update')
      }
    } catch (error) {
      toast({
        title: 'Error',
        description: `Failed to start bulk update: ${error}`,
        variant: 'destructive',
      })
    } finally {
      setIsLoading(false)
    }
  }

  const stopBulkUpdate = async () => {
    setIsLoading(true)
    try {
      const response = await apiService.post<BulkUpdateResponse>(
        '/company-profiles/bulk-update/stop',
        undefined
      )
      if (response.success) {
        toast({
          title: 'Bulk Update Stopped',
          description: 'Company profile update has been stopped.',
        })
      } else {
        throw new Error(response.error || 'Failed to stop bulk update')
      }
    } catch (error) {
      toast({
        title: 'Error',
        description: `Failed to stop bulk update: ${error}`,
        variant: 'destructive',
      })
    } finally {
      setIsLoading(false)
    }
  }

  const formatDuration = (seconds: number) => {
    if (seconds < 60) return `${seconds}s`
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`
  }

  const formatTimeRemaining = (seconds: number | null) => {
    if (!seconds) return 'Calculating...'
    return formatDuration(seconds)
  }

  const getStatusIcon = () => {
    if (!status) return <AlertCircle className='h-4 w-4' />
    if (status.isRunning) return <RefreshCw className='h-4 w-4 animate-spin' />
    if (status.successfulUpdates > 0)
      return <CheckCircle className='h-4 w-4 text-green-500' />
    return <XCircle className='h-4 w-4 text-red-500' />
  }

  const getStatusColor = () => {
    if (!status) return 'secondary'
    if (status.isRunning) return 'default'
    if (status.successfulUpdates > 0) return 'bullish'
    return 'destructive'
  }

  return (
    <div className='space-y-6'>
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <BarChart3 className='h-5 w-5' />
            Bulk Company Profile Update
          </CardTitle>
          <CardDescription>
            Update company profiles for all US stock tickers. This process can
            take several hours to complete.
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-4'>
          {/* Status Overview */}
          <div className='flex items-center justify-between'>
            <div className='flex items-center gap-2'>
              {getStatusIcon()}
              <span className='font-medium'>
                {status?.isRunning
                  ? 'Update Running'
                  : status
                    ? 'Update Complete'
                    : 'No Update'}
              </span>
              <Badge variant={getStatusColor()}>
                {status?.isRunning ? 'Active' : status ? 'Completed' : 'Idle'}
              </Badge>
            </div>
            <div className='flex gap-2'>
              <Button
                onClick={startBulkUpdate}
                disabled={isLoading || status?.isRunning}
                className='flex items-center gap-2'
              >
                <Play className='h-4 w-4' />
                Start Update
              </Button>
              <Button
                onClick={stopBulkUpdate}
                disabled={isLoading || !status?.isRunning}
                variant='destructive'
                className='flex items-center gap-2'
              >
                <Square className='h-4 w-4' />
                Stop Update
              </Button>
            </div>
          </div>

          {/* Progress Bar */}
          {status && (
            <div className='space-y-2'>
              <div className='flex justify-between text-sm'>
                <span>Progress</span>
                <span>{status.progress.toFixed(1)}%</span>
              </div>
              <Progress value={status.progress} className='w-full' />
              <div className='flex justify-between text-xs text-muted-foreground'>
                <span>
                  {status.processedTickers} / {status.totalTickers} tickers
                </span>
                <span>
                  Batch {status.currentBatch} / {status.totalBatches}
                </span>
              </div>
            </div>
          )}

          {/* Statistics */}
          {status && (
            <div className='grid grid-cols-2 gap-4 md:grid-cols-4'>
              <div className='text-center'>
                <div className='text-2xl font-bold text-green-600'>
                  {status.successfulUpdates}
                </div>
                <div className='text-xs text-muted-foreground'>Successful</div>
              </div>
              <div className='text-center'>
                <div className='text-2xl font-bold text-red-600'>
                  {status.failedUpdates}
                </div>
                <div className='text-xs text-muted-foreground'>Failed</div>
              </div>
              <div className='text-center'>
                <div className='text-2xl font-bold'>
                  {formatDuration(status.duration)}
                </div>
                <div className='text-xs text-muted-foreground'>Duration</div>
              </div>
              <div className='text-center'>
                <div className='text-2xl font-bold'>
                  {formatTimeRemaining(status.estimatedTimeRemaining)}
                </div>
                <div className='text-xs text-muted-foreground'>ETA</div>
              </div>
            </div>
          )}

          {/* Configuration */}
          <Separator />
          <div className='space-y-4'>
            <h4 className='font-medium'>Update Configuration</h4>
            <div className='grid grid-cols-1 gap-4 md:grid-cols-2'>
              <div>
                <label className='text-sm font-medium'>Batch Size</label>
                <input
                  type='number'
                  value={updateConfig.batchSize}
                  onChange={(e) =>
                    setUpdateConfig((prev) => ({
                      ...prev,
                      batchSize: parseInt(e.target.value) || 50,
                    }))
                  }
                  className='mt-1 w-full rounded-md border px-3 py-2'
                  min='1'
                  max='100'
                />
              </div>
              <div>
                <label className='text-sm font-medium'>
                  Delay Between Batches (ms)
                </label>
                <input
                  type='number'
                  value={updateConfig.delayBetweenBatches}
                  onChange={(e) =>
                    setUpdateConfig((prev) => ({
                      ...prev,
                      delayBetweenBatches: parseInt(e.target.value) || 2000,
                    }))
                  }
                  className='mt-1 w-full rounded-md border px-3 py-2'
                  min='0'
                  max='10000'
                />
              </div>
              <div>
                <label className='text-sm font-medium'>
                  Max Concurrent Requests
                </label>
                <input
                  type='number'
                  value={updateConfig.maxConcurrentRequests}
                  onChange={(e) =>
                    setUpdateConfig((prev) => ({
                      ...prev,
                      maxConcurrentRequests: parseInt(e.target.value) || 10,
                    }))
                  }
                  className='mt-1 w-full rounded-md border px-3 py-2'
                  min='1'
                  max='50'
                />
              </div>
              <div>
                <label className='text-sm font-medium'>
                  Sample Size (for testing)
                </label>
                <input
                  type='number'
                  value={updateConfig.sampleSize || ''}
                  onChange={(e) =>
                    setUpdateConfig((prev) => ({
                      ...prev,
                      sampleSize: e.target.value
                        ? parseInt(e.target.value)
                        : null,
                    }))
                  }
                  className='mt-1 w-full rounded-md border px-3 py-2'
                  placeholder='Leave empty for all tickers'
                  min='1'
                />
              </div>
            </div>
          </div>

          {/* Historical Statistics */}
          {stats && (
            <>
              <Separator />
              <div className='space-y-4'>
                <h4 className='font-medium'>Historical Statistics</h4>
                <div className='grid grid-cols-2 gap-4 md:grid-cols-4'>
                  <div className='text-center'>
                    <div className='text-lg font-semibold'>
                      {stats.totalUpdates}
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      Total Updates
                    </div>
                  </div>
                  <div className='text-center'>
                    <div className='text-lg font-semibold'>
                      {stats.successRate.toFixed(1)}%
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      Success Rate
                    </div>
                  </div>
                  <div className='text-center'>
                    <div className='text-lg font-semibold'>
                      {stats.averageTimePerTicker.toFixed(1)}s
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      Avg Time/Ticker
                    </div>
                  </div>
                  <div className='text-center'>
                    <div className='text-lg font-semibold'>
                      {stats.errorCount}
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      Total Errors
                    </div>
                  </div>
                </div>
                {stats.lastUpdate && (
                  <div className='text-sm text-muted-foreground'>
                    Last Update: {new Date(stats.lastUpdate).toLocaleString()}
                  </div>
                )}
              </div>
            </>
          )}

          {/* Recent Errors */}
          {status && status.errors.length > 0 && (
            <>
              <Separator />
              <div className='space-y-2'>
                <h4 className='font-medium text-red-600'>Recent Errors</h4>
                <div className='max-h-32 space-y-1 overflow-y-auto'>
                  {status.errors.slice(-5).map((error, index) => (
                    <Alert key={index} variant='destructive'>
                      <AlertDescription className='text-xs'>
                        <strong>{error.ticker}:</strong> {error.error}
                      </AlertDescription>
                    </Alert>
                  ))}
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
