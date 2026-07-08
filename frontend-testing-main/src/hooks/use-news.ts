import { useQuery } from '@tanstack/react-query'
import { newsService } from '@/api/news.service'

export const useNews = (page: number = 1) => {
  const { data, isLoading, error } = useQuery({
    queryKey: ['news', page],
    queryFn: () => newsService.getNews(page),
    staleTime: 1000 * 60 * 5, // 5 minutes
    refetchInterval: 1000 * 60 * 60, // Refetch every 60 minutes
    refetchIntervalInBackground: true, // Refetch even when the tab is in the background
  })

  return {
    news: data?.data,
    meta: data?.meta,
    isLoading,
    error,
  }
}
