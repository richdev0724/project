import { useState, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import { PoliticianTradesTable } from './components/PoliticianTradesTable'
import { PoliticianPortfolio } from './components/PoliticianPortfolio'
import { TradeFilters } from './components/TradeFilters'
import {
  usePaginatedPoliticianTrades,
  usePoliticianPortfolio,
} from '@/hooks/use-politician-trades'
import { Loader2, ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'

type View = 'list' | 'portfolio'

const TRADES_PER_PAGE = 10

export default function Politicians() {
  const [view, setView] = useState<View>('list')
  const [selectedPolitician, setSelectedPolitician] = useState<string | null>(
    null
  )
  const [searchQuery, setSearchQuery] = useState('')
  const [partyFilter, setPartyFilter] = useState('all')
  const [tradeTypeFilter, setTradeTypeFilter] = useState('all')
  const [assetTypeFilter, setAssetTypeFilter] = useState('all')
  const [currentPage, setCurrentPage] = useState(1)
  const [, setDirection] = useState(0)

  // Fetch paginated trades from API
  const {
    data,
    isLoading: isLoadingTrades,
    error: tradesError,
  } = usePaginatedPoliticianTrades(1000) // Load 1000 at a time for client-side filtering

  // Flatten all pages into single array
  const allTrades = useMemo(() => {
    return data?.pages.flatMap((page) => page.data) || []
  }, [data])

  // Fetch portfolio data when a politician is selected
  const { data: portfolioData, isLoading: isLoadingPortfolio } =
    usePoliticianPortfolio(selectedPolitician)

  // Filter trades based on search and filters
  const filteredTrades = useMemo(() => {
    return allTrades.filter((trade) => {
      // Search filter
      const searchLower = searchQuery.toLowerCase()
      const matchesSearch =
        searchQuery === '' ||
        trade.politician.toLowerCase().includes(searchLower) ||
        trade.ticker.toLowerCase().includes(searchLower) ||
        trade.assetName.toLowerCase().includes(searchLower)

      // Party filter
      const matchesParty = partyFilter === 'all' || trade.party === partyFilter

      // Trade type filter
      const matchesTradeType =
        tradeTypeFilter === 'all' || trade.tradeType === tradeTypeFilter

      // Asset type filter
      const matchesAssetType =
        assetTypeFilter === 'all' || trade.assetType === assetTypeFilter

      return (
        matchesSearch && matchesParty && matchesTradeType && matchesAssetType
      )
    })
  }, [allTrades, searchQuery, partyFilter, tradeTypeFilter, assetTypeFilter])

  // Pagination calculations
  const totalPages = Math.ceil(filteredTrades.length / TRADES_PER_PAGE)
  const startIndex = (currentPage - 1) * TRADES_PER_PAGE
  const endIndex = startIndex + TRADES_PER_PAGE
  const paginatedTrades = filteredTrades.slice(startIndex, endIndex)

  // Reset to page 1 when filters change
  useMemo(() => {
    setCurrentPage(1)
  }, [searchQuery, partyFilter, tradeTypeFilter, assetTypeFilter])

  const handlePoliticianClick = (politicianName: string) => {
    setSelectedPolitician(politicianName)
    setView('portfolio')
  }

  const handleBackToList = () => {
    setView('list')
    setSelectedPolitician(null)
  }

  if (view === 'portfolio' && selectedPolitician) {
    return (
      <Layout>
        <Seo title='Politician Portfolio | MarketMono' />
        <Layout.Body className='px-10 py-5 md:px-10'>
          {isLoadingPortfolio ? (
            <div className='flex min-h-[400px] items-center justify-center'>
              <Loader2 className='h-8 w-8 animate-spin text-primary' />
            </div>
          ) : portfolioData ? (
            <AnimatePresence mode='wait'>
              <motion.div
                key='portfolio'
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.4, ease: 'easeOut' }}
              >
                <PoliticianPortfolio
                  portfolioData={portfolioData}
                  trades={allTrades}
                  onBack={handleBackToList}
                />
              </motion.div>
            </AnimatePresence>
          ) : (
            <div className='flex min-h-[400px] items-center justify-center text-muted-foreground'>
              No portfolio data available for {selectedPolitician}
            </div>
          )}
        </Layout.Body>
      </Layout>
    )
  }

  // Show loading state
  if (isLoadingTrades) {
    return (
      <Layout>
        <Seo title='Politicians Trades | MarketMono' />
        <Layout.Body className='px-10 py-5 md:px-10'>
          <div className='flex min-h-[400px] items-center justify-center'>
            <Loader2 className='h-8 w-8 animate-spin text-primary' />
          </div>
        </Layout.Body>
      </Layout>
    )
  }

  // Show error state
  if (tradesError) {
    return (
      <Layout>
        <Seo title='Politicians Trades | MarketMono' />
        <Layout.Body className='px-10 py-5 md:px-10'>
          <div className='flex min-h-[400px] flex-col items-center justify-center gap-4'>
            <p className='text-red-400'>Failed to load politician trades</p>
            <p className='text-sm text-muted-foreground'>
              Please try again later
            </p>
          </div>
        </Layout.Body>
      </Layout>
    )
  }

  return (
    <Layout className='overflow-hidden'>
      <Seo title='Politicians Trades | MarketMono' />
      <Layout.Body className='flex h-screen flex-col overflow-hidden px-10 pb-5 pt-10 md:px-10'>
        <AnimatePresence mode='wait'>
          <motion.div
            key='list'
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3 }}
            className='flex h-full flex-col overflow-hidden'
          >
            {/* Compact Stats Bar */}
            <motion.div
              className='mb-10 flex-shrink-0'
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.2 }}
            >
              <div className='flex items-center justify-between rounded-lg border border-[#2A2D35]/50 bg-gradient-to-r from-[#161F26]/80 via-[#28292D]/60 to-[#161F26]/80 px-4 py-3 backdrop-blur-sm'>
                <div className='flex items-center gap-8'>
                  {/* Total Trades */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Trades
                    </div>
                    <div className='text-lg font-semibold text-white'>
                      {filteredTrades.length.toLocaleString()}
                    </div>
                  </div>

                  {/* Divider */}
                  <div className='h-6 w-px bg-[#2A2D35]'></div>

                  {/* Total Value */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Total Value
                    </div>
                    <div className='text-lg font-semibold text-emerald-400'>
                      $
                      {(
                        filteredTrades.reduce(
                          (sum, trade) => sum + trade.amountMin,
                          0
                        ) / 1000000
                      ).toFixed(0)}
                      M+
                    </div>
                  </div>

                  {/* Divider */}
                  <div className='h-6 w-px bg-[#2A2D35]'></div>

                  {/* Politicians */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Politicians
                    </div>
                    <div className='text-lg font-semibold text-white'>
                      {new Set(filteredTrades.map((t) => t.politician)).size}
                    </div>
                  </div>

                  {/* Divider */}
                  <div className='h-6 w-px bg-[#2A2D35]'></div>

                  {/* Tickers */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Tickers
                    </div>
                    <div className='text-lg font-semibold text-white'>
                      {new Set(filteredTrades.map((t) => t.ticker)).size}
                    </div>
                  </div>

                  {/* Divider */}
                  <div className='h-6 w-px bg-[#2A2D35]'></div>

                  {/* Most Traded Ticker */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Top Ticker
                    </div>
                    <div className='text-lg font-semibold text-blue-400'>
                      {filteredTrades.length > 0
                        ? Object.entries(
                            filteredTrades.reduce(
                              (acc, t) => {
                                acc[t.ticker] = (acc[t.ticker] || 0) + 1
                                return acc
                              },
                              {} as Record<string, number>
                            )
                          ).sort((a, b) => b[1] - a[1])[0]?.[0] || 'N/A'
                        : 'N/A'}
                    </div>
                  </div>

                  {/* Divider */}
                  <div className='h-6 w-px bg-[#2A2D35]'></div>

                  {/* Most Active Politician */}
                  <div className='flex items-center gap-2'>
                    <div className='text-xs uppercase tracking-wide text-muted-foreground'>
                      Most Active
                    </div>
                    <div className='text-lg font-semibold text-amber-400'>
                      {filteredTrades.length > 0
                        ? Object.entries(
                            filteredTrades.reduce(
                              (acc, t) => {
                                acc[t.politician] = (acc[t.politician] || 0) + 1
                                return acc
                              },
                              {} as Record<string, number>
                            )
                          )
                            .sort((a, b) => b[1] - a[1])[0]?.[0]
                            .split(' ')
                            .slice(-1)[0] || 'N/A'
                        : 'N/A'}
                    </div>
                  </div>
                </div>

                {/* Date Range on Right */}
                <div className='flex items-center gap-2 text-xs text-muted-foreground'>
                  <svg
                    className='h-3.5 w-3.5'
                    fill='none'
                    stroke='currentColor'
                    viewBox='0 0 24 24'
                  >
                    <path
                      strokeLinecap='round'
                      strokeLinejoin='round'
                      strokeWidth={2}
                      d='M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z'
                    />
                  </svg>
                  <span>2012 - 2024</span>
                </div>
              </div>
            </motion.div>

            {/* Filters */}
            <motion.div
              className='mb-6 flex-shrink-0'
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.4, delay: 0.3 }}
            >
              <TradeFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                partyFilter={partyFilter}
                onPartyFilterChange={setPartyFilter}
                tradeTypeFilter={tradeTypeFilter}
                onTradeTypeFilterChange={setTradeTypeFilter}
                assetTypeFilter={assetTypeFilter}
                onAssetTypeFilterChange={setAssetTypeFilter}
              />
            </motion.div>

            {/* Table Container - Scrollable */}
            <div className='flex min-h-0 flex-1 flex-col'>
              {/* Table */}
              <div className='relative flex-1 overflow-hidden'>
                <AnimatePresence initial={false}>
                  <motion.div
                    key={currentPage}
                    className='overflow-auto'
                    initial={{
                      opacity: 0,
                    }}
                    animate={{
                      opacity: 1,
                    }}
                    exit={{
                      opacity: 0,
                    }}
                    transition={{
                      duration: 0.25,
                    }}
                  >
                    {filteredTrades.length === 0 ? (
                      <div className='py-12 text-center text-muted-foreground'>
                        No trades found matching your filters.
                      </div>
                    ) : (
                      <PoliticianTradesTable
                        trades={paginatedTrades}
                        onPoliticianClick={handlePoliticianClick}
                      />
                    )}
                  </motion.div>
                </AnimatePresence>
              </div>

              {/* Pagination */}
              {filteredTrades.length > 0 && totalPages > 1 && (
                <motion.div
                  className='mt-1 flex flex-shrink-0 items-center justify-between border-t border-[#2A2D35] px-2 py-4'
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ duration: 0.4, delay: 0.5 }}
                >
                  <div className='text-sm text-muted-foreground'>
                    Showing {startIndex + 1}-
                    {Math.min(endIndex, filteredTrades.length)} of{' '}
                    {filteredTrades.length} trades
                  </div>

                  <div className='flex items-center gap-2'>
                    <Button
                      variant='outline'
                      size='sm'
                      onClick={() => {
                        setDirection(-1)
                        setCurrentPage((p) => Math.max(1, p - 1))
                      }}
                      disabled={currentPage === 1}
                      className='h-8 w-8 p-0 transition-all duration-200 hover:scale-110 active:scale-95'
                    >
                      <ChevronLeft className='h-4 w-4' />
                    </Button>

                    <div className='flex items-center gap-1'>
                      {/* Fixed 7-button layout: [1] [...] [A] [B] [C] [...] [Last] */}

                      {/* Button 1: Always Page 1 */}
                      <Button
                        variant={currentPage === 1 ? 'default' : 'outline'}
                        size='sm'
                        onClick={() => {
                          setDirection(-1)
                          setCurrentPage(1)
                        }}
                        className={`h-8 w-8 p-0 transition-all duration-200 ${
                          currentPage === 1
                            ? 'scale-110 shadow-lg'
                            : 'hover:scale-110 active:scale-95'
                        }`}
                      >
                        1
                      </Button>

                      {/* Button 2: Ellipsis or Page 2 */}
                      {totalPages > 5 && currentPage > 3 ? (
                        <div className='flex h-8 w-8 items-center justify-center text-muted-foreground'>
                          ...
                        </div>
                      ) : totalPages > 1 ? (
                        <Button
                          variant={currentPage === 2 ? 'default' : 'outline'}
                          size='sm'
                          onClick={() => {
                            setDirection(currentPage < 2 ? 1 : -1)
                            setCurrentPage(2)
                          }}
                          className={`h-8 w-8 p-0 transition-all duration-200 ${
                            currentPage === 2
                              ? 'scale-110 shadow-lg'
                              : 'hover:scale-110 active:scale-95'
                          }`}
                        >
                          2
                        </Button>
                      ) : (
                        <div className='w-8' />
                      )}

                      {/* Button 3: Dynamic middle page */}
                      {(() => {
                        let pageNum = 3
                        if (totalPages > 5 && currentPage > 3) {
                          pageNum = Math.min(currentPage - 1, totalPages - 3)
                        }

                        return totalPages >= 3 ? (
                          <Button
                            variant={
                              currentPage === pageNum ? 'default' : 'outline'
                            }
                            size='sm'
                            onClick={() => {
                              setDirection(currentPage < pageNum ? 1 : -1)
                              setCurrentPage(pageNum)
                            }}
                            className={`h-8 w-8 p-0 transition-all duration-200 ${
                              currentPage === pageNum
                                ? 'scale-110 shadow-lg'
                                : 'hover:scale-110 active:scale-95'
                            }`}
                          >
                            {pageNum}
                          </Button>
                        ) : (
                          <div className='w-8' />
                        )
                      })()}

                      {/* Button 4: Center/Current page area */}
                      {(() => {
                        let pageNum = 4
                        if (totalPages > 5 && currentPage > 4) {
                          pageNum = Math.min(currentPage, totalPages - 2)
                        }

                        return totalPages >= 4 ? (
                          <Button
                            variant={
                              currentPage === pageNum ? 'default' : 'outline'
                            }
                            size='sm'
                            onClick={() => {
                              setDirection(currentPage < pageNum ? 1 : -1)
                              setCurrentPage(pageNum)
                            }}
                            className={`h-8 w-8 p-0 transition-all duration-200 ${
                              currentPage === pageNum
                                ? 'scale-110 shadow-lg'
                                : 'hover:scale-110 active:scale-95'
                            }`}
                          >
                            {pageNum}
                          </Button>
                        ) : (
                          <div className='w-8' />
                        )
                      })()}

                      {/* Button 5: Dynamic middle page */}
                      {(() => {
                        let pageNum = 5
                        if (totalPages > 5 && currentPage > 4) {
                          pageNum = Math.min(currentPage + 1, totalPages - 1)
                        }

                        return totalPages >= 5 ? (
                          <Button
                            variant={
                              currentPage === pageNum ? 'default' : 'outline'
                            }
                            size='sm'
                            onClick={() => {
                              setDirection(currentPage < pageNum ? 1 : -1)
                              setCurrentPage(pageNum)
                            }}
                            className={`h-8 w-8 p-0 transition-all duration-200 ${
                              currentPage === pageNum
                                ? 'scale-110 shadow-lg'
                                : 'hover:scale-110 active:scale-95'
                            }`}
                          >
                            {pageNum}
                          </Button>
                        ) : (
                          <div className='w-8' />
                        )
                      })()}

                      {/* Button 6: Ellipsis or second-to-last page */}
                      {totalPages > 6 && currentPage < totalPages - 2 ? (
                        <div className='flex h-8 w-8 items-center justify-center text-muted-foreground'>
                          ...
                        </div>
                      ) : totalPages === 6 ? (
                        <Button
                          variant={currentPage === 6 ? 'default' : 'outline'}
                          size='sm'
                          onClick={() => {
                            setDirection(currentPage < 6 ? 1 : -1)
                            setCurrentPage(6)
                          }}
                          className={`h-8 w-8 p-0 transition-all duration-200 ${
                            currentPage === 6
                              ? 'scale-110 shadow-lg'
                              : 'hover:scale-110 active:scale-95'
                          }`}
                        >
                          6
                        </Button>
                      ) : (
                        <div className='w-8' />
                      )}

                      {/* Button 7: Always Last Page */}
                      {totalPages > 1 && (
                        <Button
                          variant={
                            currentPage === totalPages ? 'default' : 'outline'
                          }
                          size='sm'
                          onClick={() => {
                            setDirection(1)
                            setCurrentPage(totalPages)
                          }}
                          className={`h-8 w-8 p-0 transition-all duration-200 ${
                            currentPage === totalPages
                              ? 'scale-110 shadow-lg'
                              : 'hover:scale-110 active:scale-95'
                          }`}
                        >
                          {totalPages}
                        </Button>
                      )}
                    </div>

                    <Button
                      variant='outline'
                      size='sm'
                      onClick={() => {
                        setDirection(1)
                        setCurrentPage((p) => Math.min(totalPages, p + 1))
                      }}
                      disabled={currentPage === totalPages}
                      className='h-8 w-8 p-0 transition-all duration-200 hover:scale-110 active:scale-95'
                    >
                      <ChevronRight className='h-4 w-4' />
                    </Button>
                  </div>
                </motion.div>
              )}
            </div>
          </motion.div>
        </AnimatePresence>
      </Layout.Body>
    </Layout>
  )
}
