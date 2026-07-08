import { TrendingUp, TrendingDown } from 'lucide-react'
import { motion } from 'motion/react'
import { useRef, useEffect, useState, useMemo } from 'react'

interface MarketIndex {
  name: string
  ticker: string
  value: string
  change: number
  changePercent: number
}

const marketIndices: MarketIndex[] = [
  {
    name: 'S&P 500',
    ticker: 'SPX',
    value: '5,751.13',
    change: 42.87,
    changePercent: 0.75,
  },
  {
    name: 'Dow Jones',
    ticker: 'DJIA',
    value: '42,863.86',
    change: -103.08,
    changePercent: -0.24,
  },
  {
    name: 'Nasdaq',
    ticker: 'COMP',
    value: '18,342.94',
    change: 185.33,
    changePercent: 1.02,
  },
  {
    name: 'Russell 2000',
    ticker: 'RUT',
    value: '2,234.56',
    change: 12.45,
    changePercent: 0.56,
  },
  {
    name: 'VIX',
    ticker: 'VIX',
    value: '16.23',
    change: -1.34,
    changePercent: -7.63,
  },
  {
    name: 'Crude Oil',
    ticker: 'CL',
    value: '77.42',
    change: 2.18,
    changePercent: 2.9,
  },
  {
    name: 'Gold',
    ticker: 'GC',
    value: '2,654.30',
    change: -8.2,
    changePercent: -0.31,
  },
  {
    name: '10-Yr Bond',
    ticker: 'TNX',
    value: '4.02',
    change: 0.03,
    changePercent: 0.75,
  },
  {
    name: 'Bitcoin',
    ticker: 'BTC',
    value: '62,450.00',
    change: 1250.3,
    changePercent: 2.04,
  },
  {
    name: 'Ethereum',
    ticker: 'ETH',
    value: '3,421.00',
    change: -45.2,
    changePercent: -1.3,
  },
]

interface TickerTapeProps {
  selectedTickers: string[]
}

export function TickerTape({ selectedTickers }: TickerTapeProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [totalWidth, setTotalWidth] = useState(0)

  // Filter market indices based on selected tickers
  const filteredIndices = useMemo(() => {
    if (selectedTickers.length === 0) {
      return marketIndices // Show all if none selected
    }
    return marketIndices.filter((index) =>
      selectedTickers.includes(index.ticker)
    )
  }, [selectedTickers])

  // Duplicate the filtered array for seamless looping
  const duplicatedIndices = [...filteredIndices, ...filteredIndices]

  useEffect(() => {
    if (containerRef.current) {
      const width = containerRef.current.scrollWidth / 2 // Divide by 2 since we duplicate the array
      setTotalWidth(width)
    }
  }, [])

  return (
    <motion.div
      className='sticky top-[72px] z-10 -mt-px overflow-hidden border-y border-border bg-background'
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, delay: 0.2 }}
    >
      <div className='relative py-3'>
        <motion.div
          ref={containerRef}
          className='flex gap-6'
          animate={{
            x: [0, -totalWidth],
          }}
          transition={{
            duration: 60,
            repeat: Infinity,
            ease: 'linear',
            repeatType: 'loop',
          }}
          style={{
            willChange: 'transform',
          }}
        >
          {duplicatedIndices.map((index, idx) => (
            <div
              key={`ticker-${index.ticker}-${idx}`}
              className='flex flex-shrink-0 items-center gap-6 whitespace-nowrap rounded-md border border-gray-300/20 bg-card/50 px-3 py-1.5 shadow-xl transition-all duration-300 ease-out hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-2xl'
            >
              <div className='flex flex-col pl-1'>
                <span className='text-xs font-semibold text-muted-foreground/70'>
                  {index.name}
                </span>
                <span className='text-sm text-foreground/80'>
                  {index.value}
                </span>
              </div>
              <div
                className={`flex flex-col items-center gap-1 text-xs ${index.change >= 0 ? 'text-green-500' : 'text-red-500'}`}
              >
                <div className='flex items-center gap-1'>
                  {index.change >= 0 ? (
                    <TrendingUp className='h-3 w-3' />
                  ) : (
                    <TrendingDown className='h-3 w-3' />
                  )}
                  <span>
                    {index.change >= 0 ? '+' : '-'}$
                    {Math.abs(index.change).toFixed(2)}
                  </span>
                </div>
                <span className='ml-4'>
                  {index.change >= 0 ? '+' : '-'}
                  {Math.abs(index.changePercent)}%
                </span>
              </div>
            </div>
          ))}
        </motion.div>
      </div>
    </motion.div>
  )
}
