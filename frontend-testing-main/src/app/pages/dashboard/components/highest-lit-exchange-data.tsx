import LitExchangeChart from '@/components/custom/md-components/barcharts/litExchange'
import Loader from '@/components/loader'
import { useLitPoolTopActivity } from '@/hooks/use-lit-pool-top-activity'

export default function HighestLitExchangeData() {
  const { litPoolTopActivity, isLoading, isError } = useLitPoolTopActivity()

  if (isLoading) {
    return <Loader />
  }

  if (isError) {
    return <div>Error</div>
  }

  return <LitExchangeChart tickers={litPoolTopActivity || []} />
}
