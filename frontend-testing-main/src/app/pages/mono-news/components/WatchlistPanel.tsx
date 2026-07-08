import { Badge } from '@/components/ui/badge'
import { ExternalLink, TrendingUp, Clock, Settings } from 'lucide-react'
import { WatchlistSettings } from './WatchlistSettings'
import { NewsArticleData } from './NewsArticle'
import { Button } from '@/components/custom/button'
import { useState } from 'react'
import moment from 'moment'

interface WatchlistPanelProps {
  watchlist: string[]
  onUpdateWatchlist: (tickers: string[]) => void
  news: NewsArticleData[]
  showSettingsButton?: boolean
}

export function WatchlistPanel({
  watchlist,
  onUpdateWatchlist,
  news,
  showSettingsButton = true,
}: WatchlistPanelProps) {
  const [settingsOpen, setSettingsOpen] = useState(false)

  const formatDateTime = (publishedAt: string) => {
    const momentDate = moment(publishedAt)
    const date = momentDate.format('MMMM D, YYYY')
    const time = momentDate.format('h:mma')
    return `${date} • ${time}`
  }

  return (
    <div className='flex h-full flex-col overflow-hidden bg-background'>
      {/* Header */}
      <div className='border-b border-border bg-gradient-to-b from-card to-card/80 px-4 py-3'>
        <div className='flex items-center justify-between'>
          <h3 className='text-base font-bold text-primary'>Watchlist News</h3>
          {showSettingsButton && (
            <Button
              variant='outline'
              size='icon'
              className='h-8 w-8'
              onClick={() => setSettingsOpen(true)}
            >
              <Settings className='h-4 w-4 text-foreground' />
            </Button>
          )}
        </div>
      </div>

      {/* News Section */}
      <div className='flex-1 space-y-1 overflow-y-auto p-2'>
        {watchlist.length === 0 ? (
          <div className='px-4 py-12 text-center'>
            <div className='mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-muted/30'>
              <TrendingUp className='h-8 w-8 text-muted-foreground/50' />
            </div>
            <p className='text-sm text-muted-foreground'>
              Add tickers to your watchlist
            </p>
            <p className='mt-1 text-xs text-muted-foreground/70'>
              to see relevant news here
            </p>
          </div>
        ) : news.length === 0 ? (
          <div className='px-4 py-12 text-center'>
            <div className='mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-muted/30'>
              <Clock className='h-8 w-8 text-muted-foreground/50' />
            </div>
            <p className='text-sm text-muted-foreground'>No news found for</p>
            <p className='mt-1 text-xs text-muted-foreground/70'>
              your watchlist tickers
            </p>
          </div>
        ) : (
          news.map((article) => (
            <a
              key={article.id}
              href={article.article_url || '#'}
              target='_blank'
              rel='noopener noreferrer'
              className='group block origin-top'
            >
              <div className='relative overflow-hidden rounded-md border border-gray-300/20 bg-card/80 p-3 shadow-md transition-all duration-300 hover:border-gray-400/60 hover:shadow-xl'>
                <div>
                  <div className='mb-2 flex items-start gap-2'>
                    <h4 className='line-clamp-2 flex-1 text-xs font-bold leading-snug text-foreground/90 transition-colors group-hover:text-primary'>
                      {article.title}
                    </h4>
                    <ExternalLink className='mt-0.5 h-3 w-3 flex-shrink-0 text-muted-foreground/50 transition-colors group-hover:text-primary/70' />
                  </div>

                  <p className='mb-2 line-clamp-2 text-xs leading-relaxed text-muted-foreground'>
                    {article.snippet}
                  </p>

                  <div className='mb-2 flex items-center gap-2 text-[10px] text-muted-foreground/80'>
                    <span>
                      {formatDateTime(
                        article.published_at || article.time || ''
                      )}
                    </span>
                    <span>•</span>
                    <span className='font-medium'>
                      {article.publisher?.name || article.source}
                    </span>
                  </div>

                  <div className='flex flex-wrap gap-1.5'>
                    {article.tickers?.map((ticker) => (
                      <Badge
                        key={ticker}
                        variant='outline'
                        className='h-5 border border-purple-600/40 bg-gradient-to-r from-purple-900/80 to-purple-800/80 px-2 font-mono text-[10px] font-semibold text-muted-foreground/80'
                      >
                        {ticker}
                      </Badge>
                    ))}
                  </div>
                </div>
              </div>
            </a>
          ))
        )}
      </div>

      {/* Settings Dialog */}
      {settingsOpen && (
        <WatchlistSettings
          watchlist={watchlist}
          onUpdateWatchlist={onUpdateWatchlist}
          onClose={() => setSettingsOpen(false)}
        />
      )}
    </div>
  )
}
