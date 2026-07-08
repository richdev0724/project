import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { useMarketStatus, isMarketOpen } from './use-market-status'

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

export function useTickerPrices(timeframe: string, ticker: string) {
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
    if (isWeekend(date)) return false
    return true
  }

  const isMarketCurrentlyOpen = () => {
    if (marketStatus) {
      return isMarketOpen(marketStatus)
    }

    const now = new Date()
    const nyTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )
    const hour = nyTime.getHours()
    const minute = nyTime.getMinutes()

    const marketOpen = hour > 9 || (hour === 9 && minute >= 30)
    const marketClose = hour < 16

    return marketOpen && marketClose && isMarketDay(nyTime)
  }

  const getLastTradingDay = () => {
    const now = new Date()
    const nyTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )

    const checkDate = new Date(nyTime)
    checkDate.setDate(checkDate.getDate() - 1)

    while (!isMarketDay(checkDate)) {
      checkDate.setDate(checkDate.getDate() - 1)
      if (nyTime.getTime() - checkDate.getTime() > 10 * 24 * 60 * 60 * 1000) {
        break
      }
    }

    return checkDate.toISOString().slice(0, 10)
  }

  const fetchTickerPrices = async () => {
    let date: string | undefined

    if (tf === '1D') {
      const nyTime = new Date(
        new Date().toLocaleString('en-US', { timeZone: 'America/New_York' })
      )
      const nyDateStr =
        nyTime.getFullYear() +
        '-' +
        String(nyTime.getMonth() + 1).padStart(2, '0') +
        '-' +
        String(nyTime.getDate()).padStart(2, '0')

      date = isWeekend(nyTime) ? undefined : nyDateStr

      // Always use market prices endpoint for 1D data to ensure consistency
      const requestUrl = '/api/polygon/market-prices'
      const params = {
        tickers: ticker,
        timeframe: tf,
        ...(date && { date }),
      }

      const { data } = await axios.get(requestUrl, { params })
      return data
    }

    date = isWeekend() ? getLastTradingDay() : undefined

    const requestUrl = '/api/polygon/market-prices'
    const params = {
      tickers: ticker,
      timeframe: tf,
      ...(date && { date }),
    }

    const { data } = await axios.get(requestUrl, { params })
    return data
  }

  const query = useQuery({
    queryKey: ['ticker-prices', tf, ticker],
    queryFn: fetchTickerPrices,
    refetchInterval: tf === '1D' && isMarketCurrentlyOpen() ? 60000 : false,
    refetchOnWindowFocus: tf === '1D' && isMarketCurrentlyOpen(),
    staleTime: tf === '1D' ? 0 : 5 * 60 * 1000,
    gcTime: tf === '1D' ? 30000 : 5 * 60 * 1000,
    enabled: !!ticker,
  })

  useEffect(() => {
    if (tf === '1D' && marketStatus) {
      queryClient.invalidateQueries({
        queryKey: ['ticker-prices', tf, ticker],
      })
    }
  }, [
    tf,
    marketStatus?.exchanges?.nasdaq,
    marketStatus?.exchanges?.nyse,
    queryClient,
    ticker,
  ])

  return query
}
