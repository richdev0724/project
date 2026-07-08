import React, { useState, useMemo, useRef, useCallback, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Layout } from '@/components/custom/layout'
import { Header } from './components/Header'
import { TickerTape } from './components/TickerTape'
import { NewsArticle } from './components/NewsArticle'
import { WatchlistPanel } from './components/WatchlistPanel'
import { UnifiedSettingsModal } from './components/UnifiedSettingsModal'
import { useMonoNews, useMonoTickerNews } from '@/hooks/use-mono-news'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { sectorMappingService } from '@/api/sector-mapping.service'
import { usePreloadCompanyProfiles } from '@/hooks/use-company-profile'
import { Button } from '@/components/custom/button'

export default function MonoNews() {
  const [searchQuery, setSearchQuery] = useState('')
  const [activeTab, setActiveTab] = useState('all')
  const [watchlist, setWatchlist] = useState<string[]>(['AAPL', 'TSLA', 'MSFT'])
  const [sidebarWidth, setSidebarWidth] = useState(320) // Default 320px
  const [isResizing, setIsResizing] = useState(false)
  const [page, setPage] = useState(1)
  const [selectedSectors, setSelectedSectors] = useState<string[]>([
    'Communication Services',
    'Energy',
    'Financials',
    'Information Technology',
    'Utilities',
  ])
  const [showUnifiedSettings, setShowUnifiedSettings] = useState(false)
  const [selectedTickerTapeIndices, setSelectedTickerTapeIndices] = useState<
    string[]
  >(['SPX', 'DJIA', 'COMP', 'RUT', 'VIX', 'BTC', 'ETH'])
  const containerRef = useRef<HTMLDivElement>(null)
  const newsContainerRef = useRef<HTMLDivElement>(null)

  // Get available sectors
  const availableSectors = useMemo(() => {
    return sectorMappingService.getAvailableSectors()
  }, [])

  // Preload company profiles hook
  const { preloadProfiles } = usePreloadCompanyProfiles()

  // Load selected sectors from localStorage on component mount
  useEffect(() => {
    const savedSectors = localStorage.getItem('mono-news-selected-sectors')
    if (savedSectors) {
      try {
        const parsed = JSON.parse(savedSectors)
        if (Array.isArray(parsed) && parsed.length > 0) {
          setSelectedSectors(parsed)
        }
      } catch (error) {
        // Failed to parse saved sectors
      }
    }

    const savedTickerTape = localStorage.getItem('mono-news-ticker-tape')
    if (savedTickerTape) {
      try {
        const parsed = JSON.parse(savedTickerTape)
        if (Array.isArray(parsed) && parsed.length > 0) {
          setSelectedTickerTapeIndices(parsed)
        }
      } catch (error) {
        // Failed to parse saved ticker tape
      }
    }

    // Preload common company profiles for better performance
    const commonTickers = [
      'AAPL',
      'MSFT',
      'GOOGL',
      'AMZN',
      'TSLA',
      'META',
      'NVDA',
      'NFLX',
    ]
    preloadProfiles(commonTickers)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Save selected sectors to localStorage
  const saveSelectedSectors = useCallback(() => {
    localStorage.setItem(
      'mono-news-selected-sectors',
      JSON.stringify(selectedSectors)
    )
  }, [selectedSectors])

  // Handle sector toggle
  const handleSectorToggle = useCallback((sector: string) => {
    setSelectedSectors((prev) => {
      if (prev.includes(sector)) {
        return prev.filter((s) => s !== sector)
      } else {
        return [...prev, sector]
      }
    })
  }, [])

  // Handle ticker tape toggle
  const handleTickerTapeToggle = useCallback((ticker: string) => {
    setSelectedTickerTapeIndices((prev) => {
      if (prev.includes(ticker)) {
        return prev.filter((t) => t !== ticker)
      } else {
        return [...prev, ticker]
      }
    })
  }, [])

  // Save selected ticker tape indices to localStorage
  const saveSelectedTickerTape = useCallback(() => {
    localStorage.setItem(
      'mono-news-ticker-tape',
      JSON.stringify(selectedTickerTapeIndices)
    )
  }, [selectedTickerTapeIndices])

  // Fetch live news data (20 items per page)
  const {
    news: allNews,
    isLoading: newsLoading,
    error: newsError,
    refetch,
  } = useMonoNews(page, 20)

  // Fetch watchlist-specific news - using individual hooks for each ticker
  const aaplNews = useMonoTickerNews('AAPL', 1, 20, watchlist.includes('AAPL'))
  const tslaNews = useMonoTickerNews('TSLA', 1, 20, watchlist.includes('TSLA'))
  const msftNews = useMonoTickerNews('MSFT', 1, 20, watchlist.includes('MSFT'))

  const watchlistNews = useMemo(() => {
    const allWatchlistNews = [
      ...(aaplNews.news || []),
      ...(tslaNews.news || []),
      ...(msftNews.news || []),
    ]

    return allWatchlistNews
      .map((article, index) => ({
        ...article,
        id: `${article.title}-${article.published_at}-${index}`, // Generate unique ID
      }))
      .sort(
        (a, b) =>
          new Date(b.published_at).getTime() -
          new Date(a.published_at).getTime()
      )
      .slice(0, 20) // Limit to 20 most recent articles
  }, [aaplNews.news, tslaNews.news, msftNews.news])

  // Animation variants
  const pageVariants = {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -20 },
  }

  const staggerContainer = {
    animate: {
      transition: {
        staggerChildren: 0.1,
      },
    },
  }

  const itemVariants = {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
  }

  // Filter news by search query (ticker search)
  const filteredNews = useMemo(() => {
    const newsWithIds = allNews.map((article, index) => ({
      ...article,
      id: `${article.title}-${article.published_at}-${index}`, // Generate unique ID
    }))

    if (!searchQuery.trim()) {
      return newsWithIds
    }

    const query = searchQuery.toUpperCase().trim()
    return newsWithIds.filter(
      (article) =>
        article.tickers?.some((ticker) => ticker.includes(query)) ||
        article.title.toUpperCase().includes(query) ||
        article.snippet.toUpperCase().includes(query)
    )
  }, [searchQuery, allNews])

  // Further filter by sector tab
  const displayedNews = useMemo(() => {
    if (activeTab === 'all') {
      return filteredNews
    }
    // Filter by sector
    return filteredNews.filter((article) =>
      article.sectors?.some(
        (sector) => sector.toLowerCase() === activeTab.toLowerCase()
      )
    )
  }, [filteredNews, activeTab])

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    setIsResizing(true)
    e.preventDefault()
  }, [])

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isResizing || !containerRef.current) return

      const containerRect = containerRef.current.getBoundingClientRect()
      const containerWidth = containerRect.width
      const mouseX = e.clientX - containerRect.left

      // Calculate new sidebar width
      const newSidebarWidth = containerWidth - mouseX

      // Constrain between 350px (minimum) and 50% of container width
      const minWidth = 350
      const maxWidth = containerWidth * 0.5

      const constrainedWidth = Math.max(
        minWidth,
        Math.min(maxWidth, newSidebarWidth)
      )
      setSidebarWidth(constrainedWidth)
    },
    [isResizing]
  )

  const handleMouseUp = useCallback(() => {
    setIsResizing(false)
  }, [])

  // Add event listeners for mouse move and up
  React.useEffect(() => {
    if (isResizing) {
      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = 'col-resize'
      document.body.style.userSelect = 'none'
    } else {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
  }, [isResizing, handleMouseMove, handleMouseUp])

  // Scroll to top when page changes
  useEffect(() => {
    if (newsContainerRef.current) {
      newsContainerRef.current.scrollTo({ top: 0, behavior: 'smooth' })
    }
  }, [page])

  return (
    <Layout className='overflow-hidden'>
      <motion.div
        variants={pageVariants}
        initial='initial'
        animate='animate'
        exit='exit'
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className='h-full'
      >
        <div
          className='dark flex h-full flex-col overflow-hidden bg-background'
          data-theme='dark'
        >
          <motion.div variants={itemVariants} className='flex-shrink-0'>
            <Header
              searchQuery={searchQuery}
              onSearchChange={setSearchQuery}
              onSettingsClick={() => setShowUnifiedSettings(true)}
            />
          </motion.div>

          <motion.div variants={itemVariants} className='flex-shrink-0'>
            <TickerTape selectedTickers={selectedTickerTapeIndices} />
          </motion.div>

          <motion.div
            ref={containerRef}
            className='flex flex-1 overflow-hidden'
            variants={staggerContainer}
            initial='initial'
            animate='animate'
          >
            {/* Main News Section */}
            <motion.div
              ref={newsContainerRef}
              className='relative min-w-0 flex-1 overflow-y-auto'
              style={{ width: `calc(100% - ${sidebarWidth}px)` }}
              variants={itemVariants}
            >
              <div className='mx-auto px-4'>
                <Tabs
                  value={activeTab}
                  onValueChange={setActiveTab}
                  className='w-full'
                >
                  {/* Sticky Header with Tabs and Pagination */}
                  <div className='sticky left-0 right-0 top-0 z-50 -mx-4 border-b border-border/40 bg-background/95 px-4 py-2 backdrop-blur-md'>
                    <div className='flex items-center justify-between gap-3'>
                      <div className='scrollbar-hide flex-1 overflow-x-auto scroll-smooth'>
                        <TabsList className='h-8 min-w-max flex-nowrap gap-1'>
                          <TabsTrigger
                            value='all'
                            className='h-7 whitespace-nowrap text-xs'
                          >
                            All News
                          </TabsTrigger>
                          {selectedSectors.map((sector) => (
                            <TabsTrigger
                              key={sector}
                              value={sector.toLowerCase()}
                              className='h-7 whitespace-nowrap text-xs'
                            >
                              {sector}
                            </TabsTrigger>
                          ))}
                        </TabsList>
                      </div>

                      {/* Pagination Controls - Flow Table Style */}
                      <div className='flex flex-shrink-0 items-center justify-center'>
                        <Button
                          variant='ghost'
                          className='h-7 w-7 p-0'
                          onClick={() => setPage(Math.max(1, page - 1))}
                          disabled={page === 1}
                        >
                          <span className='sr-only'>Go to previous page</span>
                          <span className='flex h-3.5 w-3.5 items-center justify-center text-[11px] text-primary'>
                            {'<'}
                          </span>
                        </Button>
                        <span className='mx-1.5 flex items-center text-[11px] text-popOverBorder'>
                          Page {page}
                        </span>
                        <Button
                          variant='ghost'
                          className='h-7 w-7 p-0'
                          onClick={() => setPage(page + 1)}
                        >
                          <span className='sr-only'>Go to next page</span>
                          <span className='flex h-3.5 w-3.5 items-center justify-center text-[11px] text-primary'>
                            {'>'}
                          </span>
                        </Button>
                      </div>
                    </div>
                  </div>

                  <TabsContent
                    value={activeTab}
                    className='min-h-[500px] space-y-3 pt-3'
                  >
                    {newsLoading ? (
                      <div className='flex items-center justify-center py-20'>
                        <div className='flex flex-col items-center space-y-4'>
                          <div className='h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent'></div>
                          <p className='text-sm text-muted-foreground'>
                            Loading news...
                          </p>
                        </div>
                      </div>
                    ) : newsError ? (
                      <div className='flex items-center justify-center py-20'>
                        <div className='flex flex-col items-center space-y-4'>
                          <p className='text-red-500'>Failed to load news</p>
                          <button
                            onClick={() => refetch()}
                            className='rounded bg-primary px-4 py-2 text-sm text-white hover:bg-primary/80'
                          >
                            Retry
                          </button>
                        </div>
                      </div>
                    ) : (
                      <>
                        {searchQuery && (
                          <motion.div
                            className='mb-3 text-sm text-muted-foreground'
                            initial={{ opacity: 0, y: 10 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ duration: 0.3 }}
                          >
                            Found {displayedNews.length}{' '}
                            {displayedNews.length === 1
                              ? 'article'
                              : 'articles'}
                            {searchQuery && ` for "${searchQuery}"`}
                          </motion.div>
                        )}

                        {displayedNews.length === 0 ? (
                          <motion.div
                            className='py-12 text-center text-muted-foreground'
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            transition={{ duration: 0.4 }}
                          >
                            <p>No articles found matching your search.</p>
                            <p className='mt-2 text-sm'>
                              Try searching for tickers like AAPL, TSLA, MSFT,
                              or NVDA
                            </p>
                          </motion.div>
                        ) : (
                          <AnimatePresence>
                            {displayedNews.map((article, index) => (
                              <motion.div
                                key={article.id}
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0, y: -20 }}
                                transition={{
                                  duration: 0.4,
                                  delay: index * 0.05,
                                  ease: 'easeOut',
                                }}
                              >
                                <NewsArticle article={article} />
                              </motion.div>
                            ))}
                          </AnimatePresence>
                        )}
                      </>
                    )}
                  </TabsContent>
                </Tabs>
              </div>
            </motion.div>

            {/* Resizable Divider */}
            <motion.div
              className={`w-1 cursor-col-resize bg-border transition-colors hover:bg-primary/50 ${isResizing ? 'bg-primary/70' : ''}`}
              onMouseDown={handleMouseDown}
              variants={itemVariants}
            />

            {/* Watchlist Sidebar */}
            <motion.div
              className='flex-shrink-0 overflow-hidden border-l-2 border-border bg-card'
              style={{ width: `${sidebarWidth}px` }}
              variants={itemVariants}
            >
              <WatchlistPanel
                watchlist={watchlist}
                onUpdateWatchlist={setWatchlist}
                news={watchlistNews}
                showSettingsButton={false}
              />
            </motion.div>
          </motion.div>
        </div>
      </motion.div>

      {/* Unified Settings Modal */}
      <UnifiedSettingsModal
        isOpen={showUnifiedSettings}
        onClose={() => setShowUnifiedSettings(false)}
        watchlist={watchlist}
        onUpdateWatchlist={setWatchlist}
        onSaveWatchlist={() => {
          localStorage.setItem('mono-news-watchlist', JSON.stringify(watchlist))
          setShowUnifiedSettings(false)
        }}
        availableSectors={availableSectors}
        selectedSectors={selectedSectors}
        onSectorToggle={handleSectorToggle}
        onSaveSectors={saveSelectedSectors}
        selectedTickerTapeIndices={selectedTickerTapeIndices}
        onTickerTapeToggle={handleTickerTapeToggle}
        onSaveTickerTape={saveSelectedTickerTape}
      />
    </Layout>
  )
}
