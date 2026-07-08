import { useState, useEffect, useCallback } from 'react'
import { monthlyUpdateService } from '@/api/monthly-update.service'

interface UpdateStatus {
  isRunning: boolean
  lastUpdate: string | null
  nextUpdate: string | null
  totalTickers: number
  updatedTickers: number
  failedTickers: number
  currentTicker: string | null
  progress: number
  errors: string[]
}

export const useMonthlyUpdate = () => {
  const [updateStatus, setUpdateStatus] = useState<UpdateStatus>(() =>
    monthlyUpdateService.getUpdateStatus()
  )

  const [isChecking, setIsChecking] = useState(false)

  // Update status periodically
  useEffect(() => {
    const interval = setInterval(() => {
      setUpdateStatus(monthlyUpdateService.getUpdateStatus())
    }, 1000) // Update every second when running

    return () => clearInterval(interval)
  }, [])

  // Check if update should run on mount
  useEffect(() => {
    const checkUpdate = async () => {
      setIsChecking(true)
      try {
        await monthlyUpdateService.checkAndRunUpdate()
        setUpdateStatus(monthlyUpdateService.getUpdateStatus())
      } catch (_error) {
        // Silent fail - update status will remain unchanged
      } finally {
        setIsChecking(false)
      }
    }

    checkUpdate()
  }, [])

  // Force run update
  const forceUpdate = useCallback(async () => {
    setIsChecking(true)
    try {
      await monthlyUpdateService.forceUpdate()
      setUpdateStatus(monthlyUpdateService.getUpdateStatus())
    } catch (_error) {
      // Silent fail - update status will remain unchanged
    } finally {
      setIsChecking(false)
    }
  }, [])

  // Add ticker to update list
  const addTicker = useCallback((ticker: string) => {
    monthlyUpdateService.addTickerToUpdateList(ticker)
    setUpdateStatus(monthlyUpdateService.getUpdateStatus())
  }, [])

  // Remove ticker from update list
  const removeTicker = useCallback((ticker: string) => {
    monthlyUpdateService.removeTickerFromUpdateList(ticker)
    setUpdateStatus(monthlyUpdateService.getUpdateStatus())
  }, [])

  // Get common tickers
  const getCommonTickers = useCallback(() => {
    return monthlyUpdateService.getCommonTickers()
  }, [])

  // Format next update date
  const formatNextUpdate = useCallback(() => {
    if (!updateStatus.nextUpdate) return 'Not scheduled'
    return new Date(updateStatus.nextUpdate).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      weekday: 'long',
    })
  }, [updateStatus.nextUpdate])

  // Format last update date
  const formatLastUpdate = useCallback(() => {
    if (!updateStatus.lastUpdate) return 'Never'
    return new Date(updateStatus.lastUpdate).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }, [updateStatus.lastUpdate])

  // Check if update is due
  const isUpdateDue = useCallback(() => {
    if (!updateStatus.nextUpdate) return false
    const nextUpdate = new Date(updateStatus.nextUpdate)
    const now = new Date()
    return now >= nextUpdate
  }, [updateStatus.nextUpdate])

  // Get progress percentage
  const getProgressPercentage = useCallback(() => {
    if (updateStatus.totalTickers === 0) return 0
    return Math.round(
      (updateStatus.updatedTickers / updateStatus.totalTickers) * 100
    )
  }, [updateStatus.updatedTickers, updateStatus.totalTickers])

  // Get success rate
  const getSuccessRate = useCallback(() => {
    if (updateStatus.totalTickers === 0) return 0
    return Math.round(
      (updateStatus.updatedTickers / updateStatus.totalTickers) * 100
    )
  }, [updateStatus.updatedTickers, updateStatus.totalTickers])

  return {
    updateStatus,
    isChecking,
    forceUpdate,
    addTicker,
    removeTicker,
    getCommonTickers,
    formatNextUpdate,
    formatLastUpdate,
    isUpdateDue,
    getProgressPercentage,
    getSuccessRate,
  }
}
