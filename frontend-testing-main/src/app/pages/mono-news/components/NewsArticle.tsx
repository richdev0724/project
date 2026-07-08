import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { ExternalLink } from 'lucide-react'
import { motion } from 'motion/react'
import moment from 'moment'

export interface NewsArticleData {
  id: string
  title: string
  snippet?: string
  summary?: string
  image_url?: string
  imageUrl?: string
  published_at?: string
  time?: string
  article_url?: string
  sourceUrl?: string
  author?: string
  category?: string
  publisher?: {
    name: string
    logo_url: string
    favicon_url: string
    homepage_url: string
  }
  tickers?: string[]
  source: string
  sectors?: string[] // Added sectors field
}

interface NewsArticleProps {
  article: NewsArticleData
}

export function NewsArticle({ article }: NewsArticleProps) {
  const formatDateTime = (publishedAt: string) => {
    const momentDate = moment(publishedAt)
    const date = momentDate.format('MMMM D, YYYY')
    const time = momentDate.format('h:mma')
    return `${date} • ${time}`
  }

  return (
    <motion.div
      whileHover={{
        y: -2,
        transition: {
          duration: 0.3,
          ease: 'easeOut',
        },
      }}
    >
      <Card className='overflow-hidden rounded-md border border-gray-300/20 bg-card shadow-sm transition-all duration-300 hover:border-gray-400/60 hover:shadow-xl'>
        <a
          href={article.article_url || '#'}
          target='_blank'
          rel='noopener noreferrer'
          className='block'
        >
          <div className='flex gap-3 p-3'>
            {article.image_url && (
              <div className='h-16 w-24 flex-shrink-0 overflow-hidden rounded bg-muted'>
                <img
                  src={article.image_url}
                  alt={article.title}
                  className='h-full w-full object-cover'
                  onError={(e) => {
                    e.currentTarget.style.display = 'none'
                  }}
                />
              </div>
            )}
            <div className='min-w-0 flex-1'>
              <div className='mb-1 flex items-start justify-between gap-2'>
                <h3 className='line-clamp-2 text-sm font-semibold text-foreground/90 transition-colors hover:text-primary'>
                  {article.title}
                </h3>
                <ExternalLink className='h-3 w-3 flex-shrink-0 text-muted-foreground' />
              </div>
              <p className='mb-2 line-clamp-2 text-xs text-muted-foreground'>
                {article.snippet}
              </p>
              <div className='flex flex-wrap items-center gap-2'>
                <span className='text-xs text-muted-foreground'>
                  {formatDateTime(article.published_at || article.time || '')}
                </span>
                <span className='text-xs text-muted-foreground'>•</span>
                <span className='text-xs text-primary'>
                  {article.publisher?.name || article.source.toUpperCase()}
                </span>
                <div className='flex gap-1.5'>
                  {article.tickers?.map((ticker) => (
                    <Badge
                      key={ticker}
                      variant='secondary'
                      className='px-1.5 py-0 text-[10px]'
                    >
                      {ticker}
                    </Badge>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </a>
      </Card>
    </motion.div>
  )
}
