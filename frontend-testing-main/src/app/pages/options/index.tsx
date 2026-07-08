import { Layout } from '@/components/custom/layout'
import { useNavigate, useParams } from 'react-router-dom'
import { Component, ReactNode } from 'react'

import Seo from '@/components/seo'

import ChartLayout from '../dashboard/components/chart-layout'
import TickerDarkPool from './components/ticker-dark-pool/ticker-dark-pool'
import TickerEarningsEPS from './components/ticker-earnings-eps/ticker-earnings-eps'
import TickerGammaExposure from './components/ticker-gamma-exposure'
import TickerLitLevels from './components/ticker-lit-levels/ticker-lit-levels'
import TickerChartV2 from './components/ticker-main-chart/ticker-chart-v2'
import TickerMarketPremiums from './components/ticker-market-premiums'
import TickerMatrixChart from './components/ticker-matrix'
import TickerTopBlockOrders from './components/ticker-top-block-orders/ticker-top-block-orders'
import TickerTopContracts from './components/ticker-top-contracts/ticker-top-contracts'
import TickerTopSweepsOrders from './components/ticker-top-sweep-orders/ticker-top-sweep-orders'

import TickerEarningsAudioTab from './components/ticker-earnings-eps/ticker-earning-audio-tab'
import TickerStrikePrice from './components/ticker-strike-price/ticker-strike-price'
import TickerNews from './components/ticker-news/ticker-news'
import TickerInsiderTrades from './components/ticker-insider-trades/ticker-insider-trades'
import TickerAnalystRatings from './components/ticker-analyst-ratings/ticker-analyst-ratings'
import TickerRightTab from './components/ticker-greed-meter'
import { ChangeEvent, FormEvent, KeyboardEvent, useState } from 'react'
import { Input } from '@/components/ui/input'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

class TickerErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error }
  }

  componentDidCatch() {
    // Ticker component error
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className='flex min-h-[400px] items-center justify-center bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)] p-4'>
          <p className='text-gray-400'>
            Something went wrong loading the ticker data. Please try again.
          </p>
        </div>
      )
    }

    return this.props.children
  }
}

/**
 * Page component for displaying options-related data for a given ticker symbol.
 *
 * It displays a variety of charts and tables, including:
 * - A main chart with the ticker's price history
 * - A greed meter to display the ticker's sentiment
 * - A matrix chart to display the ticker's IV surface
 * - A market premiums chart to display the ticker's market premiums
 * - A dark pool chart to display the ticker's dark pool activity
 * - A lit levels chart to display the ticker's lit levels
 * - A top contracts table to display the ticker's top contracts
 * - A top sweeps orders table to display the ticker's top sweeps orders
 * - A top block orders table to display the ticker's top block orders
 * - An earnings EPS chart to display the ticker's earnings history
 * - An earnings audio tab to display the ticker's earnings audio
 * - A gamma exposure chart to display the ticker's gamma exposure
 * - A strike price chart to display the ticker's strike price history
 *
 * It also includes a server status indicator at the top of the page.
 * @returns {JSX.Element} The page component
 */
export default function Options(): JSX.Element {
  const { symbol } = useParams()
  const navigate = useNavigate()
  const [tagLine, setTagLine] = useState('')
  const [searchValue, setSearchValue] = useState(symbol || '')

  // Handle search input change
  const handleSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    // Convert to uppercase as user types
    setSearchValue(e.target.value.toUpperCase())
  }

  // Handle form submission
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (searchValue.trim()) {
      navigate(`/ticker-overview/${searchValue.trim()}`) // Already uppercase from handleSearchChange
    }
  }

  // Handle the Enter key press
  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSubmit(e)
    }
  }
  return (
    <Layout>
      <Seo title={`${symbol} Overview`} />
      <Layout.Body className='px-10 py-5 md:px-10'>
        <div className='flex items-center justify-between'>
          <div>
            <form onSubmit={handleSubmit}>
              <Input
                placeholder={'SPY'}
                value={searchValue}
                onChange={handleSearchChange}
                onKeyDown={handleKeyDown}
                style={{ textTransform: 'uppercase' }}
                className='h-9 rounded-sm border-inputBorderColor bg-inputFieldBackground pl-8 font-medium placeholder:text-placeHolderColor focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50'
              />
            </form>
          </div>
          <div
            className='flex items-center gap-2 rounded-sm border border-green-500/50 bg-gradient-to-t from-serverStatusPrimary to-serverStatusPrimaryTransparent px-3 py-1 text-sm shadow-lg shadow-green-500/20'
            style={{ animation: 'borderGlow 6s ease-in-out infinite' }}
          >
            <span className='font-light'>server status:</span>
            <span className='font-medium text-green-400'>online</span>
          </div>
        </div>
        <div className='mt-7 space-y-10 pb-6'>
          <div className='grid grid-cols-3 gap-10'>
            <ChartLayout
              className='col-span-2 h-[520px] [&>div]:py-1'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerChartV2 key={symbol} tagLine={tagLine} />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              className='h-[520px]'
              overflow='overflow-y-auto'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='100% auto'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerRightTab setTagLine={setTagLine} />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-2 gap-10'>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerMatrixChart classes='h-[404px]'>
                  <h1 className='text-start text-sm'>
                    <span className='px-4 font-normal'>{symbol} Matrix</span>
                  </h1>
                </TickerMatrixChart>
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerMarketPremiums />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-3 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerDarkPool />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerLitLevels />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerTopContracts />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-3 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerTopSweepsOrders />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerTopBlockOrders />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              className='[&>div]:px-0 [&>div]:pb-0'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerEarningsEPS />
                <TickerEarningsAudioTab />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-2 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerGammaExposure />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerStrikePrice />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-1 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerInsiderTrades />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-2 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerNews />
              </TickerErrorBoundary>
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TickerErrorBoundary>
                <TickerAnalystRatings />
              </TickerErrorBoundary>
            </ChartLayout>
          </div>
          <div className='flex justify-center pt-24'>
            <p className='text-sm text-gray-400'>© Market Mono, LLC</p>
          </div>
        </div>
      </Layout.Body>
    </Layout>
  )
}
