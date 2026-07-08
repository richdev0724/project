import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import { useWhaleTrades } from '@/hooks/use-whale-trades'
import {
  isDarkPoolChimeNotificationOnAtom,
  isDarkPoolTradeLoadingAtom,
} from '@/store/store'
import { useAtom } from 'jotai'
import { useState } from 'react'
import FlowTableHeaderTwo from '../flow/components/flow-table-header-two'
import WhaleFlowFilter from './components/whale-flow-filter'
import { cn } from '@/lib/utils'
import { DataTable } from './components/whale-flow-data-table'
import { columns } from './components/whale-flow-columns'

export default function WhaleFlow() {
  const [isDarkPoolTradeLoadingValue] = useAtom<boolean>(
    isDarkPoolTradeLoadingAtom
  )
  const [isSoundOff] = useAtom(isDarkPoolChimeNotificationOnAtom)
  const [showSoundIcon] = useState<boolean>(false)
  const [pageIndex, setPageIndex] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(50)
  const [showFilter, setShowFilter] = useState<boolean>(false)
  const [activeFilters, setActiveFilters] = useState({
    minPremium: 10000,
    maxPremium: null as number | null,
    minSize: null as number | null,
    maxSize: null as number | null,
    minPrice: null as number | null,
    maxPrice: null as number | null,
    session: null as string | null,
    ticker: null as string | null,
    sortBy: 'timestamp' as string | null, // Default to timestamp for regular trades
  })

  const {
    data: whaleTradesData,
    totalCount,
    isLoading,
    isStreaming,
    startStreaming,
    stopStreaming,
  } = useWhaleTrades({
    page: pageIndex + 1,
    pageSize,
    minPremium: activeFilters.minPremium,
    maxPremium: activeFilters.maxPremium,
    minSize: activeFilters.minSize || undefined,
    maxSize: activeFilters.maxSize || undefined,
    minPrice: activeFilters.minPrice || undefined,
    maxPrice: activeFilters.maxPrice || undefined,
    session: activeFilters.session || undefined,
    ticker: activeFilters.ticker || undefined,
    sortBy: activeFilters.sortBy || undefined,
  })

  const handlePageChange = (page: number) => {
    setPageIndex(page)
  }
  const toggleFilter = () => {
    setShowFilter((prev) => !prev)
  }

  const handleFilterChange = (filters: any) => {
    setActiveFilters(filters)
    setPageIndex(0) // Reset to first page when filters change
  }

  return (
    <Layout>
      <Seo title='Flow' />
      <Layout.Header2 sticky>
        <FlowTableHeaderTwo
          handlePageChange={handlePageChange}
          isOptionsTradeLoadingValue={
            isStreaming || isDarkPoolTradeLoadingValue
          }
          totalCount={totalCount}
          showSoundIcon={showSoundIcon}
          isSoundOff={isSoundOff}
          pageIndex={pageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          setSortBy={() => {}}
          stopListeningToOptionsTrade={stopStreaming}
          startListeningToOptionsTrade={startStreaming}
          setPageIndex={setPageIndex}
        >
          <WhaleFlowFilter
            showFilter={showFilter}
            toggleFilter={toggleFilter}
            onFilterChange={handleFilterChange}
          />
        </FlowTableHeaderTwo>
      </Layout.Header2>
      <Layout.Body
        className={cn(
          'px-0 py-0 transition-all duration-300 md:px-0',
          showFilter ? 'ml-[200px] w-[calc(100%-200px)]' : 'w-full'
        )}
      >
        <div className='flex min-h-0 flex-1 overflow-auto'>
          <div className='min-h-0 flex-1 overflow-auto'>
            <div>
              <DataTable
                data={whaleTradesData}
                columns={columns}
                isLoading={isLoading}
              />
            </div>
          </div>
        </div>
      </Layout.Body>
    </Layout>
  )
}
