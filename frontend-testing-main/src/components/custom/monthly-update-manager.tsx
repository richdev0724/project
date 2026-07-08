import React from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Calendar,
  Play,
  RefreshCw,
  CheckCircle,
  Clock,
  AlertTriangle,
  Database,
} from 'lucide-react'
import { useMonthlyUpdate } from '@/hooks/use-monthly-update'
import { toast } from 'sonner'

export const MonthlyUpdateManager: React.FC = () => {
  const {
    updateStatus,
    isChecking,
    forceUpdate,
    formatNextUpdate,
    formatLastUpdate,
    isUpdateDue,
    getProgressPercentage,
    getSuccessRate,
  } = useMonthlyUpdate()

  const handleForceUpdate = async () => {
    try {
      await forceUpdate()
      toast.success('Monthly update completed successfully')
    } catch (error) {
      toast.error('Failed to run monthly update')
    }
  }

  const getStatusColor = () => {
    if (updateStatus.isRunning) return 'bg-blue-500'
    if (isUpdateDue()) return 'bg-orange-500'
    return 'bg-green-500'
  }

  const getStatusText = () => {
    if (updateStatus.isRunning) return 'Running'
    if (isUpdateDue()) return 'Due'
    return 'Scheduled'
  }

  return (
    <div className='space-y-6'>
      {/* Main Status Card */}
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <Calendar className='h-5 w-5' />
            Monthly Update System
          </CardTitle>
          <CardDescription>
            Automatically updates company profiles after market close on the
            last Friday of each month.
          </CardDescription>
        </CardHeader>

        <CardContent className='space-y-6'>
          {/* Status Overview */}
          <div className='grid grid-cols-1 gap-4 md:grid-cols-4'>
            <div className='rounded-lg bg-muted p-4 text-center'>
              <div className='mb-2 flex items-center justify-center gap-2'>
                <div
                  className={`h-3 w-3 rounded-full ${getStatusColor()}`}
                ></div>
                <span className='font-semibold'>{getStatusText()}</span>
              </div>
              <div className='text-sm text-muted-foreground'>Update Status</div>
            </div>

            <div className='rounded-lg bg-muted p-4 text-center'>
              <div className='text-2xl font-bold text-primary'>
                {updateStatus.totalTickers}
              </div>
              <div className='text-sm text-muted-foreground'>Total Tickers</div>
            </div>

            <div className='rounded-lg bg-muted p-4 text-center'>
              <div className='text-2xl font-bold text-primary'>
                {getSuccessRate()}%
              </div>
              <div className='text-sm text-muted-foreground'>Success Rate</div>
            </div>

            <div className='rounded-lg bg-muted p-4 text-center'>
              <div className='text-sm font-medium'>{formatNextUpdate()}</div>
              <div className='text-sm text-muted-foreground'>Next Update</div>
            </div>
          </div>

          {/* Progress Bar (only show when running) */}
          {updateStatus.isRunning && (
            <div className='space-y-2'>
              <div className='flex justify-between text-sm'>
                <span>Update Progress</span>
                <span>
                  {updateStatus.updatedTickers}/{updateStatus.totalTickers}
                </span>
              </div>
              <Progress value={getProgressPercentage()} className='h-2' />
              {updateStatus.currentTicker && (
                <div className='text-center text-sm text-muted-foreground'>
                  Currently updating:{' '}
                  <span className='font-medium'>
                    {updateStatus.currentTicker}
                  </span>
                </div>
              )}
            </div>
          )}

          {/* Action Buttons */}
          <div className='flex gap-4'>
            <Button
              onClick={handleForceUpdate}
              disabled={updateStatus.isRunning || isChecking}
              className='flex-1'
            >
              {updateStatus.isRunning ? (
                <>
                  <RefreshCw className='mr-2 h-4 w-4 animate-spin' />
                  Running...
                </>
              ) : (
                <>
                  <Play className='mr-2 h-4 w-4' />
                  Force Update Now
                </>
              )}
            </Button>

            <Button
              variant='outline'
              disabled={updateStatus.isRunning}
              onClick={() => window.location.reload()}
            >
              <RefreshCw className='mr-2 h-4 w-4' />
              Refresh Status
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Update History */}
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <Clock className='h-5 w-5' />
            Update History
          </CardTitle>
        </CardHeader>

        <CardContent className='space-y-4'>
          <div className='grid grid-cols-1 gap-4 md:grid-cols-2'>
            <div className='rounded-lg border p-4'>
              <div className='mb-2 flex items-center gap-2'>
                <CheckCircle className='h-4 w-4 text-green-500' />
                <span className='font-medium'>Last Update</span>
              </div>
              <div className='text-sm text-muted-foreground'>
                {formatLastUpdate()}
              </div>
            </div>

            <div className='rounded-lg border p-4'>
              <div className='mb-2 flex items-center gap-2'>
                <Calendar className='h-4 w-4 text-blue-500' />
                <span className='font-medium'>Next Update</span>
              </div>
              <div className='text-sm text-muted-foreground'>
                {formatNextUpdate()}
              </div>
            </div>
          </div>

          {/* Last Update Results */}
          {updateStatus.lastUpdate && (
            <div className='grid grid-cols-1 gap-4 md:grid-cols-3'>
              <div className='rounded-lg bg-green-50 p-3 text-center dark:bg-green-950/20'>
                <div className='text-xl font-bold text-green-600 dark:text-green-400'>
                  {updateStatus.updatedTickers}
                </div>
                <div className='text-sm text-green-600 dark:text-green-400'>
                  Successfully Updated
                </div>
              </div>

              <div className='rounded-lg bg-red-50 p-3 text-center dark:bg-red-950/20'>
                <div className='text-xl font-bold text-red-600 dark:text-red-400'>
                  {updateStatus.failedTickers}
                </div>
                <div className='text-sm text-red-600 dark:text-red-400'>
                  Failed Updates
                </div>
              </div>

              <div className='rounded-lg bg-blue-50 p-3 text-center dark:bg-blue-950/20'>
                <div className='text-xl font-bold text-blue-600 dark:text-blue-400'>
                  {getSuccessRate()}%
                </div>
                <div className='text-sm text-blue-600 dark:text-blue-400'>
                  Success Rate
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Error Display */}
      {updateStatus.errors.length > 0 && (
        <Alert variant='destructive'>
          <AlertTriangle className='h-4 w-4' />
          <AlertDescription>
            <div className='mb-2 font-medium'>Update Errors:</div>
            <ul className='list-inside list-disc space-y-1'>
              {updateStatus.errors.slice(0, 5).map((error, index) => (
                <li key={index} className='text-sm'>
                  {error}
                </li>
              ))}
              {updateStatus.errors.length > 5 && (
                <li className='text-sm text-muted-foreground'>
                  ... and {updateStatus.errors.length - 5} more errors
                </li>
              )}
            </ul>
          </AlertDescription>
        </Alert>
      )}

      {/* System Information */}
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <Database className='h-5 w-5' />
            System Information
          </CardTitle>
        </CardHeader>

        <CardContent>
          <div className='space-y-3'>
            <div className='flex justify-between'>
              <span className='text-sm text-muted-foreground'>
                Update Schedule:
              </span>
              <span className='text-sm font-medium'>
                Last Friday of each month
              </span>
            </div>

            <div className='flex justify-between'>
              <span className='text-sm text-muted-foreground'>
                Update Time:
              </span>
              <span className='text-sm font-medium'>
                After market close (4 PM ET)
              </span>
            </div>

            <div className='flex justify-between'>
              <span className='text-sm text-muted-foreground'>
                Total Tickers:
              </span>
              <span className='text-sm font-medium'>
                {updateStatus.totalTickers}
              </span>
            </div>

            <div className='flex justify-between'>
              <span className='text-sm text-muted-foreground'>
                Cache Duration:
              </span>
              <span className='text-sm font-medium'>30 days</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
