import { useQuery } from '@tanstack/react-query'
import axios from 'axios'
import { useMarketStatus, isMarketOpen } from './use-market-status'

export function useTickerSnapshot(ticker: string) {
  const { data: marketStatus } = useMarketStatus()

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

    return marketOpen && marketClose
  }

  const fetchTickerSnapshot = async () => {
    const snapshotUrl = '/api/polygon/sector-snapshots'
    const { data } = await axios.get(snapshotUrl, {
      params: { tickers: ticker },
    })
    return data
  }

  return useQuery({
    queryKey: ['ticker-snapshot', ticker],
    queryFn: fetchTickerSnapshot,
    refetchInterval: isMarketCurrentlyOpen() ? 60000 : false,
    refetchOnWindowFocus: isMarketCurrentlyOpen(),
    staleTime: 0,
    gcTime: 0,
    enabled: !!ticker,
  })
}
