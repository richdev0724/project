import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useMarketStatus, isMarketOpen } from './use-market-status'

const DEFAULT_TICKERS = ['SPY', 'QQQ', 'IWM', 'DIA', 'VTI']

const getTimeframeLabel = (tf: string) => {
  switch (tf) {
    case '1D':
      return '1D'
    case '5D':
      return '5D'
    case '1M':
      return '1M'
    case '3M':
      return '3M'
    case '6M':
      return '6M'
    case 'YTD':
      return 'YTD'
    case '1Y':
      return '1Y'
    case '5Y':
      return '5Y'
    default:
      return '1D'
  }
}

export function useMarketPrices(
  timeframe: string,
  tickers: string[] = DEFAULT_TICKERS
) {
  const queryClient = useQueryClient()
  const tf = getTimeframeLabel(timeframe)
  const { data: marketStatus } = useMarketStatus()

  const isWeekend = (date?: Date) => {
    const checkDate = date || new Date()
    const nyTime = new Date(
      checkDate.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )
    const day = nyTime.getDay()
    return day === 0 || day === 6 // 0 is Sunday, 6 is Saturday
  }

  const isMarketDay = (date: Date) => {
    // Check if it's a weekend
    if (isWeekend(date)) return false

    // TODO: Add holiday checking here (NYSE holidays)
    // For now, just check weekends

    return true
  }

  const isMarketCurrentlyOpen = () => {
    // Use Polygon API market status if available, otherwise fall back to time-based logic
    if (marketStatus) {
      return isMarketOpen(marketStatus)
    }

    // Fallback to time-based logic
    const now = new Date()
    const nyTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )
    const hour = nyTime.getHours()
    const minute = nyTime.getMinutes()

    // Check if it's between 9:30 AM and 4:00 PM EST and it's a market day
    const marketOpen = hour > 9 || (hour === 9 && minute >= 30)
    const marketClose = hour < 16

    return marketOpen && marketClose && isMarketDay(nyTime)
  }

  const getLastTradingDay = () => {
    const now = new Date()
    const nyTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )

    // Start from yesterday and go back to find the last trading day
    const checkDate = new Date(nyTime)
    checkDate.setDate(checkDate.getDate() - 1)

    // Keep going back until we find a trading day
    while (!isMarketDay(checkDate)) {
      checkDate.setDate(checkDate.getDate() - 1)
      // Safety check - don't go back more than 10 days
      if (nyTime.getTime() - checkDate.getTime() > 10 * 24 * 60 * 60 * 1000) {
        break
      }
    }

    return checkDate.toISOString().slice(0, 10)
  }

  const filterWeekendData = (data: any) => {
    if (!data) return data

    const result: any = {}
    Object.keys(data).forEach((ticker) => {
      if (!data[ticker]?.results) {
        result[ticker] = data[ticker]
        return
      }

      // Filter out weekend data points
      const filteredResults = data[ticker].results.filter((item: any) => {
        const date = new Date(item.t)
        const nyDate = new Date(
          date.toLocaleString('en-US', { timeZone: 'America/New_York' })
        )
        const day = nyDate.getDay()
        return day !== 0 && day !== 6 // Remove Saturday and Sunday data
      })

      result[ticker] = {
        ...data[ticker],
        results: filteredResults,
      }
    })

    return result
  }

  const fetchMarketPrices = async () => {
    let date: string | undefined

    if (tf === '1D') {
      // For 1D timeframe, explicitly request the current NY date (if weekday) so we always attempt to
      // load same-day data while the market is open. The backend already contains fallback logic that
      // rolls back to the previous trading session if no data is returned for the supplied date, so
      // passing today's date is safe.

      // Get current NY date by creating a date object directly in NY timezone
      const nyTime = new Date(
        new Date().toLocaleString('en-US', { timeZone: 'America/New_York' })
      )
      // Format date as YYYY-MM-DD in NY timezone to avoid any TZ offset issues
      const nyDateStr =
        nyTime.getFullYear() +
        '-' +
        String(nyTime.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(nyTime.getDate()).padStart(2, '0')

      // If today is a weekday (Mon–Fri) we request today's date, otherwise leave undefined to let the
      // backend decide the most recent trading day (typically the previous Friday).
      date = isWeekend(nyTime) ? undefined : nyDateStr
    } else {
      // For other timeframes, only supply a date parameter when the current day is a weekend so that
      // we restrict the range to the last trading session.
      date = isWeekend() ? getLastTradingDay() : undefined
    }

    const requestUrl = '/api/polygon/market-prices'
    const params = {
      tickers: tickers.join(','),
      timeframe: tf,
      ...(date && { date }),
    }

    const { data } = await axios.get(requestUrl, { params })

    // Debug logging removed for production

    // Data fetched successfully

    // Filter out any weekend data points before returning
    return filterWeekendData(data)
  }

  const query = useQuery({
    queryKey: ['market-prices', tf, tickers.join(',')],
    queryFn: fetchMarketPrices,
    refetchInterval: tf === '1D' && isMarketCurrentlyOpen() ? 60000 : false, // Refetch every minute for 1D when market is open
    refetchOnWindowFocus: tf === '1D' && isMarketCurrentlyOpen(),
    staleTime: tf === '1D' ? 0 : 5 * 60 * 1000, // Always fetch fresh data for 1D
    gcTime: tf === '1D' ? 30000 : 5 * 60 * 1000, // Keep 1D data for 30 seconds only
  })

  // Force invalidate and refetch when market status changes for 1D timeframe
  useEffect(() => {
    if (tf === '1D' && marketStatus) {
      queryClient.invalidateQueries({
        queryKey: ['market-prices', tf, tickers.join(',')],
      })
    }
  }, [
    tf,
    marketStatus?.exchanges?.nasdaq,
    marketStatus?.exchanges?.nyse,
    queryClient,
    tickers,
  ])

  return query
}
