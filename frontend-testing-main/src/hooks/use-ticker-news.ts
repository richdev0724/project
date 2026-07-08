import { useQuery } from '@tanstack/react-query'
import { newsService } from '@/api/news.service'

export const useTickerNews = (ticker: string, page: number = 1) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['ticker-news', ticker, page],
    queryFn: () => newsService.getTickerNews(ticker, page),
    staleTime: 1000 * 60, // 1 minute - data is fresh for 1 minute
    refetchInterval: 1000 * 60, // Refetch every 60 seconds
    refetchIntervalInBackground: true, // Refetch even when the tab is in the background
    enabled: !!ticker, // Only run query if ticker is provided
  })

  return {
    news: data?.data,
    meta: data?.meta,
    isLoading,
    error,
  }
}
