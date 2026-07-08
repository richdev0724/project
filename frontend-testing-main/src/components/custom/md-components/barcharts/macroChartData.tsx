import { useRecentMacro } from '@/hooks/use-recent-macro'
import MacroChart from './macroChart'
import Loader from '@/components/loader'

export default function MacroChartData() {
  const { data, isLoading } = useRecentMacro(14)

  if (isLoading) {
    return <Loader />
  }
  return <MacroChart data={data || []} />
}
