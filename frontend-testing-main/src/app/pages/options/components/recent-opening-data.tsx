import { useRecentOpening } from '@/hooks/use-recent-opening'
import { DataTable } from './recent-opening-trades/recent-opening-trades-table'
import { columns } from './recent-opening-trades/recent-opening-trades-table-columns'

interface Props {
  ticker: string
}
export default function RecentOpeningData({ ticker }: Props) {
  const { opData: recentOpeningData } = useRecentOpening(ticker)

  return <DataTable data={recentOpeningData.data || []} columns={columns} />
}
