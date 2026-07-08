import { marketDataAnalysisService } from '@/api'
import { OptionTrade } from '@/app/pages/flow/data/schema'
import {
  // isChimeNotificationOnAtom,
  isOptionsTradeLoadingAtom,
} from '@/store/store'
import { socket } from '@/utilities/socket'
import {
  FlowFilterRequest,
  flowFilterSchema,
} from '@/validations/flow.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAtom } from 'jotai'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import useSocket from './socket'

interface InitialData {
  results: OptionTrade[]
  totalCount: number
}

interface params {
  pageSize: number
  pageIndex: number
  orderBy: string
}

/**
 * Hook to manage and fetch flow data using WebSockets.
 *
 * This hook provides a way to fetch flow data from an API and listen to real-time updates
 * via WebSockets. It manages the loading state, error handling, and provides functions
 * to start and stop listening to the 'optionsTrade' WebSocket event. It also initializes
 * a form for filtering flow data.
 *
 * @param {Object} params - The parameters for initializing the hook.
 * @param {number} params.pageSize - The number of items per page.
 * @param {number} params.pageIndex - The current page index.
 * @param {string} params.orderBy - The field by which to order the results.
 *
 * @returns {Object} An object containing:
 *  - flowData: The current flow data.
 *  - totalCount: The total count of flow data items.
 *  - isLoading: Boolean indicating if the data is currently loading.
 *  - error: The error message, if any.
 *  - startListeningToOptionsTrade: Function to start listening to WebSocket events.
 *  - stopListeningToOptionsTrade: Function to stop listening to WebSocket events.
 *  - form: The form object for managing filter state.
 */

const convertToDesiredData = (data: any, fields: string[]): any => {
  const processedData = { ...data }

  // For ticker filters, we want to keep them as comma-separated strings
  // The backend expects strings, not arrays
  fields.forEach((field: string) => {
    if (processedData[field] && typeof processedData[field] === 'string') {
      // Clean up the string by removing extra spaces and empty entries
      const cleanedString = processedData[field]
        .split(',')
        .map((item: any) => item.trim())
        .filter((item: string) => item.length > 0)
        .join(',')

      processedData[field] = cleanedString || null
    }
  })

  return processedData
}

export const useFlowWithWebSocket = ({
  pageSize,
  pageIndex,
  orderBy,
}: params) => {
  const [isOptionsTradeLoading, setIsOptionsTradeLoading] = useAtom(
    isOptionsTradeLoadingAtom
  )
  // const [isSoundOff] = useAtom(isChimeNotificationOnAtom)
  const { registerHandlers } = useSocket<'optionsTrade', never>()
  const [flowData, setFlowData] = useState<OptionTrade[]>([])
  const [error, setError] = useState<string | null>(null)
  const [filterValues, setFilterValues] = useState<FlowFilterRequest | null>(
    null
  )
  const queryClient = useQueryClient()

  const form = useForm<FlowFilterRequest>({
    resolver: zodResolver(flowFilterSchema),
    defaultValues: {
      tickers: '',
      ignoreTickers: '',
      marketCap: [],
      startDate: '',
      endDate: '',
      expiration: [],
      dteMin: null,
      dteMax: null,
      minimumPremium: null,
      maximumPremium: null,
      minimumSize: null,
      maximumSize: null,
      minimumVolume: null,
      maximumVolume: null,
      calls: null,
      puts: null,
      sweeps: null,
      floor: null,
      cross: null,
      bid: null,
      mid: null,
      ask: null,
      noSide: null,
      singleLegOnly: null,
      'volume>Oi': null,
      'size>Oi': null,
      openingTrade: null,
      excludeDeepItm: null,
      otmOnly: null,
      itmOnly: null,
      multilegOnly: null,
      singlelegOnly: null,
      aggregatedTrades: null,
      hideExpired: null,
    },
    reValidateMode: 'onChange',
  })

  // Fetch initial data from API
  const queryKey = useMemo(
    () => [
      'marketDataAnalysis',
      'flowWithWebSocket',
      pageSize,
      pageIndex,
      orderBy,
      JSON.stringify(filterValues),
    ],
    [pageSize, pageIndex, orderBy, filterValues]
  )

  const {
    data: initialData,
    isLoading,
    refetch,
  } = useQuery<InitialData>({
    queryKey,
    queryFn: async () => {
      try {
        const { data } = await marketDataAnalysisService.getFlowData({
          page: pageIndex,
          pageSize,
          orderBy,
          ...filterValues,
        })
        return data
      } catch (err) {
        setError('Failed to fetch initial data')
        throw err
      }
    },
    enabled: true, // Always enabled, don't let other conditions disable it
    staleTime: 0, // Always consider data stale when filters change
    gcTime: 0, // Don't cache filtered results
    refetchOnWindowFocus: false, // Don't refetch on window focus
  })

  /**
   * Handle Filter Submit to apply filters on option trade data
   */
  const handleSubmitFilter = useCallback(async () => {
    const values = form.getValues()

    const cleanedValues = Object.entries(values).reduce(
      (acc, [key, value]) => {
        if (Array.isArray(value)) {
          if (value.length > 0) {
            acc[key] = value
          }
          return acc
        }

        if (value !== null && value !== '' && value !== undefined) {
          acc[key] = value
        }

        return acc
      },
      {} as Record<string, any>
    ) as FlowFilterRequest

    const finalFilterValues = convertToDesiredData(cleanedValues, [
      'tickers',
      'ignoreTickers',
    ])

    setFilterValues(finalFilterValues)

    // Invalidate and refetch data immediately
    queryClient.invalidateQueries({
      queryKey: ['marketDataAnalysis', 'flowWithWebSocket'],
    })

    // Force a refetch to ensure data updates immediately
    setTimeout(() => {
      refetch()
    }, 100)
  }, [form, refetch, queryClient])

  /**
   * Handle incoming options trade data
   */
  const handleOptionsTradeData = useCallback(
    (data: OptionTrade) => {
      if (!data || typeof data !== 'object') return

      setFlowData((prevData) => {
        const existingIds = new Set(prevData.map((trade) => trade._source.id))

        if (data?._source?.id && !existingIds.has(data._source.id)) {
          const updatedData = [data, ...prevData.slice(0, pageSize - 1)]
          return updatedData
        }
        return prevData
      })
    },
    [pageSize]
  )

  /**
   * Stop listening to the 'optionsTrade' event
   */
  const stopListeningToOptionsTrade = () => {
    setIsOptionsTradeLoading(false)
    socket.off('optionsTrade', handleOptionsTradeData)
  }

  /**
   * Start listening to the 'optionsTrade' event
   */
  const startListeningToOptionsTrade = () => {
    setIsOptionsTradeLoading(true)
    socket.off('optionsTrade', handleOptionsTradeData)
    socket.on('optionsTrade', handleOptionsTradeData)
  }

  /**
   * Set initial data in state and trigger prefetching
   */
  useEffect(() => {
    if (initialData && 'results' in initialData) {
      setFlowData(initialData.results)

      // Prefetching removed for simplification
    }
  }, [initialData, pageIndex, pageSize, filterValues])

  /**
   * Register event handlers
   */
  useEffect(() => {
    if (isOptionsTradeLoading) {
      const cleanup = registerHandlers([
        'optionsTrade',
        handleOptionsTradeData as (data: any) => void,
      ])

      return cleanup
    }
  }, [isOptionsTradeLoading, handleOptionsTradeData])

  return {
    flowData,
    totalCount:
      initialData && 'totalCount' in initialData ? initialData.totalCount : 0,
    isLoading,
    error,
    startListeningToOptionsTrade,
    stopListeningToOptionsTrade,
    filterForm: form,
    handleSubmitFilter,
  }
}
