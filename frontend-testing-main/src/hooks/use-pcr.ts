import { useQuery } from '@tanstack/react-query'
import { pcrService } from '@/api/pcr.service'

export const usePCR = (ticker: string, date?: string) => {
  const { data } = useQuery({
    queryKey: ['pcr', ticker, date],
    queryFn: () => pcrService.getPCR(ticker, date),
    enabled: false, // Disabled PCR fetching
    staleTime: 0,
    gcTime: 0,
  })

  return {
    pcr: data?.pcr,
    date: data?.date,
    cached: data?.cached,
    isLoading: false, // Always return false since fetching is disabled
    error: null,
  }
}

export const usePCRBulk = (tickers: string[], date?: string) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['pcr-bulk', tickers, date],
    queryFn: () => pcrService.calculatePCR(tickers, date),
    enabled: tickers.length > 0,
    staleTime: 1000 * 60 * 60 * 24, // 24 hours
    refetchInterval: false,
  })

  return {
    results: data?.results || {},
    date: data?.date,
    count: data?.count,
    isLoading,
    error,
  }
}

export const usePCRCacheStats = () => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['pcr-cache-stats'],
    queryFn: () => pcrService.getCacheStats(),
    staleTime: 1000 * 60 * 5, // 5 minutes
  })

  return {
    stats: data,
    isLoading,
    error,
  }
}
