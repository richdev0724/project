import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import { useFlowWithWebSocket } from '@/hooks/use-flow-web-socket'
import { useRealTimeFlow } from '@/hooks/use-real-time-flow'
import { useSound } from '@/hooks/use-sound'
import { cn } from '@/lib/utils'
import {
  isChimeNotificationOnAtom,
  isOptionsTradeLoadingAtom,
} from '@/store/store'
import { useAtom } from 'jotai'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import { DataTable } from './components/flow-data-table'
import { columns } from './components/flow-table-columns'
import FlowTableFilter from './components/flow-table-filter'
import FlowTableHeaderTwo from './components/flow-table-header-two'
import { OptionTrade } from './data/schema'

/**
 * Page to display the Dark Pool data table.
 *
 * This page displays a table of Dark Pool data, with the ability to filter and sort the data.
 * It also uses a WebSocket to listen for new data and update the table in real-time.
 *
 * @returns A JSX element containing the Dark Pool data table and associated filters and sorting options.
 */
const Flow = React.memo(() => {
  const [isOptionsTradeLoadingValue] = useAtom<boolean>(
    isOptionsTradeLoadingAtom
  )
  const [isSoundOff] = useAtom(isChimeNotificationOnAtom)

  const [showSoundIcon, setShowSoundIcon] = useState<boolean>(false)
  const [pageIndex, setPageIndex] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(50)
  const [showFilter, setShowFilter] = useState<boolean>(false)
  const [sortBy, setSortBy] = useState<
    'sip_timestamp' | 'net_premium' | 'size'
  >('sip_timestamp')

  const { playSound } = useSound('../../../assets/sounds/sound.mp3')

  // Use WebSocket for real-time updates
  const {
    flowData: wsFlowData,
    totalCount: wsTotalCount,
    isLoading: wsIsLoading,
    startListeningToOptionsTrade,
    stopListeningToOptionsTrade,
    filterForm,
    handleSubmitFilter,
  } = useFlowWithWebSocket({
    pageSize,
    pageIndex: pageIndex + 1,
    orderBy: sortBy,
  })

  // Use real-time Socket.IO for instant updates
  const {
    flowData: realTimeFlowData,
    totalCount: realTimeTotalCount,
    isLoading: realTimeIsLoading,
    // isConnected,
    startRealTime,
    stopRealTime,
    // connectionStatus
  } = useRealTimeFlow({
    pageSize,
    pageIndex,
    orderBy: sortBy,
    enabled: isOptionsTradeLoadingValue, // Enable real-time when "live" mode is on
  })

  // Use real-time data when available AND no filters are applied, otherwise use WebSocket data
  // Real-time mode doesn't support filters, so we fallback to WebSocket data when filters are active
  const expirationValue = filterForm.watch('expiration')
  const hasActiveFilters =
    !!filterForm.watch('tickers') ||
    !!filterForm.watch('ignoreTickers') ||
    (expirationValue != null &&
      Array.isArray(expirationValue) &&
      expirationValue.length > 0) ||
    filterForm.watch('dteMin') !== null ||
    filterForm.watch('dteMax') !== null ||
    filterForm.watch('minimumPremium') !== null ||
    filterForm.watch('maximumPremium') !== null ||
    filterForm.watch('minimumSize') !== null ||
    filterForm.watch('maximumSize') !== null ||
    filterForm.watch('minimumVolume') !== null ||
    filterForm.watch('maximumVolume') !== null ||
    filterForm.watch('calls') !== null ||
    filterForm.watch('puts') !== null ||
    filterForm.watch('sweeps') !== null ||
    filterForm.watch('floor') !== null ||
    filterForm.watch('cross') !== null ||
    filterForm.watch('bid') !== null ||
    filterForm.watch('mid') !== null ||
    filterForm.watch('ask') !== null ||
    filterForm.watch('noSide') !== null ||
    filterForm.watch('singleLegOnly') !== null ||
    filterForm.watch('volume>Oi') !== null ||
    filterForm.watch('size>Oi') !== null ||
    filterForm.watch('openingTrade') !== null ||
    filterForm.watch('excludeDeepItm') !== null ||
    filterForm.watch('otmOnly') !== null ||
    filterForm.watch('itmOnly') !== null ||
    filterForm.watch('multilegOnly') !== null ||
    filterForm.watch('singlelegOnly') !== null ||
    filterForm.watch('aggregatedTrades') !== null ||
    filterForm.watch('hideExpired') !== null
  const shouldUseRealTime = isOptionsTradeLoadingValue && !hasActiveFilters

  const flowData = shouldUseRealTime ? realTimeFlowData : wsFlowData
  const totalCount = shouldUseRealTime ? realTimeTotalCount : wsTotalCount
  const isLoading = shouldUseRealTime ? realTimeIsLoading : wsIsLoading

  const previousDataLength = useRef<number>(0)

  const handlePageChange = (page: number) => {
    setPageIndex(page)
  }

  // Unified toggle functions that handle both WebSocket and Real-time Socket.IO
  const handleStartRealTime = useCallback(() => {
    startListeningToOptionsTrade()
    startRealTime()
  }, [startListeningToOptionsTrade, startRealTime])

  const handleStopRealTime = useCallback(() => {
    stopListeningToOptionsTrade()
    stopRealTime()
  }, [stopListeningToOptionsTrade, stopRealTime])

  const toggleFilter = () => {
    setShowFilter((prev) => !prev)
  }

  const handleDataUpdate = useCallback(() => {
    if (flowData) {
      const currentLength = flowData.length
      if (
        previousDataLength.current !== 0 &&
        // currentLength > previousDataLength.current &&
        isOptionsTradeLoadingValue &&
        isSoundOff
      ) {
        playSound()
        setShowSoundIcon(true)
        setTimeout(() => {
          setShowSoundIcon(false)
        }, 1000)
      }
      previousDataLength.current = currentLength
    }
  }, [flowData, isOptionsTradeLoadingValue, isSoundOff, playSound])

  useEffect(() => {
    handleDataUpdate()
  }, [handleDataUpdate])

  return (
    <Layout className='overflow-hidden'>
      <Seo title='Flow' />
      <Layout.Header2 sticky>
        <FlowTableHeaderTwo
          handlePageChange={handlePageChange}
          isOptionsTradeLoadingValue={isOptionsTradeLoadingValue}
          totalCount={totalCount}
          showSoundIcon={showSoundIcon}
          isSoundOff={isSoundOff}
          pageIndex={pageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          setSortBy={setSortBy}
          stopListeningToOptionsTrade={handleStopRealTime}
          startListeningToOptionsTrade={handleStartRealTime}
          setPageIndex={setPageIndex}
        >
          <FlowTableFilter
            showFilter={showFilter}
            toggleFilter={toggleFilter}
            form={filterForm}
            handleSubmitFilter={handleSubmitFilter}
            setPageIndex={setPageIndex}
          />
        </FlowTableHeaderTwo>
      </Layout.Header2>
      <Layout.Body
        className={cn(
          'px-0 py-0 transition-all duration-300 md:px-0',
          showFilter ? 'ml-[216px] w-[calc(100%-216px)]' : 'w-full'
        )}
      >
        <DataTable
          data={flowData.map((item: OptionTrade) => item)}
          columns={columns}
          isLoading={isLoading}
        />
      </Layout.Body>
    </Layout>
  )
})
export default Flow
