import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import HeaderFilter from './components/header-filter'
import TopIssuersTable from './components/top-issuers-table'
import TopPerformingPoliticianTable from './components/top-performing-politician'
import { DataTable } from './components/main-data-table/main-table'
import { columns } from './components/main-data-table/main-table-columns'
import { tableData } from './data/data'

export default function PoliticianTrades(): JSX.Element {
  return (
    <Layout>
      <Seo title={`Politician Trades | MarketMono`} />
      <Layout.Header2 sticky className='flex h-16 justify-between'>
        <HeaderFilter />
        <div className='flex items-center'>
          <div className='flex items-center gap-2 rounded-sm bg-gradient-to-t from-serverStatusPrimary to-serverStatusPrimaryTransparent px-3 py-1 text-sm'>
            <span className='font-light'>server status:</span>
            <span className='font-medium text-[#28BF94]'>online</span>
          </div>
        </div>
      </Layout.Header2>
      <Layout.Body className='py-0 md:px-0'>
        <div className='flex'>
          <div className='flex w-[58%]'></div>
          <TopIssuersTable />
          <TopPerformingPoliticianTable />
        </div>
        <DataTable data={tableData} columns={columns} isLoading={false} />
      </Layout.Body>
    </Layout>
  )
}
