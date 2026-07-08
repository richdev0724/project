import { IconCircleFilled } from '@tabler/icons-react'
import { useEffect, useState } from 'react'
import { newsService, type NewsHeadline } from '@/api/news.service'

const NewsList = () => {
  const [news, setNews] = useState<NewsHeadline[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchNews = async () => {
      try {
        setLoading(true)
        const response = await newsService.getHeadlines()
        if (response.data) {
          setNews(response.data)
        }
      } catch (err) {
        setError('Failed to fetch headlines')
      } finally {
        setLoading(false)
      }
    }

    fetchNews()
  }, [])

  // Format time ago
  const formatTimeAgo = (dateString: string) => {
    const date = new Date(dateString)
    const now = new Date()
    const diffInMinutes = Math.floor(
      (now.getTime() - date.getTime()) / (1000 * 60)
    )

    if (diffInMinutes < 1) {
      return 'Just now'
    } else if (diffInMinutes < 60) {
      return `${diffInMinutes}m ago`
    } else if (diffInMinutes < 1440) {
      const hours = Math.floor(diffInMinutes / 60)
      return `${hours}h ago`
    } else {
      const days = Math.floor(diffInMinutes / 1440)
      return `${days}d ago`
    }
  }
  if (loading) {
    return (
      <div className='flex max-w-[350px] flex-col border-l-2 border-[#1F2025] p-5'>
        <div className='mb-4 flex items-center justify-between'>
          <h2 className='text-lg font-semibold italic text-white'>
            Today's News
          </h2>
        </div>
        <div className='flex h-full items-center justify-center'>
          <div className='text-sm text-gray-400'>Loading news...</div>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className='flex max-w-[350px] flex-col border-l-2 border-[#1F2025] p-5'>
        <div className='mb-4 flex items-center justify-between'>
          <h2 className='text-lg font-semibold italic text-white'>
            Today's News
          </h2>
        </div>
        <div className='flex h-full items-center justify-center'>
          <div className='text-sm text-red-400'>{error}</div>
        </div>
      </div>
    )
  }

  return (
    <div className='flex max-w-[350px] flex-col border-l-2 border-[#1F2025] p-5'>
      <div className='mb-4 flex items-center justify-between'>
        <h2 className='text-lg font-semibold italic text-white'>
          Today's Headlines
        </h2>
      </div>

      <div className='space-y-4 overflow-y-auto'>
        {news.map((article, index) => {
          const timeAgo = formatTimeAgo(article.created_at)
          return (
            <div
              key={index}
              className='animate-fade-in group cursor-pointer transition-opacity hover:opacity-80'
              style={{ animationDelay: `${index * 50}ms` }}
            >
              <div className='flex items-start gap-2'>
                <div className='flex-1'>
                  <p className='text-[13px] font-medium leading-[18px] text-white'>
                    {article.headline}
                  </p>
                  <div className='my-1.5 flex items-center'>
                    <IconCircleFilled size={6} />
                    <hr className='-mx-1 h-0.5 w-full border-t border-[#FFFFFF]' />
                    <IconCircleFilled size={6} />
                  </div>
                  <div className='flex items-center justify-between text-xs font-light italic'>
                    <div className='flex items-center gap-1.5'>
                      <span className='text-[#45CA89]'>{article.source}</span>
                      {article.is_major && (
                        <span className='font-italic text-orange-500'>
                          major headline
                        </span>
                      )}
                    </div>
                    <span className='text-[#45CA89]'>{timeAgo}</span>
                  </div>
                  {article.tickers && article.tickers.length > 0 && (
                    <div className='mt-1.5 flex flex-wrap gap-1'>
                      {article.tickers.slice(0, 3).map((ticker, idx) => (
                        <span
                          key={idx}
                          className='rounded bg-[#2A2D31] px-1.5 py-0.5 text-[10px] font-medium text-gray-300'
                        >
                          {ticker}
                        </span>
                      ))}
                      {article.tickers.length > 3 && (
                        <span className='rounded bg-[#2A2D31] px-1.5 py-0.5 text-[10px] font-medium text-gray-300'>
                          +{article.tickers.length - 3}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default NewsList
