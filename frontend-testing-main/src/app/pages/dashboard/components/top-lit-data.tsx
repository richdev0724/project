import { LitPoolChart } from '@/components/custom/md-components/barcharts/topLitChart'
import Loader from '@/components/loader'
import { useLiquiditySignalsTopActivity } from '@/hooks/use-liquidity-signals-top-activity'

export default function TopLitData() {
  const { liquiditySignalsTopActivity, isLoading, isError } =
    useLiquiditySignalsTopActivity()

  if (isLoading) {
    return <Loader />
  }

  if (isError) {
    return <div>Error</div>
  }

  return <LitPoolChart tickers={liquiditySignalsTopActivity || []} />
}
