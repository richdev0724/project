import { useQuery } from '@tanstack/react-query'
import { useMarketPrices } from './use-market-prices'
import { useTickerSnapshot } from './use-ticker-snapshot'

export interface TickerPerformanceData {
  percentageChanges: {
    '1D': number
    '1W': number
    '1M': number
    '3M': number
    '6M': number
    YTD: number
    '1Y': number
  }
}

const calculatePerformanceMetrics = (
  snapshotData: any,
  weekData: any,
  monthData: any,
  threeMonthData: any,
  sixMonthData: any,
  yearData: any,
  ytdData: any,
  ticker: string
): TickerPerformanceData => {
  // Initialize with zeros
  const performance: TickerPerformanceData = {
    percentageChanges: {
      '1D': 0,
      '1W': 0,
      '1M': 0,
      '3M': 0,
      '6M': 0,
      YTD: 0,
      '1Y': 0,
    },
  }

  // Get current price from snapshot
  const tickerData = snapshotData?.[ticker]
  const currentPrice = tickerData?.day?.c || 0

  // Get daily performance from snapshot
  if (tickerData) {
    performance.percentageChanges['1D'] =
      Number(tickerData.todaysChangePerc?.toFixed(2)) || 0
  }

  // Calculate weekly performance (last 5 trading days)
  if (weekData?.[ticker]?.results?.length > 0) {
    // Get the first trading day from the data (oldest date, which is 5 trading days ago)
    const weekAgoPrice =
      weekData[ticker].results[0]?.c || weekData[ticker].results[0]?.o || 0
    if (weekAgoPrice && currentPrice) {
      performance.percentageChanges['1W'] = Number(
        (((currentPrice - weekAgoPrice) / weekAgoPrice) * 100).toFixed(2)
      )
    }
  }

  // Calculate monthly performance (last ~22 trading days)
  if (monthData?.[ticker]?.results?.length > 0) {
    const monthAgoPrice =
      monthData[ticker].results[0]?.c || monthData[ticker].results[0]?.o || 0
    if (monthAgoPrice && currentPrice) {
      performance.percentageChanges['1M'] = Number(
        (((currentPrice - monthAgoPrice) / monthAgoPrice) * 100).toFixed(2)
      )
    }
  }

  // Calculate 3-month performance (last ~66 trading days)
  if (threeMonthData?.[ticker]?.results?.length > 0) {
    const threeMonthAgoPrice =
      threeMonthData[ticker].results[0]?.c ||
      threeMonthData[ticker].results[0]?.o ||
      0
    if (threeMonthAgoPrice && currentPrice) {
      performance.percentageChanges['3M'] = Number(
        (
          ((currentPrice - threeMonthAgoPrice) / threeMonthAgoPrice) *
          100
        ).toFixed(2)
      )
    }
  }

  // Calculate 6-month performance (last ~132 trading days)
  if (sixMonthData?.[ticker]?.results?.length > 0) {
    const sixMonthAgoPrice =
      sixMonthData[ticker].results[0]?.c ||
      sixMonthData[ticker].results[0]?.o ||
      0
    if (sixMonthAgoPrice && currentPrice) {
      performance.percentageChanges['6M'] = Number(
        (((currentPrice - sixMonthAgoPrice) / sixMonthAgoPrice) * 100).toFixed(
          2
        )
      )
    }
  }

  // Calculate YTD performance (from first trading day of year)
  if (ytdData?.[ticker]?.results?.length > 0) {
    const ytdPrice =
      ytdData[ticker].results[0]?.c || ytdData[ticker].results[0]?.o || 0
    if (ytdPrice && currentPrice) {
      performance.percentageChanges['YTD'] = Number(
        (((currentPrice - ytdPrice) / ytdPrice) * 100).toFixed(2)
      )
    }
  }

  // Calculate 1-year performance
  if (yearData?.[ticker]?.results?.length > 0) {
    const yearAgoPrice =
      yearData[ticker].results[0]?.c || yearData[ticker].results[0]?.o || 0
    if (yearAgoPrice && currentPrice) {
      performance.percentageChanges['1Y'] = Number(
        (((currentPrice - yearAgoPrice) / yearAgoPrice) * 100).toFixed(2)
      )
    }
  }

  return performance
}

export const useTickerPerformance = (ticker: string) => {
  // Get real-time data
  const { data: snapshotData } = useTickerSnapshot(ticker)

  // Fetch historical data for different timeframes
  const { data: weekData } = useMarketPrices('5D', [ticker])
  const { data: monthData } = useMarketPrices('1M', [ticker])
  const { data: threeMonthData } = useMarketPrices('3M', [ticker])
  const { data: sixMonthData } = useMarketPrices('6M', [ticker])
  const { data: yearData } = useMarketPrices('1Y', [ticker])
  const { data: ytdData } = useMarketPrices('YTD', [ticker])

  return useQuery({
    queryKey: [
      'ticker-performance',
      ticker,
      snapshotData,
      weekData,
      monthData,
      threeMonthData,
      sixMonthData,
      yearData,
      ytdData,
    ],
    queryFn: () =>
      calculatePerformanceMetrics(
        snapshotData,
        weekData,
        monthData,
        threeMonthData,
        sixMonthData,
        yearData,
        ytdData,
        ticker
      ),
    enabled: !!(ticker && snapshotData), // Only require snapshot data, others are optional
    staleTime: 0,
    gcTime: 0,
  })
}
