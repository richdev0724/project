import { useTopBlockTrades } from '@/hooks/use-top-block-traders'
import { DataTable } from './blocks-table/blocks-table'
import { columns } from './blocks-table/blocks-table-columns'
import Loader from '@/components/loader'

interface TopBlockOrderDataProps {
  symbol: string
  searchTermTrades: string
}

export default function TopBlockOrderData({
  symbol,
  searchTermTrades,
}: TopBlockOrderDataProps) {
  const { topBlockData, isLoading, isError } = useTopBlockTrades(symbol)

  if (isLoading) {
    return <Loader />
  }

  if (isError) {
    return <div>Something went wrong!!</div>
  }

  return (
    <DataTable
      data={topBlockData.trades || []}
      columns={columns}
      searchTerm={searchTermTrades}
    />
  )
}
