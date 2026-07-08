import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import moment from 'moment-timezone'
import ChartLayout from './components/chart-layout'
import FooterLinksEmailSection from './components/footer/footer-links-email-section'
import FooterTextSection from './components/footer/footer-text-section'
import GammaExposure from './components/gamma-exposure/gamma-exposure'
import MagnificentSevenChart from './components/magnificent-seven-chart'
import MarketPerformanceChart from './components/market-performance-chart'
import MarketPremiums from './components/market-premiums'
import MonoMatrixChart from './components/mono-matrix-chart'
import NewsSection from './components/news/news'
import SectorPerformance from './components/sector-performance/sector-performance'
import TopBearishNetPremium from './components/top-bearish-net-premium/top-bearish-net-premium'
import TopBlockOrders from './components/top-block-orders/top-block-orders'
import TopBullishNetPremium from './components/top-bullish-net-premium/top-bullish-net-premium'
import TopContracts from './components/top-contracts/top-contracts'
import TopDarkPool from './components/top-dark-pool/top-dark-pool'
import TopLitFlow from './components/top-lit-flow/top-lit-flow'
import TopSweepsOrders from './components/top-sweeps-orders/top-sweeps-orders'
import UpcomingEvents from './components/upcoming-events/upcoming-events'

/**
 * The Market Dashboard page.
 *
 * This page displays a collection of charts and tables summarizing market data.
 *
 * The page is divided into sections:
 * - The top header shows the current date and server status.
 * - The main body section contains the charts and tables.
 * - The footer section contains links, email subscription, and text information.
 *
 * The main body section is divided into rows, each containing one or more charts
 * or tables. The charts and tables are arranged in a responsive grid, so that
 * they adapt to different screen sizes.
 *
 * The charts and tables are:
 * - Market Performance Chart: a line chart showing the performance of the
 *   overall market.
 * - Mono Matrix Chart: a heatmap showing the correlation between different
 *   sectors.
 * - Top Contracts: a table showing the top contracts by trading volume.
 * - Top Dark Pool: a table showing the top dark pool orders.
 * - Upcoming Events: a table showing upcoming events (e.g. earnings reports).
 * - Top Bullish Net Premium: a table showing the top bullish net premium
 *   contracts.
 * - Top Bearish Net Premium: a table showing the top bearish net premium
 *   contracts.
 * - Sector Performance: a bar chart showing the performance of different
 *   sectors.
 * - Magnificent Seven: a table showing the top seven contracts by trading
 *   volume.
 * - Market Premiums: a table showing the premiums of different contracts.
 * - Top Sweeps Orders: a table showing the top sweep orders.
 * - Top Block Orders: a table showing the top block orders.
 * - Top Lit Flow: a table showing the top lit flow orders.
 * - Gamma Exposure: a bar chart showing the gamma exposure of different
 *   contracts.
 * - News Section: a section showing news articles.
 */
export default function Dashboard() {
  return (
    <Layout>
      <Seo title='Market Dashboard' />
      {/* Main Body */}
      <Layout.Body className='px-10 py-5 md:px-10'>
        <div className='flex items-center justify-between'>
          <h3 className='text-xs font-light italic text-[#FFFFFF]'>
            {moment().tz('America/New_York').format('dddd')},{' '}
            {moment().tz('America/New_York').format('MMMM Do YYYY')}
          </h3>
          <div
            className='flex items-center gap-2 rounded-sm border border-green-500/50 bg-gradient-to-t from-serverStatusPrimary to-serverStatusPrimaryTransparent px-3 py-1 text-sm shadow-lg shadow-green-500/20'
            style={{ animation: 'borderGlow 6s ease-in-out infinite' }}
          >
            <span className='font-light'>server status:</span>
            <span className='font-medium text-green-400'>online</span>
          </div>
        </div>
        <div className='mt-7 space-y-10'>
          <div className='grid grid-cols-2 gap-10'>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <MarketPerformanceChart />
            </ChartLayout>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <MonoMatrixChart classes='h-[404px]'>
                <h1 className='text-start text-sm'>
                  <span className='px-4 font-normal'>Mono Matrix</span>
                </h1>
              </MonoMatrixChart>
            </ChartLayout>
          </div>
          <div className='grid grid-cols-3 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopContracts />
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopDarkPool />
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <UpcomingEvents />
            </ChartLayout>
          </div>
          <div className='grid grid-cols-4 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopBullishNetPremium />
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopBearishNetPremium />
            </ChartLayout>
            <ChartLayout
              className='col-span-2'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <SectorPerformance />
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
              <MagnificentSevenChart />
            </ChartLayout>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <MarketPremiums />
            </ChartLayout>
          </div>
          <div className='grid grid-cols-3 gap-10'>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopBlockOrders />
            </ChartLayout>
            <ChartLayout
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopSweepsOrders />
            </ChartLayout>
            <ChartLayout
              className='[&>div]:pl-2 [&>div]:pr-9'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-square-bg.png'
              honeycombSize='80% 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <TopLitFlow />
            </ChartLayout>
          </div>
          <div className='grid grid-cols-2 gap-10'>
            <ChartLayout
              className='min-h-[404px] [&>div]:px-2'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <GammaExposure />
            </ChartLayout>
            <ChartLayout
              className='min-h-[404px]'
              background='linear-gradient(to bottom, rgba(22, 31, 38, 0.8) 10%, rgba(40, 42, 53, 0.6) 50%, rgba(22, 31, 38, 0.8) 90%)'
              honeycombPattern='/assets/images/chart/honeycomb-wide.png'
              honeycombSize='auto 80%'
              shadow='0 8px 32px rgba(0, 0, 0, 0.3)'
            >
              <NewsSection />
            </ChartLayout>
          </div>
        </div>
        <div className='mt-60'>
          <FooterLinksEmailSection />
          <FooterTextSection />
        </div>
      </Layout.Body>
    </Layout>
  )
}
