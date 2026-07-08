import React, { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Check, Plus, TrendingUp } from 'lucide-react'
import { Button } from '@/components/custom/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

interface UnifiedSettingsModalProps {
  isOpen: boolean
  onClose: () => void
  // Watchlist props
  watchlist: string[]
  onUpdateWatchlist: (watchlist: string[]) => void
  onSaveWatchlist: () => void
  // Sectors props
  availableSectors: string[]
  selectedSectors: string[]
  onSectorToggle: (sector: string) => void
  onSaveSectors: () => void
  // Ticker tape props
  selectedTickerTapeIndices: string[]
  onTickerTapeToggle: (ticker: string) => void
  onSaveTickerTape: () => void
}

export const UnifiedSettingsModal: React.FC<UnifiedSettingsModalProps> = ({
  isOpen,
  onClose,
  watchlist,
  onUpdateWatchlist,
  onSaveWatchlist,
  availableSectors,
  selectedSectors,
  onSectorToggle,
  onSaveSectors,
  selectedTickerTapeIndices,
  onTickerTapeToggle,
  onSaveTickerTape,
}: UnifiedSettingsModalProps) => {
  const [newTicker, setNewTicker] = useState('')
  const [activeTab, setActiveTab] = useState<
    'watchlist' | 'sectors' | 'tickerTape'
  >('watchlist')
  const [openWatchlist, setOpenWatchlist] = useState(false)
  const [openTickerTape, setOpenTickerTape] = useState(false)

  // Popular tickers for autocomplete
  const popularTickers = [
    'AAPL',
    'MSFT',
    'GOOGL',
    'AMZN',
    'TSLA',
    'META',
    'NVDA',
    'NFLX',
    'AMD',
    'INTC',
    'CRM',
    'ADBE',
    'PYPL',
    'UBER',
    'LYFT',
    'SQ',
    'ROKU',
    'ZM',
    'PTON',
    'DOCU',
    'SPOT',
    'TWTR',
    'SNAP',
    'PINS',
    'SHOP',
    'SQ',
    'ROKU',
    'ZM',
    'PTON',
    'DOCU',
    'SPX',
    'DJIA',
    'COMP',
    'RUT',
    'VIX',
    'CL',
    'GC',
    'TNX',
    'BTC',
    'ETH',
    'QQQ',
    'SPY',
    'IWM',
    'VTI',
    'VOO',
    'ARKK',
    'TQQQ',
    'SQQQ',
    'XLF',
    'XLK',
    'JPM',
    'BAC',
    'WFC',
    'GS',
    'MS',
    'C',
    'AXP',
    'USB',
    'PNC',
    'COF',
    'JNJ',
    'PFE',
    'UNH',
    'ABBV',
    'MRK',
    'TMO',
    'ABT',
    'DHR',
    'BMY',
    'AMGN',
    'KO',
    'PEP',
    'WMT',
    'PG',
    'JNJ',
    'UNH',
    'HD',
    'V',
    'MA',
    'DIS',
  ]

  // Filter tickers based on input
  const filteredTickers = popularTickers.filter(
    (ticker) =>
      ticker.toLowerCase().includes(newTicker.toLowerCase()) &&
      !watchlist.includes(ticker) &&
      !selectedTickerTapeIndices.includes(ticker)
  )

  const handleAddTicker = () => {
    if (
      newTicker.trim() &&
      !watchlist.includes(newTicker.trim().toUpperCase())
    ) {
      onUpdateWatchlist([...watchlist, newTicker.trim().toUpperCase()])
      setNewTicker('')
    }
  }

  const handleAddTickerToTape = () => {
    if (
      newTicker.trim() &&
      !selectedTickerTapeIndices.includes(newTicker.trim().toUpperCase())
    ) {
      onTickerTapeToggle(newTicker.trim().toUpperCase())
      setNewTicker('')
    }
  }

  const handleRemoveTicker = (ticker: string) => {
    onUpdateWatchlist(watchlist.filter((t) => t !== ticker))
  }

  const handleRemoveTickerFromTape = (ticker: string) => {
    onTickerTapeToggle(ticker)
  }

  const handleSaveAll = () => {
    onSaveWatchlist()
    onSaveSectors()
    onSaveTickerTape()
    onClose()
  }

  if (!isOpen) return null

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className='fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm'
        onClick={onClose}
      >
        <motion.div
          initial={{ scale: 0.95, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.95, opacity: 0 }}
          className='max-h-[90vh] w-full max-w-2xl overflow-hidden rounded-lg border bg-background shadow-xl'
          onClick={(e) => e.stopPropagation()}
        >
          {/* Header */}
          <div className='flex items-center justify-between border-b p-6'>
            <h2 className='text-xl font-semibold'>Settings</h2>
            <Button variant='ghost' size='sm' onClick={onClose}>
              <X className='h-4 w-4' />
            </Button>
          </div>

          {/* Tab Navigation */}
          <div className='flex border-b'>
            <button
              className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'watchlist'
                  ? 'border-b-2 border-primary text-primary'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
              onClick={() => setActiveTab('watchlist')}
            >
              <TrendingUp className='mr-2 inline h-4 w-4' />
              Watchlist
            </button>
            <button
              className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'sectors'
                  ? 'border-b-2 border-primary text-primary'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
              onClick={() => setActiveTab('sectors')}
            >
              Sectors
            </button>
            <button
              className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
                activeTab === 'tickerTape'
                  ? 'border-b-2 border-primary text-primary'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
              onClick={() => setActiveTab('tickerTape')}
            >
              Ticker Tape
            </button>
          </div>

          {/* Content */}
          <div className='max-h-[60vh] overflow-y-auto p-6'>
            {/* Watchlist Tab */}
            {activeTab === 'watchlist' && (
              <div>
                <h3 className='mb-3 text-lg font-semibold'>
                  Watchlist Tickers
                </h3>
                <p className='mb-4 text-sm text-muted-foreground'>
                  Add tickers to see relevant news in your watchlist sidebar.
                </p>

                {/* Add Ticker Input */}
                <div className='mb-4 flex gap-2'>
                  <Popover open={openWatchlist} onOpenChange={setOpenWatchlist}>
                    <PopoverTrigger asChild>
                      <div className='flex-1'>
                        <Input
                          value={newTicker}
                          onChange={(e) =>
                            setNewTicker(e.target.value.toUpperCase())
                          }
                          placeholder='Enter ticker symbol (e.g., AAPL)'
                          className='w-full'
                          onKeyPress={(e) =>
                            e.key === 'Enter' && handleAddTicker()
                          }
                        />
                      </div>
                    </PopoverTrigger>
                    <PopoverContent className='w-[300px] p-0' align='start'>
                      <Command>
                        <CommandInput placeholder='Search tickers...' />
                        <CommandList>
                          <CommandEmpty>No tickers found.</CommandEmpty>
                          <CommandGroup>
                            {filteredTickers.slice(0, 10).map((ticker) => (
                              <CommandItem
                                key={ticker}
                                value={ticker}
                                onSelect={() => {
                                  setNewTicker(ticker)
                                  setOpenWatchlist(false)
                                }}
                              >
                                <span className='font-mono'>{ticker}</span>
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        </CommandList>
                      </Command>
                    </PopoverContent>
                  </Popover>
                  <Button
                    onClick={handleAddTicker}
                    disabled={!newTicker.trim()}
                  >
                    <Plus className='h-4 w-4' />
                  </Button>
                </div>

                {/* Current Watchlist */}
                <div className='space-y-2'>
                  <h4 className='text-sm font-medium'>
                    Current Watchlist ({watchlist.length})
                  </h4>
                  <div className='flex flex-wrap gap-2'>
                    {watchlist.map((ticker) => (
                      <Badge
                        key={ticker}
                        variant='secondary'
                        className='flex items-center gap-1 px-2 py-1'
                      >
                        <span className='font-mono text-xs'>{ticker}</span>
                        <Button
                          variant='ghost'
                          size='sm'
                          className='h-4 w-4 p-0 hover:bg-destructive hover:text-destructive-foreground'
                          onClick={() => handleRemoveTicker(ticker)}
                        >
                          <X className='h-3 w-3' />
                        </Button>
                      </Badge>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Sectors Tab */}
            {activeTab === 'sectors' && (
              <div>
                <h3 className='mb-3 text-lg font-semibold'>News Sectors</h3>
                <p className='mb-4 text-sm text-muted-foreground'>
                  Select which sectors you want to see in your news feed.
                </p>

                <div className='space-y-2'>
                  {availableSectors.map((sector) => (
                    <div
                      key={sector}
                      className='flex items-center justify-between rounded-lg border p-3'
                    >
                      <span className='text-sm'>{sector}</span>
                      <Button
                        variant={
                          selectedSectors.includes(sector)
                            ? 'default'
                            : 'outline'
                        }
                        size='sm'
                        onClick={() => onSectorToggle(sector)}
                      >
                        {selectedSectors.includes(sector) ? (
                          <Check className='h-4 w-4' />
                        ) : (
                          <Plus className='h-4 w-4' />
                        )}
                      </Button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Ticker Tape Tab */}
            {activeTab === 'tickerTape' && (
              <div>
                <h3 className='mb-3 text-lg font-semibold'>Ticker Tape</h3>
                <p className='mb-4 text-sm text-muted-foreground'>
                  Add any tickers to display on the ticker tape.
                </p>

                {/* Add Ticker Input */}
                <div className='mb-4 flex gap-2'>
                  <Popover
                    open={openTickerTape}
                    onOpenChange={setOpenTickerTape}
                  >
                    <PopoverTrigger asChild>
                      <div className='flex-1'>
                        <Input
                          value={newTicker}
                          onChange={(e) =>
                            setNewTicker(e.target.value.toUpperCase())
                          }
                          placeholder='Enter ticker symbol (e.g., AAPL)'
                          className='w-full'
                          onKeyPress={(e) =>
                            e.key === 'Enter' && handleAddTickerToTape()
                          }
                        />
                      </div>
                    </PopoverTrigger>
                    <PopoverContent className='w-[300px] p-0' align='start'>
                      <Command>
                        <CommandInput placeholder='Search tickers...' />
                        <CommandList>
                          <CommandEmpty>No tickers found.</CommandEmpty>
                          <CommandGroup>
                            {filteredTickers.slice(0, 10).map((ticker) => (
                              <CommandItem
                                key={ticker}
                                value={ticker}
                                onSelect={() => {
                                  setNewTicker(ticker)
                                  setOpenTickerTape(false)
                                }}
                              >
                                <span className='font-mono'>{ticker}</span>
                              </CommandItem>
                            ))}
                          </CommandGroup>
                        </CommandList>
                      </Command>
                    </PopoverContent>
                  </Popover>
                  <Button
                    onClick={handleAddTickerToTape}
                    disabled={!newTicker.trim()}
                  >
                    <Plus className='h-4 w-4' />
                  </Button>
                </div>

                {/* Current Ticker Tape */}
                <div className='space-y-2'>
                  <h4 className='text-sm font-medium'>
                    Ticker Tape ({selectedTickerTapeIndices.length})
                  </h4>
                  <div className='flex flex-wrap gap-2'>
                    {selectedTickerTapeIndices.map((ticker) => (
                      <Badge
                        key={ticker}
                        variant='secondary'
                        className='flex items-center gap-1 px-2 py-1'
                      >
                        <span className='font-mono text-xs'>{ticker}</span>
                        <Button
                          variant='ghost'
                          size='sm'
                          className='h-4 w-4 p-0 hover:bg-destructive hover:text-destructive-foreground'
                          onClick={() => handleRemoveTickerFromTape(ticker)}
                        >
                          <X className='h-3 w-3' />
                        </Button>
                      </Badge>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className='flex justify-end gap-2 border-t bg-muted/50 p-6'>
            <Button variant='outline' onClick={onClose}>
              Cancel
            </Button>
            <Button onClick={handleSaveAll}>Save All Settings</Button>
          </div>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  )
}
