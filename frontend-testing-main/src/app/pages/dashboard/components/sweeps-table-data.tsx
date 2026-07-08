import { useTopSweepsOrders } from '@/hooks/use-top-order-sweeps'
import { DataTable } from './sweeps-table/sweeps-table'
import { columns } from './sweeps-table/sweeps-table-columns'
import Loader from '@/components/loader'

type SweepsTableDataProps = {
  searchTerm: string
}

export default function SweepsTableData({ searchTerm }: SweepsTableDataProps) {
  const today = new Date().toISOString().split('T')[0]

  const { sweepData, isLoading } = useTopSweepsOrders({
    startDate: '2022-01-01',
    endDate: today,
  })

  if (isLoading) {
    return <Loader />
  }

  return (
    <DataTable
      data={sweepData.sweeps || []}
      columns={columns}
      searchTerm={searchTerm}
    />
  )
}
