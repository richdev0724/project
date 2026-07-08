import Loader from '@/components/loader'
import { useOptionLiquiditySnapshot } from '@/hooks/use-option-liquidity-snapshot'
import dayjs from 'dayjs'
import OpeningPremiumsChart from './charts/opening-premiums-chart'

interface Props {
  ticker: string
}

export default function OpeningPremiumsData({ ticker }: Props) {
  const { data, isLoading } = useOptionLiquiditySnapshot({
    symbol: ticker,
    startDate: dayjs().subtract(3, 'week').format('YYYY-MM-DD'),
  })

  if (isLoading) {
    return <Loader />
  }

  return <OpeningPremiumsChart data={data || []} />
}
