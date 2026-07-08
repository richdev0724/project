import { apiService } from './api.service'

export interface NewsArticle {
  title: string
  snippet: string
  image_url: string
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
}

export interface NewsResponse {
  data: NewsArticle[]
  meta: {
    found: number
    returned: number
    limit: number
    page: number
    hasNextPage: boolean
  }
}

export interface NewsHeadline {
  meta: Record<string, unknown>
  source: string
  created_at: string
  tags: string[]
  headline: string
  tickers: string[]
  is_major: boolean
  sentiment: 'neutral' | 'positive' | 'negative'
}

export interface HeadlinesResponse {
  data: NewsHeadline[]
  success: boolean
  error?: string
}

class NewsService {
  async getNews(page: number = 1, limit: number = 20): Promise<NewsResponse> {
    const response = await apiService.get<NewsResponse>(
      `/news?page=${page}&limit=${limit}`
    )
    return response
  }

  async getTickerNews(
    ticker: string,
    page: number = 1,
    limit: number = 20
  ): Promise<NewsResponse> {
    const response = await apiService.get<NewsResponse>(
      `/news/ticker/${ticker}?page=${page}&limit=${limit}`
    )
    return response
  }

  async getHeadlines(): Promise<HeadlinesResponse> {
    const response = await apiService.get<HeadlinesResponse>('/news/headlines')
    return response
  }
}

export const newsService = new NewsService()
