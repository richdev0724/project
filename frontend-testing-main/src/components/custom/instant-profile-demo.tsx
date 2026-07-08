import React, { useState, useEffect } from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  useInstantCompanyProfile,
  useBulkPreload,
  useCompanyProfileCache,
} from '@/hooks/use-instant-company-profile'
import { Database, Zap, Clock, CheckCircle, AlertCircle } from 'lucide-react'

interface InstantProfileDemoProps {
  initialTicker?: string
}

export const InstantProfileDemo: React.FC<InstantProfileDemoProps> = ({
  initialTicker = 'AAPL',
}) => {
  const [ticker, setTicker] = useState(initialTicker)
  const [searchTicker, setSearchTicker] = useState('')
  const [loadTime, setLoadTime] = useState<number | null>(null)

  const { profile, isLoading, error, isInstant } =
    useInstantCompanyProfile(ticker)
  const { preloadCommonTickers, preloadSectorTickers } = useBulkPreload()
  const { stats, refreshStats, clearCache } = useCompanyProfileCache()

  // Measure load time
  useEffect(() => {
    if (ticker) {
      const startTime = performance.now()
      // The hook will automatically load the profile
      // We'll measure when it's done loading
      const timer = setTimeout(() => {
        if (profile || error) {
          const endTime = performance.now()
          setLoadTime(endTime - startTime)
        }
      }, 100)

      return () => clearTimeout(timer)
    }
  }, [ticker, profile, error])

  const handleSearch = () => {
    if (searchTicker.trim()) {
      setTicker(searchTicker.trim().toUpperCase())
      setSearchTicker('')
    }
  }

  const handlePreloadSector = (sector: string) => {
    preloadSectorTickers(sector)
  }

  const getLoadTimeColor = (time: number | null) => {
    if (!time) return 'text-gray-500'
    if (time < 50) return 'text-green-500'
    if (time < 200) return 'text-yellow-500'
    return 'text-red-500'
  }

  const getLoadTimeIcon = (time: number | null) => {
    if (!time) return <Clock className='h-4 w-4' />
    if (time < 50) return <Zap className='h-4 w-4' />
    if (time < 200) return <Zap className='h-4 w-4' />
    return <AlertCircle className='h-4 w-4' />
  }

  return (
    <div className='space-y-6'>
      {/* Header */}
      <Card>
        <CardHeader>
          <CardTitle className='flex items-center gap-2'>
            <Zap className='h-5 w-5 text-yellow-500' />
            Instant Company Profile System
          </CardTitle>
          <CardDescription>
            Multi-layer caching system for instant company profile loading
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-4'>
          {/* Search */}
          <div className='flex gap-2'>
            <Input
              placeholder='Enter ticker symbol (e.g., AAPL, MSFT)'
              value={searchTicker}
              onChange={(e) => setSearchTicker(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
            />
            <Button onClick={handleSearch}>Load Profile</Button>
          </div>

          {/* Load Time Indicator */}
          <div className='flex items-center gap-2'>
            {getLoadTimeIcon(loadTime)}
            <span className={getLoadTimeColor(loadTime)}>
              Load Time:{' '}
              {loadTime ? `${loadTime.toFixed(2)}ms` : 'Measuring...'}
            </span>
            {isInstant && (
              <Badge variant='secondary' className='ml-2'>
                <CheckCircle className='mr-1 h-3 w-3' />
                Instant
              </Badge>
            )}
          </div>

          {/* Cache Stats */}
          <div className='grid grid-cols-3 gap-4 text-sm'>
            <div className='text-center'>
              <div className='font-semibold'>{stats.memoryCache}</div>
              <div className='text-gray-500'>Memory Cache</div>
            </div>
            <div className='text-center'>
              <div className='font-semibold'>{stats.preloadQueue}</div>
              <div className='text-gray-500'>Preload Queue</div>
            </div>
            <div className='text-center'>
              <div className='font-semibold'>
                {stats.isPreloading ? 'Yes' : 'No'}
              </div>
              <div className='text-gray-500'>Preloading</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Profile Display */}
      {profile && (
        <Card>
          <CardHeader>
            <CardTitle className='flex items-center gap-2'>
              <Database className='h-5 w-5 text-blue-500' />
              {profile.companyName} ({profile.ticker})
            </CardTitle>
            <CardDescription>
              {profile.description?.substring(0, 200)}...
            </CardDescription>
          </CardHeader>
          <CardContent className='space-y-4'>
            <div className='grid grid-cols-2 gap-4'>
              <div>
                <div className='text-sm font-semibold text-gray-500'>
                  Industry
                </div>
                <div>{profile.industry}</div>
              </div>
              <div>
                <div className='text-sm font-semibold text-gray-500'>
                  Sector
                </div>
                <div>{profile.sector}</div>
              </div>
              <div>
                <div className='text-sm font-semibold text-gray-500'>CEO</div>
                <div>{profile.ceo}</div>
              </div>
              <div>
                <div className='text-sm font-semibold text-gray-500'>
                  Employees
                </div>
                <div>{profile.employees?.toLocaleString()}</div>
              </div>
              <div>
                <div className='text-sm font-semibold text-gray-500'>
                  Market Cap
                </div>
                <div>${profile.marketCap?.toLocaleString()}</div>
              </div>
              <div>
                <div className='text-sm font-semibold text-gray-500'>
                  Exchange
                </div>
                <div>{profile.exchange}</div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Error Display */}
      {error && (
        <Card className='border-red-200 bg-red-50'>
          <CardContent className='pt-6'>
            <div className='flex items-center gap-2 text-red-600'>
              <AlertCircle className='h-4 w-4' />
              <span>Failed to load profile: {error.message}</span>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Loading State */}
      {isLoading && (
        <Card>
          <CardContent className='pt-6'>
            <div className='flex items-center gap-2'>
              <div className='h-4 w-4 animate-spin rounded-full border-b-2 border-blue-500'></div>
              <span>Loading profile for {ticker}...</span>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Preload Controls */}
      <Card>
        <CardHeader>
          <CardTitle>Preload Controls</CardTitle>
          <CardDescription>Preload profiles for instant access</CardDescription>
        </CardHeader>
        <CardContent className='space-y-4'>
          <div className='flex flex-wrap gap-2'>
            <Button onClick={preloadCommonTickers} variant='outline'>
              Preload Common Tickers
            </Button>
            <Button
              onClick={() => handlePreloadSector('technology')}
              variant='outline'
            >
              Tech Sector
            </Button>
            <Button
              onClick={() => handlePreloadSector('finance')}
              variant='outline'
            >
              Finance Sector
            </Button>
            <Button
              onClick={() => handlePreloadSector('healthcare')}
              variant='outline'
            >
              Healthcare Sector
            </Button>
            <Button
              onClick={() => handlePreloadSector('etfs')}
              variant='outline'
            >
              Popular ETFs
            </Button>
          </div>

          <Separator />

          <div className='flex gap-2'>
            <Button onClick={refreshStats} variant='outline'>
              Refresh Stats
            </Button>
            <Button onClick={clearCache} variant='destructive'>
              Clear Cache
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* System Architecture */}
      <Card>
        <CardHeader>
          <CardTitle>System Architecture</CardTitle>
          <CardDescription>
            Multi-layer caching for instant loading
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className='space-y-3'>
            <div className='flex items-center gap-3'>
              <Badge variant='secondary'>1</Badge>
              <span className='font-semibold'>Memory Cache</span>
              <Badge className='bg-green-100 text-green-800'>Instant</Badge>
            </div>
            <div className='flex items-center gap-3'>
              <Badge variant='secondary'>2</Badge>
              <span className='font-semibold'>LocalStorage Cache</span>
              <Badge className='bg-blue-100 text-blue-800'>Near-Instant</Badge>
            </div>
            <div className='flex items-center gap-3'>
              <Badge variant='secondary'>3</Badge>
              <span className='font-semibold'>ClickHouse Database</span>
              <Badge className='bg-yellow-100 text-yellow-800'>Fast</Badge>
            </div>
            <div className='flex items-center gap-3'>
              <Badge variant='secondary'>4</Badge>
              <span className='font-semibold'>FMP API</span>
              <Badge className='bg-red-100 text-red-800'>Slow</Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
