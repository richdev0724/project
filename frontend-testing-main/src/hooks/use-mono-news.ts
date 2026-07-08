import { useQuery } from '@tanstack/react-query'
import { newsService } from '@/api/news.service'
import { sectorMappingService } from '@/api/sector-mapping.service'

export interface MonoNewsArticle {
  id: string
  title: string
  snippet: string
  image_url?: string
  published_at: string
  article_url?: string
  author?: string
  publisher?: {
    name: string
    logo_url: string
    favicon_url: string
    homepage_url: string
  }
  tickers?: string[]
  source: 'polygon' | 'marketaux' | 'fmp'
  sectors?: string[] // Added sectors field
}

export interface MonoNewsResponse {
  data: MonoNewsArticle[]
  meta: {
    found: number
    returned: number
    limit: number
    page: number
    hasNextPage: boolean
  }
}

export const useMonoNews = (
  page: number = 1,
  limit: number = 20,
  enabled: boolean = true
) => {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['mono-news', page, limit],
    queryFn: async () => {
      const response = await newsService.getNews(page, limit)

      // Enhance articles with sector information
      const enhancedData = await Promise.all(
        response.data.map(async (article) => {
          let sectors: string[] = []

          if (article.tickers && article.tickers.length > 0) {
            const sectorMap = await sectorMappingService.getSectorsForTickers(
              article.tickers
            )
            sectors = Array.from(new Set(sectorMap.values()))
          }

          return {
            ...article,
            sectors,
          }
        })
      )

      return {
        ...response,
        data: enhancedData,
      }
    },
    staleTime: 1000 * 60 * 2, // 2 minutes - news is fresh for 2 minutes
    refetchInterval: 1000 * 60 * 5, // Refetch every 5 minutes for live updates
    refetchIntervalInBackground: true, // Refetch even when the tab is in the background
    enabled,
  })

  return {
    news: data?.data || [],
    meta: data?.meta,
    isLoading,
    error,
    refetch,
  }
}

export const useMonoTickerNews = (
  ticker: string,
  page: number = 1,
  limit: number = 20,
  enabled: boolean = true
) => {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['mono-ticker-news', ticker, page, limit],
    queryFn: async () => {
      const response = await newsService.getTickerNews(ticker, page, limit)

      // Enhance articles with sector information
      const enhancedData = await Promise.all(
        response.data.map(async (article) => {
          let sectors: string[] = []

          if (article.tickers && article.tickers.length > 0) {
            const sectorMap = await sectorMappingService.getSectorsForTickers(
              article.tickers
            )
            sectors = Array.from(new Set(sectorMap.values()))
          }

          return {
            ...article,
            sectors,
          }
        })
      )

      return {
        ...response,
        data: enhancedData,
      }
    },
    staleTime: 1000 * 60 * 2, // 2 minutes - news is fresh for 2 minutes
    refetchInterval: 1000 * 60 * 5, // Refetch every 5 minutes for live updates
    refetchIntervalInBackground: true, // Refetch even when the tab is in the background
    enabled: enabled && !!ticker,
  })

  return {
    news: data?.data || [],
    meta: data?.meta,
    isLoading,
    error,
    refetch,
  }
}
