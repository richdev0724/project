import React from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { RefreshCw, Trash2, Database, Download } from 'lucide-react'
import { useCompanyProfileCache } from '@/hooks/use-company-profile-cache'
import { toast } from 'sonner'

export const CompanyProfileCacheManager: React.FC = () => {
  const {
    cacheStats,
    updateCacheStats,
    clearExpiredCache,
    clearAllCache,
    preloadCommonProfiles,
    formatCacheSize,
    isClearingCache,
  } = useCompanyProfileCache()

  const handleClearExpired = async () => {
    try {
      const clearedCount = await clearExpiredCache()
      toast.success(`Cleared ${clearedCount} expired cache entries`)
    } catch (error) {
      toast.error('Failed to clear expired cache')
    }
  }

  const handleClearAll = () => {
    clearAllCache()
    toast.success('All cache cleared')
  }

  const handlePreloadCommon = async () => {
    try {
      await preloadCommonProfiles()
      toast.success('Common profiles preloaded successfully')
    } catch (error) {
      toast.error('Failed to preload common profiles')
    }
  }

  const handleRefreshStats = () => {
    updateCacheStats()
    toast.success('Cache stats updated')
  }

  return (
    <Card className='w-full max-w-2xl'>
      <CardHeader>
        <CardTitle className='flex items-center gap-2'>
          <Database className='h-5 w-5' />
          Company Profile Cache Manager
        </CardTitle>
        <CardDescription>
          Manage cached company profile data. Data is cached for 30 days and
          updated monthly.
        </CardDescription>
      </CardHeader>

      <CardContent className='space-y-6'>
        {/* Cache Statistics */}
        <div className='grid grid-cols-1 gap-4 md:grid-cols-3'>
          <div className='rounded-lg bg-muted p-4 text-center'>
            <div className='text-2xl font-bold text-primary'>
              {cacheStats.totalCached}
            </div>
            <div className='text-sm text-muted-foreground'>Cached Profiles</div>
          </div>

          <div className='rounded-lg bg-muted p-4 text-center'>
            <div className='text-2xl font-bold text-primary'>
              {formatCacheSize(cacheStats.cacheSize)}
            </div>
            <div className='text-sm text-muted-foreground'>Cache Size</div>
          </div>

          <div className='rounded-lg bg-muted p-4 text-center'>
            <div className='text-sm font-medium'>
              {cacheStats.lastUpdated === 'Never' ? (
                <Badge variant='secondary'>Never</Badge>
              ) : (
                <Badge variant='outline'>
                  {new Date(cacheStats.lastUpdated).toLocaleDateString()}
                </Badge>
              )}
            </div>
            <div className='text-sm text-muted-foreground'>Last Updated</div>
          </div>
        </div>

        <Separator />

        {/* Cache Actions */}
        <div className='space-y-4'>
          <h3 className='text-lg font-semibold'>Cache Actions</h3>

          <div className='grid grid-cols-1 gap-4 md:grid-cols-2'>
            <Button
              onClick={handleRefreshStats}
              variant='outline'
              className='w-full'
            >
              <RefreshCw className='mr-2 h-4 w-4' />
              Refresh Stats
            </Button>

            <Button
              onClick={handlePreloadCommon}
              variant='outline'
              className='w-full'
            >
              <Download className='mr-2 h-4 w-4' />
              Preload Common Tickers
            </Button>

            <Button
              onClick={handleClearExpired}
              variant='outline'
              className='w-full'
              disabled={isClearingCache}
            >
              <Trash2 className='mr-2 h-4 w-4' />
              {isClearingCache ? 'Clearing...' : 'Clear Expired'}
            </Button>

            <Button
              onClick={handleClearAll}
              variant='destructive'
              className='w-full'
            >
              <Trash2 className='mr-2 h-4 w-4' />
              Clear All Cache
            </Button>
          </div>
        </div>

        {/* Cache Info */}
        <div className='rounded-lg bg-blue-50 p-4 dark:bg-blue-950/20'>
          <h4 className='mb-2 font-semibold text-blue-900 dark:text-blue-100'>
            Cache Information
          </h4>
          <ul className='space-y-1 text-sm text-blue-800 dark:text-blue-200'>
            <li>• Company profiles are cached for 30 days</li>
            <li>• Data is automatically updated monthly</li>
            <li>• Cache persists across browser sessions</li>
            <li>• Stale data is served if API is unavailable</li>
          </ul>
        </div>
      </CardContent>
    </Card>
  )
}
