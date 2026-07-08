import { useState, useEffect, useRef } from 'react'
import { X, Plus, Search } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  tickerSearchService,
  TickerSearchResult,
} from '@/api/ticker-search.service'

interface WatchlistSettingsProps {
  watchlist: string[]
  onUpdateWatchlist: (tickers: string[]) => void
  onClose: () => void
}

export function WatchlistSettings({
  watchlist,
  onUpdateWatchlist,
  onClose,
}: WatchlistSettingsProps) {
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<TickerSearchResult[]>([])
  const [isSearching, setIsSearching] = useState(false)
  const [showResults, setShowResults] = useState(false)
  const searchTimeoutRef = useRef<NodeJS.Timeout>()
  const inputRef = useRef<HTMLInputElement>(null)

  // Debounced search
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current)
    }

    if (searchQuery.length >= 1) {
      setIsSearching(true)
      searchTimeoutRef.current = setTimeout(async () => {
        try {
          const results = await tickerSearchService.searchTickers(searchQuery)
          setSearchResults(results)
          setShowResults(true)
        } catch (error) {
          setSearchResults([])
        } finally {
          setIsSearching(false)
        }
      }, 300)
    } else {
      setSearchResults([])
      setShowResults(false)
      setIsSearching(false)
    }

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current)
      }
    }
  }, [searchQuery])

  const handleAddTicker = (ticker: string) => {
    if (!watchlist.includes(ticker)) {
      onUpdateWatchlist([...watchlist, ticker])
    }
    setSearchQuery('')
    setShowResults(false)
    inputRef.current?.focus()
  }

  const handleRemoveTicker = (ticker: string) => {
    onUpdateWatchlist(watchlist.filter((t) => t !== ticker))
  }

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && searchResults.length > 0) {
      handleAddTicker(searchResults[0].symbol)
    }
  }

  return (
    <Dialog open={true} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className='!top-[20vh] left-1/2 !translate-y-0 -translate-x-1/2 sm:max-w-md'>
        <DialogHeader>
          <DialogTitle>Manage Watchlist</DialogTitle>
          <DialogDescription>
            Search for tickers or company names to add to your watchlist
          </DialogDescription>
        </DialogHeader>

        <div className='space-y-4'>
          {/* Current Watchlist */}
          <div className='space-y-2'>
            <p className='text-sm text-muted-foreground'>
              Your Watchlist ({watchlist.length})
            </p>
            {watchlist.length === 0 ? (
              <p className='py-4 text-center text-sm text-muted-foreground'>
                No tickers in watchlist. Search and add some to get started!
              </p>
            ) : (
              <div className='flex flex-wrap gap-2'>
                {watchlist.map((ticker) => (
                  <Badge
                    key={ticker}
                    variant='secondary'
                    className='gap-2 pr-1 font-mono'
                  >
                    {ticker}
                    <button
                      onClick={() => handleRemoveTicker(ticker)}
                      className='rounded-sm p-0.5 transition-colors hover:bg-background/50'
                    >
                      <X className='h-3 w-3' />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
          </div>

          {/* Search Input */}
          <div className='relative'>
            <Search className='absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground' />
            <Input
              ref={inputRef}
              type='text'
              placeholder='Search ticker or company name...'
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={handleKeyPress}
              className='pl-10'
            />
            {isSearching && (
              <div className='absolute right-3 top-1/2 -translate-y-1/2'>
                <div className='h-4 w-4 animate-spin rounded-full border-2 border-primary border-t-transparent' />
              </div>
            )}
          </div>

          {/* Search Results */}
          {showResults && searchResults.length > 0 && (
            <div className='max-h-48 overflow-y-auto rounded-md border bg-card'>
              {searchResults.map((result) => (
                <div
                  key={result.symbol}
                  className='flex cursor-pointer items-center justify-between p-2 hover:bg-muted/50'
                  onClick={() => handleAddTicker(result.symbol)}
                >
                  <div className='min-w-0 flex-1'>
                    <div className='flex items-center gap-2'>
                      <span className='text-sm font-semibold'>
                        {result.symbol}
                      </span>
                      <span className='truncate text-xs text-muted-foreground'>
                        {result.name}
                      </span>
                    </div>
                    <div className='text-xs text-muted-foreground'>
                      {result.exchange} • {result.type}
                    </div>
                  </div>
                  <Plus className='h-4 w-4 text-muted-foreground' />
                </div>
              ))}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
