import { useTopDarkPoolBlocks } from '@/hooks/use-top-dark-pool-blocks'
import { DataTable } from './blocks-table/blocks-table'
import { columns } from './blocks-table/blocks-table-columns'
import Loader from '@/components/loader'

type SweepsTableDataProps = {
  searchTerm: string
}

export default function BlockTableData({ searchTerm }: SweepsTableDataProps) {
  const today = new Date().toISOString().split('T')[0]

  const { blockData, isLoading } = useTopDarkPoolBlocks({
    startDate: '2024-01-01',
    endDate: today,
  })

  if (isLoading) {
    return <Loader />
  }

  return (
    <DataTable
      data={blockData.blocks || []}
      columns={columns}
      searchTerm={searchTerm}
    />
  )
}
