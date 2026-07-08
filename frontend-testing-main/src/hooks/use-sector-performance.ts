import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { SECTOR_ETF_TICKERS, SECTORS } from '@/constants'
import { useMarketPrices } from './use-market-prices'

export interface SectorPerformanceData {
  ticker: string
  sector: string
  priceChanges: {
    '1d': number
    '30d': number
    '6m': number
  }
  percentageChanges: {
    '1d': number
    '30d': number
    '6m': number
  }
  currentPrice?: number
}

const fetchSectorSnapshots = async () => {
  const { data } = await axios.get('/api/polygon/sector-snapshots', {
    params: {
      tickers: SECTOR_ETF_TICKERS.join(','),
    },
  })
  return data
}

const calculatePerformanceMetrics = (
  snapshotData: any,
  monthlyData: any,
  sixMonthData: any
): SectorPerformanceData[] => {
  const results: SectorPerformanceData[] = []

  SECTOR_ETF_TICKERS.forEach((ticker, index) => {
    const sectorName = SECTORS[index]
    const tickerData = snapshotData[ticker]
    const monthlyResults = monthlyData?.[ticker]?.results || []
    const sixMonthResults = sixMonthData?.[ticker]?.results || []

    // Initialize with zeros
    let dailyPriceChange = 0
    let dailyPercentChange = 0
    let currentPrice = 0
    let monthlyPriceChange = 0
    let monthlyPercentChange = 0
    let sixMonthPriceChange = 0
    let sixMonthPercentChange = 0

    // Get daily performance from snapshot
    if (tickerData && !tickerData.error) {
      dailyPriceChange = tickerData.todaysChange || 0
      dailyPercentChange = tickerData.todaysChangePerc || 0
      currentPrice = tickerData.lastTrade?.p || tickerData.lastQuote?.p || 0
    }

    // Calculate 30-day performance
    if (monthlyResults.length > 0) {
      const monthAgoPrice = monthlyResults[0]?.c || monthlyResults[0]?.o || 0
      monthlyPriceChange = currentPrice - monthAgoPrice
      monthlyPercentChange =
        monthAgoPrice > 0 ? (monthlyPriceChange / monthAgoPrice) * 100 : 0
    }

    // Calculate 6-month performance
    if (sixMonthResults.length > 0) {
      const sixMonthAgoPrice =
        sixMonthResults[0]?.c || sixMonthResults[0]?.o || 0
      sixMonthPriceChange = currentPrice - sixMonthAgoPrice
      sixMonthPercentChange =
        sixMonthAgoPrice > 0
          ? (sixMonthPriceChange / sixMonthAgoPrice) * 100
          : 0
    }

    results.push({
      ticker,
      sector: sectorName,
      priceChanges: {
        '1d': Number(dailyPriceChange.toFixed(2)),
        '30d': Number(monthlyPriceChange.toFixed(2)),
        '6m': Number(sixMonthPriceChange.toFixed(2)),
      },
      percentageChanges: {
        '1d': Number(dailyPercentChange.toFixed(2)),
        '30d': Number(monthlyPercentChange.toFixed(2)),
        '6m': Number(sixMonthPercentChange.toFixed(2)),
      },
      currentPrice: Number(currentPrice.toFixed(2)),
    })
  })

  return results
}

export const useSectorPerformance = () => {
  // Fetch historical data for 30d and 6m
  const { data: monthlyData } = useMarketPrices('1M', SECTOR_ETF_TICKERS)
  const { data: sixMonthData } = useMarketPrices('6M', SECTOR_ETF_TICKERS)

  return useQuery({
    queryKey: ['sector-performance', monthlyData, sixMonthData],
    queryFn: async () => {
      const snapshotData = await fetchSectorSnapshots()
      return calculatePerformanceMetrics(
        snapshotData,
        monthlyData,
        sixMonthData
      )
    },
    enabled: !!(monthlyData && sixMonthData),
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}
