import { useOptionLiquiditySnapshot } from '@/hooks/use-option-liquidity-snapshot'
import dayjs from 'dayjs'
import DailyPremsChart from './charts/daily-prems-chart'
import Loader from '@/components/loader'

interface Props {
  ticker: string
}

export default function DailyPremsData({ ticker }: Props) {
  const { data, isLoading } = useOptionLiquiditySnapshot({
    symbol: ticker,
    startDate: dayjs().subtract(3, 'week').format('YYYY-MM-DD'),
  })

  if (isLoading) {
    return <Loader />
  }

  return <DailyPremsChart data={data || []} />
}
