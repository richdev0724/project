import { IconCircleFilled } from '@tabler/icons-react'
import NewsPagination from './pagination'
import { useNews } from '@/hooks/use-news'
import { useState } from 'react'
import moment from 'moment'
import Loader from '@/components/loader'

const NewsSection = () => {
  const [page, setPage] = useState(1)
  const { news, meta, isLoading, error } = useNews(page)

  const handlePageChange = (newPage: number) => {
    setPage(newPage + 1) // +1 because our API is 1-indexed but component is 0-indexed
  }

  return (
    <div className='h-[404px] w-full'>
      <div className='flex w-full items-center justify-between text-base'>
        <span className='font-normal'>Market News</span>
        <NewsPagination
          pageIndex={page - 1} // -1 because component is 0-indexed
          pageCount={page + (meta?.hasNextPage ? 1 : 0)}
          handlePageChange={handlePageChange}
        />
      </div>
      {isLoading ? (
        <div className='mt-5 flex h-[351px] items-center justify-center'>
          <Loader />
        </div>
      ) : error || !news ? (
        <div className='mt-5 flex h-[351px] items-center justify-center'>
          Error loading news
        </div>
      ) : (
        <div className='mt-5 max-h-[351px] space-y-4 overflow-y-auto px-2'>
          {news.slice(0, 10).map((article, index) => (
            <div key={index} className='flex items-center space-x-4'>
              <div className='flex h-16 w-16 flex-shrink-0 items-center justify-center overflow-hidden rounded-md bg-gray-200'>
                <img
                  className='h-full w-full object-cover'
                  src={article.image_url}
                  alt={article.title}
                />
              </div>
              <div className='flex-1'>
                <a
                  href={article.article_url}
                  target='_blank'
                  rel='noopener noreferrer'
                  className='block text-xs font-medium transition-colors hover:text-primary hover:underline'
                >
                  {article.title}
                </a>
                <div className='my-1 flex items-center'>
                  <IconCircleFilled size={4} />
                  <hr className='w-full border-t-[0.5px] border-[#FFFFFF]' />
                  <IconCircleFilled size={4} />
                </div>
                <p className='line-clamp-2 text-[11px] font-light'>
                  {article.snippet}
                </p>
                <div className='mt-1 flex items-center space-x-2'>
                  <p className='text-[10px] italic text-[#45CA89]'>
                    {moment(article.published_at).format('MMMM D, YYYY')}
                  </p>
                  <span className='text-[10px] text-muted-foreground'>
                    •{' '}
                    {moment(article.published_at).format('h:mmA').toLowerCase()}
                  </span>
                  {article.source === 'polygon' && article.publisher && (
                    <span className='text-[10px] text-muted-foreground'>
                      • {article.publisher.name}
                    </span>
                  )}
                  {article.source === 'marketaux' && (
                    <span className='text-[10px] text-muted-foreground'>
                      • Market Data
                    </span>
                  )}
                  {article.source === 'fmp' && (
                    <span className='text-[10px] text-muted-foreground'>
                      • Financial Modeling Prep
                    </span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default NewsSection
