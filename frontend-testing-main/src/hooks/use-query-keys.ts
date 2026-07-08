import { useMemo } from 'react'

export const useMarketDashboardQueryKeys = () => {
  const marketPerformanceWidgetKey = (timeFrame: string) => [
    'marketDashboard',
    'marketPerformanceWidget',
    JSON.stringify(timeFrame),
  ]

  const marketMonoMatrixWidgetKey = (timeFrame: string) => [
    'marketDashboard',
    'marketMonoMatrixWidget',
    JSON.stringify(timeFrame),
  ]

  const marketSectorsWidget = () => ['marketDashboard', 'marketSectorWidget']

  const magnificentSevenWidgetKey = (timeFrame: string) => [
    'marketDashboard',
    'magnificentSevenWidget',
    JSON.stringify(timeFrame),
  ]

  const topBullishNetPremiumWidgetKey = () => [
    'marketDashboard',
    'topBullishNetPremiumWidget',
  ]

  const topBearishNetPremiumWidgetKey = () => [
    'marketDashboard',
    'topBearishNetPremiumWidget',
  ]

  const topSweepOrdersWidgetKey = () => [
    'marketDashboard',
    'topSweepOrdersWidget',
  ]

  const topBlockOrdersWidgetKey = () => [
    'marketDashboard',
    'topBlockOrdersWidget',
  ]

  const topContractModalWidgetKey = (sortBy: string) => [
    'marketDashboard',
    'topContractModalWidget',
    JSON.stringify(sortBy),
  ]

  const marketPremiumsWidgetKey = (timeFrame: number, ticker: string) => [
    'marketDashboard',
    'marketPremiumsWidget',
    JSON.stringify(timeFrame),
    JSON.stringify(ticker),
  ]

  return useMemo(
    () => ({
      marketPerformanceWidgetKey,
      marketMonoMatrixWidgetKey,
      magnificentSevenWidgetKey,
      marketSectorsWidget,
      topBullishNetPremiumWidgetKey,
      topBearishNetPremiumWidgetKey,
      topSweepOrdersWidgetKey,
      topBlockOrdersWidgetKey,
      topContractModalWidgetKey,
      marketPremiumsWidgetKey,
    }),
    []
  )
}

export const useTickerOverviewQueryKeys = () => {
  const topContractModalWidgetKey = (sortBy: string) => [
    'tickerOverview',
    'topContractModalWidget',
    JSON.stringify(sortBy),
  ]

  const topSweepOrdersWidgetKey = () => [
    'tickerOverview',
    'topSweepOrdersWidget',
  ]

  const topBlockOrdersWidgetKey = () => [
    'tickerOverview',
    'topBlockOrdersWidget',
  ]

  const marketPremiumsWidgetKey = (timeFrame: number, ticker: string) => [
    'tickerOverview',
    'marketPremiumsWidget',
    JSON.stringify(timeFrame),
    JSON.stringify(ticker),
  ]

  const tickerOverviewWidgetKey = (symbol: string) => {
    return ['tickerOverview', 'tickerOverviewWidget', JSON.stringify(symbol)]
  }

  return useMemo(
    () => ({
      topContractModalWidgetKey,
      topSweepOrdersWidgetKey,
      topBlockOrdersWidgetKey,
      marketPremiumsWidgetKey,
      tickerOverviewWidgetKey,
    }),
    []
  )
}

export const useTickerMetricsQueryKeys = () => {
  const strikePriceMetricsWidgetKey = (ticker: string, expiration: string) => [
    'tickerMetrics',
    'strikePriceMetricsWidget',
    JSON.stringify(ticker),
    JSON.stringify(expiration),
  ]

  const gammaExposureWidgetKey = (ticker: string, expiration: string) => [
    'tickerMetrics',
    'gammaExposureWidget',
    JSON.stringify(ticker),
    JSON.stringify(expiration),
  ]

  return useMemo(
    () => ({
      strikePriceMetricsWidgetKey,
      gammaExposureWidgetKey,
    }),
    []
  )
}
