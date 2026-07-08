import { useParams } from 'react-router-dom'
import GreedMeter from './greed-meter'
import { ProgressBar } from './custom-progress'
import { useEffect, useState } from 'react'
import { tickerOverviewService } from '@/api/ticker-data.service'
import Loader from '@/components/loader'
import { useTickerSnapshot } from '@/hooks/use-ticker-snapshot'
import { useTickerPerformance } from '@/hooks/use-ticker-performance'
import { useCompanyProfile } from '@/hooks/use-company-profile'
import { useKeyMetrics } from '@/hooks/use-key-metrics'
import { usePCR } from '@/hooks/use-pcr'

interface Props {
  setTagLine: (tagLine: string) => void
}

interface TickerDetails {
  ticker: string
  name: string
  description: string
  logo_url: string | null
  icon_url: string | null
  total_employees: number
  ceo: string | null
  address: string | null
  market_cap: number
  is_etf: boolean
  sic_description: string | null
  homepage_url: string | null
}

const LoadingState = () => (
  <div className='flex min-h-[400px] items-center justify-center bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)] p-4'>
    <Loader />
  </div>
)

const ErrorState = ({ message }: { message: string }) => (
  <div className='flex min-h-[400px] items-center justify-center bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)] p-4'>
    <p className='text-gray-400'>{message}</p>
  </div>
)

const PriceDisplay = ({
  price,
  change,
  percentageChange,
}: {
  price: number
  change: number
  percentageChange: number
}) => (
  <div>
    <span className='text-[18px] font-bold'>
      {price}
      <sub className='relative left-[2px] top-0 text-xs font-light text-[#F5F7F7]'>
        USD
      </sub>
    </span>
    <span
      className={`ml-2 text-xs ${change > 0 ? 'text-[#28BF94]' : 'text-[#FF6B6B]'}`}
    >
      {change > 0 ? `+${change}` : change}
    </span>
    <span
      className={`ml-2 text-xs ${percentageChange > 0 ? 'text-[#28BF94]' : 'text-[#FF6B6B]'}`}
    >
      {percentageChange > 0 ? `+${percentageChange}%` : `${percentageChange}%`}
    </span>
  </div>
)

const PerformanceBox = ({
  period,
  value,
}: {
  period: string
  value: number
}) => (
  <div
    className={`flex w-[calc(33.33%_-_4px)] flex-col items-center rounded-[3px] ${
      value < 0 ? 'bg-[#CD4B3866]' : 'bg-[#28BF9466]'
    } p-1.5 shadow-[0px_4px_4px_0px_#00000040]`}
  >
    <p
      className={`card-text-shadow text-[14px] font-bold leading-[18px] ${value < 0 ? 'text-[#F12617]' : 'text-[#28BF94]'}`}
    >
      {value.toFixed(2)}%
    </p>
    <p className='text-[10px]'>{period}</p>
  </div>
)

const TickerRightTab: React.FC<Props> = ({ setTagLine }) => {
  const { symbol = '' } = useParams()
  const [tickerDetails, setTickerDetails] = useState<TickerDetails | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Get real-time price data
  const {
    data: snapshotData,
    isLoading: isSnapshotLoading,
    isError: isSnapshotError,
  } = useTickerSnapshot(symbol)

  // Get performance data
  const { data: performanceData, isLoading: isPerformanceLoading } =
    useTickerPerformance(symbol)

  // Get FMP company profile and key metrics
  const { profile: companyProfile, error: profileError } = useCompanyProfile(
    symbol || ''
  )
  const { metrics: keyMetrics, error: metricsError } = useKeyMetrics(
    symbol || ''
  )

  // Get PCR data
  const { pcr, isLoading: isPCRLoading } = usePCR(symbol || '')

  // Function to get sentiment label based on PCR value
  const getSentimentLabel = (
    pcrValue: number
  ): { label: string; color: string } => {
    if (pcrValue < 0.6) {
      return { label: 'Excessive\nBullishness', color: 'text-[#28BF94]' }
    } else if (pcrValue >= 0.6 && pcrValue < 0.7) {
      return { label: 'Moderately\nBullish', color: 'text-[#28BF94]' }
    } else if (pcrValue >= 0.7 && pcrValue < 1.0) {
      return { label: 'Neutral', color: 'text-[#F5F7F7]' }
    } else if (pcrValue >= 1.0 && pcrValue < 1.2) {
      return { label: 'Moderately\nBearish', color: 'text-[#FF6B6B]' }
    } else {
      return { label: 'Excessive\nBearishness', color: 'text-[#FF6B6B]' }
    }
  }

  useEffect(() => {
    let mounted = true

    const fetchTickerDetails = async () => {
      try {
        setIsLoading(true)
        setError(null)
        const details = await tickerOverviewService.getTickerDetails(symbol)
        if (mounted) {
          setTickerDetails(details as TickerDetails)
          // Remove "Class A Common Stock" and similar text from company name
          const cleanName = details.name
            .replace(/\s*Class\s+[A-Z]\s+Common\s+Stock\s*/gi, '')
            .replace(/\s*Common\s+Stock\s*/gi, '')
            .replace(/\s*Class\s+[A-Z]\s*/gi, '')
            .trim()
          setTagLine(cleanName)
        }
      } catch (err) {
        if (mounted) {
          setError('Failed to fetch ticker details')
          // Error fetching ticker details
        }
      } finally {
        if (mounted) {
          setIsLoading(false)
        }
      }
    }

    if (symbol) {
      fetchTickerDetails()
    }

    return () => {
      mounted = false
    }
  }, [symbol, setTagLine])

  // Extract current price data
  const tickerData = snapshotData?.[symbol]

  if (isLoading || isSnapshotLoading || isPerformanceLoading || isPCRLoading) {
    return <LoadingState />
  }

  if (error || !tickerDetails || isSnapshotError || !tickerData) {
    return <ErrorState message={error || 'No data available'} />
  }

  // Fallback for when ticker details don't have is_etf property
  const isETF = tickerDetails.is_etf || false

  // Market data
  const marketData = {
    overview: {
      close: tickerData?.day?.c || 0,
      change: tickerData?.todaysChange?.toFixed(2) || 0,
      percentage_change: tickerData?.todaysChangePerc?.toFixed(2) || 0,
      low: tickerData?.day?.l || 0,
      high: tickerData?.day?.h || 0,
      open: tickerData?.day?.o || 0,
    },
    PCR: pcr || 0.85, // Use real PCR data or fallback to 0.85
  }

  const performanceBoxes = performanceData
    ? [
        { period: '1D', value: performanceData.percentageChanges['1D'] },
        { period: '1W', value: performanceData.percentageChanges['1W'] },
        { period: '1M', value: performanceData.percentageChanges['1M'] },
        { period: '3M', value: performanceData.percentageChanges['3M'] },
        { period: '6M', value: performanceData.percentageChanges['6M'] },
        { period: 'YTD', value: performanceData.percentageChanges['YTD'] },
      ]
    : [
        { period: '1D', value: 0 },
        { period: '1W', value: 0 },
        { period: '1M', value: 0 },
        { period: '3M', value: 0 },
        { period: '6M', value: 0 },
        { period: 'YTD', value: 0 },
      ]

  return (
    <div className='flex h-full flex-col p-4'>
      {/* Sticky Header Section */}
      <div className='sticky top-0 z-10 bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)]'>
        <div className='mb-3 flex justify-between'>
          <div className='flex flex-col'>
            <h2 className='text-[24px] font-bold leading-[24px]'>
              {tickerDetails.ticker}
            </h2>
            {companyProfile?.companyName && (
              <div className='mt-1 flex items-center gap-2'>
                <p className='text-sm text-gray-400'>
                  {companyProfile.companyName}
                </p>
                {companyProfile?.website && (
                  <a
                    href={companyProfile.website}
                    target='_blank'
                    rel='noopener noreferrer'
                    className='text-gray-400 transition-colors hover:text-white'
                    title={`Visit ${companyProfile.companyName} website`}
                  >
                    <svg
                      className='h-4 w-4'
                      fill='none'
                      stroke='currentColor'
                      viewBox='0 0 24 24'
                      xmlns='http://www.w3.org/2000/svg'
                    >
                      <path
                        strokeLinecap='round'
                        strokeLinejoin='round'
                        strokeWidth={2}
                        d='M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14'
                      />
                    </svg>
                  </a>
                )}
              </div>
            )}
            <div className='mt-auto px-1'>
              <PriceDisplay
                price={marketData.overview.close}
                change={Number(marketData.overview.change)}
                percentageChange={Number(marketData.overview.percentage_change)}
              />
              {marketData.overview.high > marketData.overview.low && (
                <ProgressBar
                  start={marketData.overview.low}
                  end={marketData.overview.high}
                  breakpoint1={marketData.overview.low}
                  breakpoint2={marketData.overview.close}
                />
              )}
            </div>
          </div>
          <div className='px-2'>
            <h3
              className={`text-center text-[12px] font-bold ${getSentimentLabel(marketData.PCR).color}`}
            >
              {getSentimentLabel(marketData.PCR)
                .label.split('\n')
                .map((line, index) => (
                  <div key={index}>{line}</div>
                ))}
            </h3>
            <div className='flex justify-center'>
              <GreedMeter pcrValue={marketData.PCR} />
            </div>
            <div className='-mt-3 flex flex-col text-center'>
              <span className='text-sm font-bold leading-[12px]'>
                {marketData.PCR.toFixed(2)}
              </span>
              <span className='text-[10px] font-light'>PCR</span>
            </div>
          </div>
        </div>

        <div className='mb-2 mt-5'>
          <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>Performance</p>
          <div className='flex flex-wrap gap-1.5'>
            {performanceBoxes.map(({ period, value }) => (
              <PerformanceBox key={period} period={period} value={value} />
            ))}
          </div>
        </div>
      </div>

      {/* Scrollable Content Section */}
      <div className='flex-1 overflow-y-auto'>
        {/* Company/ETF Overview and Details from FMP */}
        <div className='mt-5 space-y-4'>
          {/* Company/ETF Overview */}
          {companyProfile?.description && (
            <div>
              <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>
                {isETF ? 'Fund Overview' : 'Company Overview'}
              </p>
              <p className='mb-3 text-[10px] font-normal leading-[18px]'>
                {companyProfile.description}
              </p>
            </div>
          )}

          {/* Error state for profile loading */}
          {profileError && (
            <div>
              <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>
                {isETF ? 'Fund Overview' : 'Company Overview'}
              </p>
              <p className='mb-3 text-[10px] font-normal leading-[18px] text-gray-400'>
                {isETF
                  ? 'Fund information temporarily unavailable. This is an Exchange-Traded Fund (ETF).'
                  : 'Company information temporarily unavailable.'}
              </p>
            </div>
          )}

          {/* Key Details */}
          <div>
            <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>
              {isETF ? 'Fund Details' : 'Key Details'}
            </p>
            {profileError ? (
              <p className='text-[10px] font-normal leading-[14px] text-gray-400'>
                {isETF
                  ? 'Fund details temporarily unavailable.'
                  : 'Company details temporarily unavailable.'}
              </p>
            ) : (
              <ul className='ml-3 list-disc space-y-1'>
                {companyProfile?.marketCap && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>
                      {isETF ? 'Assets Under Management' : 'Market Cap'}
                    </span>
                    :{' '}
                    {(() => {
                      const cap = companyProfile.marketCap
                      if (cap >= 1e12) return (cap / 1e12).toFixed(2) + 'T'
                      if (cap >= 1e9) return (cap / 1e9).toFixed(2) + 'B'
                      if (cap >= 1e6) return (cap / 1e6).toFixed(2) + 'M'
                      return cap.toLocaleString()
                    })()}
                  </li>
                )}
                {companyProfile?.sector && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>
                      {isETF ? 'Category' : 'Sector'}
                    </span>
                    : {companyProfile.sector}
                  </li>
                )}
                {companyProfile?.industry && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>
                      {isETF ? 'Fund Type' : 'Industry'}
                    </span>
                    : {companyProfile.industry}
                  </li>
                )}
                {!isETF && companyProfile?.employees && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Employees</span>:{' '}
                    {companyProfile.employees.toLocaleString()}
                  </li>
                )}
                {!isETF && companyProfile?.ceo && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>CEO</span>: {companyProfile.ceo}
                  </li>
                )}
                {companyProfile?.website && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Website</span>:{' '}
                    <a
                      href={companyProfile.website}
                      target='_blank'
                      rel='noopener noreferrer'
                      className='text-[#28BF94] transition hover:underline'
                    >
                      {companyProfile.website}
                    </a>
                  </li>
                )}
                {companyProfile?.country && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Country</span>:{' '}
                    {companyProfile.country}
                  </li>
                )}
                {companyProfile?.exchange && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Exchange</span>:{' '}
                    {companyProfile.exchange}
                  </li>
                )}
                {isETF && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Fund Type</span>:
                    Exchange-Traded Fund (ETF)
                  </li>
                )}
              </ul>
            )}
          </div>

          {/* Key Metrics - Only show for non-ETFs */}
          {!isETF && keyMetrics && !metricsError && (
            <div>
              <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>
                Key Metrics
              </p>
              <ul className='ml-3 list-disc space-y-1'>
                {keyMetrics.peRatio && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>P/E Ratio</span>:{' '}
                    {keyMetrics.peRatio.toFixed(2)}
                  </li>
                )}
                {keyMetrics.priceToBookRatio && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Price-to-Book</span>:{' '}
                    {keyMetrics.priceToBookRatio.toFixed(2)}
                  </li>
                )}
                {keyMetrics.priceToSalesRatio && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Price-to-Sales</span>:{' '}
                    {keyMetrics.priceToSalesRatio.toFixed(2)}
                  </li>
                )}
                {keyMetrics.returnOnEquity && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>ROE</span>:{' '}
                    {(keyMetrics.returnOnEquity * 100).toFixed(2)}%
                  </li>
                )}
                {keyMetrics.returnOnAssets && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>ROA</span>:{' '}
                    {(keyMetrics.returnOnAssets * 100).toFixed(2)}%
                  </li>
                )}
                {keyMetrics.debtToEquity && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Debt-to-Equity</span>:{' '}
                    {keyMetrics.debtToEquity.toFixed(2)}
                  </li>
                )}
                {keyMetrics.currentRatio && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Current Ratio</span>:{' '}
                    {keyMetrics.currentRatio.toFixed(2)}
                  </li>
                )}
                {keyMetrics.dividendYield && (
                  <li className='text-[10px] font-normal leading-[14px]'>
                    <span className='font-bold'>Dividend Yield</span>:{' '}
                    {(keyMetrics.dividendYield * 100).toFixed(2)}%
                  </li>
                )}
              </ul>
            </div>
          )}

          {/* ETF-Specific Information */}
          {isETF && (
            <div>
              <p className='mb-2 text-sm font-bold text-[#F5F7F7]'>
                ETF Information
              </p>
              <ul className='ml-3 list-disc space-y-1'>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Fund Family: State Street Global Advisors
                </li>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Expense Ratio: 0.0945%
                </li>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Tracking Index: S&P 500
                </li>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Inception Date: January 22, 1993
                </li>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Dividend Frequency: Quarterly
                </li>
                <li className='text-[10px] font-normal leading-[14px]'>
                  Liquidity: High (Most liquid ETF)
                </li>
              </ul>
            </div>
          )}
        </div>

        {/* Add padding at the end */}
        <div className='pb-8'></div>
      </div>
    </div>
  )
}

export default TickerRightTab
