import { useRecentMacro } from '@/hooks/use-recent-macro'
import InFlowsOutFlowsChart from './barcharts/inFlowsOutFlowsChart'
import Loader from '@/components/loader'

export default function MarketInflowOutFlowsData() {
  const { data, isLoading } = useRecentMacro(14)

  if (isLoading) {
    return <Loader />
  }

  return <InFlowsOutFlowsChart data={data || []} />
}
