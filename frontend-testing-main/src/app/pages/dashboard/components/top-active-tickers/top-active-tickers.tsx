import { useMarketActives } from '@/hooks/use-market-actives'
import TopActiveTickerCard from './top-active-ticker-card'

export interface ActiveTicker {
  ticker: string
  companyName: string
  change: number
  price: number
  changesPercentage: number
}

export default function TopActiveTickers() {
  const { marketActives } = useMarketActives()

  return (
    <div className='grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5'>
      {marketActives &&
        marketActives
          .filter((active: { price: number }) => active.price > 5)
          .slice(0, 10)
          .map((item: ActiveTicker, index: number) => (
            <TopActiveTickerCard key={index} item={item} />
          ))}
    </div>
  )
}
