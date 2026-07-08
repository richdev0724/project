import { apiService } from './api.service'

export interface TimeframeData {
  ticker: string
  timeframe: string
  timestamp: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  vwap: number
}

export interface TimeframeResponse {
  success: boolean
  ticker: string
  timeframe: string
  data: TimeframeData[]
  count: number
}

export interface TimeframeOption {
  id: string
  label: string
  description: string
}

export interface TimeframeOptionsResponse {
  success: boolean
  timeframes: TimeframeOption[]
}

export interface DateRange {
  fromDate: string
  toDate: string
}

export interface DateRangeResponse {
  success: boolean
  timeframe: string
  dateRange: DateRange
}

/**
 * Get available timeframes
 */
export const getTimeframes = async (): Promise<TimeframeOption[]> => {
  const response = await apiService.get<TimeframeOptionsResponse>(
    '/timeframe-data/timeframes'
  )
  return response.timeframes
}

/**
 * Get timeframe data for a specific ticker and timeframe
 */
export const getTimeframeData = async (
  ticker: string,
  timeframe: string,
  limit: number = 1000
): Promise<TimeframeData[]> => {
  const response = await apiService.get<TimeframeResponse>(
    `/timeframe-data/data/${ticker}/${timeframe}`,
    { params: { limit } }
  )
  return response.data
}

/**
 * Fetch and store data for a specific ticker and timeframe
 */
export const fetchTimeframeData = async (
  ticker: string,
  timeframe: string
): Promise<void> => {
  await apiService.post(`/timeframe-data/fetch/${ticker}/${timeframe}`)
}

/**
 * Fetch and store data for all timeframes for a ticker
 */
export const fetchAllTimeframes = async (ticker: string): Promise<void> => {
  await apiService.post(`/timeframe-data/fetch-all/${ticker}`)
}

/**
 * Get date range for a specific timeframe
 */
export const getTimeframeDateRange = async (
  timeframe: string
): Promise<DateRange> => {
  const response = await apiService.get<DateRangeResponse>(
    `/timeframe-data/date-range/${timeframe}`
  )
  return response.dateRange
}

/**
 * Initialize the timeframe data table
 */
export const initializeTimeframeTable = async (): Promise<void> => {
  await apiService.post('/timeframe-data/initialize')
}

/**
 * Start timeframe cron jobs
 */
export const startTimeframeCronJobs = async (): Promise<void> => {
  await apiService.post('/timeframe-cron/start')
}

/**
 * Get timeframe cron job status
 */
export const getTimeframeCronStatus = async (): Promise<{
  dailyUpdate: boolean
  liveChart: boolean
  preMarket: boolean
  afterHours: boolean
}> => {
  const response = await apiService.get('/timeframe-cron/status')
  return response.status
}
