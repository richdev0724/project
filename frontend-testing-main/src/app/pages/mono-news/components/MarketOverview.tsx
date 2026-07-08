import { TrendingUp, TrendingDown } from 'lucide-react'
import { Card } from '@/components/ui/card'

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
    value: '4.02%',
    change: 0.03,
    changePercent: 0.75,
  },
]

export function MarketOverview() {
  return (
    <div className='border-b border-border bg-secondary/30'>
      <div className='container mx-auto px-4 py-6'>
        <h2 className='mb-4'>Market Overview</h2>
        <div className='grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-8'>
          {marketIndices.map((index) => (
            <Card
              key={index.ticker}
              className='cursor-pointer bg-card p-4 transition-colors hover:bg-accent/50'
            >
              <div className='text-sm text-muted-foreground'>{index.name}</div>
              <div className='mt-1 text-foreground'>{index.value}</div>
              <div
                className={`mt-1 flex items-center gap-1 text-sm ${index.change >= 0 ? 'text-green-500' : 'text-red-500'}`}
              >
                {index.change >= 0 ? (
                  <TrendingUp className='h-3 w-3' />
                ) : (
                  <TrendingDown className='h-3 w-3' />
                )}
                <span>
                  {index.change >= 0 ? '+' : ''}
                  {index.changePercent}%
                </span>
              </div>
            </Card>
          ))}
        </div>
      </div>
    </div>
  )
}
