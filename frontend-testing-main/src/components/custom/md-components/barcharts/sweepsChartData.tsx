import { useRecentMacro } from '@/hooks/use-recent-macro'
import SweepsChart from './sweepsChart'

export default function SweepsChartData() {
  const { data } = useRecentMacro(14)

  return <SweepsChart data={data || []} />
}
