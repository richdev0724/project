import {
  useAnalystRatings,
  usePriceTarget,
  useUpgradesDowngrades,
} from '@/hooks/use-analyst-ratings'
import Loader from '@/components/loader'
import { useParams } from 'react-router-dom'
import moment from 'moment'
import {
  Minus,
  ArrowUp,
  ArrowDown,
  TrendingUp,
  TrendingDown,
  Info,
} from 'lucide-react'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { useState } from 'react'
import MetricDetailModal from './metric-detail-modal'

const TickerAnalystRatings = () => {
  const { symbol } = useParams()
  const {
    rating,
    isLoading: isLoadingRating,
    error: ratingError,
  } = useAnalystRatings(symbol || '')
  const { priceTargets, isLoading: isLoadingPriceTarget } = usePriceTarget(
    symbol || ''
  )
  const { upgradesDowngrades, isLoading: isLoadingUpgrades } =
    useUpgradesDowngrades(symbol || '', 8)

  const [selectedMetric, setSelectedMetric] = useState<{
    type: 'DCF' | 'PE' | 'ROE' | 'ROA' | 'DE' | 'PB'
    value: string
    score: number
  } | null>(null)

  if (!symbol) {
    return (
      <div className='flex h-[404px] w-full items-center justify-center'>
        <p className='text-gray-400'>No ticker symbol provided</p>
      </div>
    )
  }

  const isLoading = isLoadingRating || isLoadingPriceTarget || isLoadingUpgrades

  const getRatingColor = (recommendation: string) => {
    const rec = recommendation?.toLowerCase() || ''
    if (rec.includes('strong buy')) return 'text-[#00FF88]'
    if (rec.includes('buy')) return 'text-[#45CA89]'
    if (rec.includes('hold')) return 'text-[#FFA500]'
    if (rec.includes('sell') && !rec.includes('strong')) return 'text-[#FF6B6B]'
    if (rec.includes('strong sell')) return 'text-[#FF0000]'
    return 'text-gray-400'
  }

  const formatPrice = (price: number) => {
    if (!price) return 'N/A'
    return `$${price.toFixed(2)}`
  }

  const avgPriceTarget =
    priceTargets && priceTargets.length > 0
      ? priceTargets.reduce((sum, pt) => sum + (pt.priceTarget || 0), 0) /
        priceTargets.length
      : null

  const highPriceTarget =
    priceTargets && priceTargets.length > 0
      ? Math.max(...priceTargets.map((pt) => pt.priceTarget || 0))
      : null

  const lowPriceTarget =
    priceTargets && priceTargets.length > 0
      ? Math.min(...priceTargets.map((pt) => pt.priceTarget || 0))
      : null

  return (
    <TooltipProvider delayDuration={1500}>
      <div className='flex h-[404px] w-full flex-col px-4 pb-4 pt-4'>
        <div className='mb-5 flex w-full items-center justify-between pl-2'>
          <div className='flex items-center gap-2'>
            <span className='text-base font-normal'>
              {symbol} Analyst Ratings
            </span>
            <Tooltip>
              <TooltipTrigger asChild>
                <Info className='h-3.5 w-3.5 cursor-help text-gray-500 transition-colors hover:text-gray-300' />
              </TooltipTrigger>
              <TooltipContent side='right' className='max-w-xs'>
                <p className='text-xs'>
                  Comprehensive analyst consensus, price targets, recent
                  upgrades/downgrades, and fundamental analysis scores from top
                  financial institutions.
                </p>
              </TooltipContent>
            </Tooltip>
          </div>
          {rating && (
            <span className='text-sm font-light italic text-gray-400'>
              updated {moment(rating.date).format('MMM D, YYYY')}
            </span>
          )}
        </div>
        {isLoading ? (
          <div className='flex h-[344px] items-center justify-center'>
            <Loader />
          </div>
        ) : ratingError || !rating ? (
          <div className='flex flex-1 items-center justify-center'>
            <p className='text-gray-400'>No analyst ratings available</p>
          </div>
        ) : (
          <div className='flex flex-1 flex-col gap-2.5'>
            {/* Consensus Rating - Condensed */}
            <div className='relative overflow-hidden rounded-xl border border-gray-700/40 bg-gradient-to-br from-gray-800/60 via-gray-800/40 to-gray-900/30 p-3 shadow-[0_8px_16px_rgba(0,0,0,0.4)] backdrop-blur-sm'>
              <div className='relative flex items-center justify-between gap-4 pr-4'>
                <div className='flex flex-1 flex-col justify-center py-1 pl-4'>
                  <div className='mb-0.5'>
                    <p className='text-[10px] font-medium tracking-wider text-white'>
                      Consensus Rating
                    </p>
                  </div>
                  <div className='flex items-center gap-2'>
                    <h3
                      className={`text-xl font-bold tracking-tight ${getRatingColor(rating.ratingRecommendation)}`}
                    >
                      {rating.ratingRecommendation}
                    </h3>
                    {rating.ratingScore >= 3.5 && (
                      <TrendingUp className='h-3.5 w-3.5 text-[#45CA89]' />
                    )}
                    {rating.ratingScore < 2.5 && (
                      <TrendingDown className='h-3.5 w-3.5 text-[#FF6B6B]' />
                    )}
                  </div>
                </div>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <div className='mt-1.5 cursor-help'>
                      {/* Horizontal gradient scale */}
                      <div className='flex flex-col gap-1.5 pt-2'>
                        <div className='relative h-2 w-48 rounded-full bg-gradient-to-r from-[#DC4034] to-[#24D438] shadow-[0_0_8px_rgba(0,0,0,0.4)] ring-1 ring-white/5'>
                          {/* Triangle indicator pointing down */}
                          <div
                            className='absolute -top-2'
                            style={{
                              left: `calc(${((rating.ratingScore - 1) / 4) * 100}% - 4px)`,
                            }}
                          >
                            <div className='h-0 w-0 border-l-[4px] border-r-[4px] border-t-[6px] border-l-transparent border-r-transparent border-t-white' />
                          </div>
                        </div>
                        <div className='flex w-48 justify-between text-[7px] font-medium text-gray-400'>
                          <span>1</span>
                          <span>2</span>
                          <span>3</span>
                          <span>4</span>
                          <span>5</span>
                        </div>
                      </div>
                    </div>
                  </TooltipTrigger>
                  <TooltipContent side='left' className='max-w-xs'>
                    <p className='text-xs font-semibold'>
                      Consensus Rating: {rating.ratingScore.toFixed(2)}/5.0
                    </p>
                    <p className='mt-1 text-[10px] text-gray-300'>
                      1 = Strong Sell | 2 = Sell | 3 = Hold | 4 = Buy | 5 =
                      Strong Buy
                    </p>
                  </TooltipContent>
                </Tooltip>
              </div>
            </div>

            {/* Price Targets - Enhanced */}
            {avgPriceTarget && (
              <div className='group relative overflow-hidden rounded-xl border border-gray-700/40 bg-gradient-to-br from-gray-800/50 via-gray-800/30 to-gray-900/20 p-3 shadow-lg transition-all duration-300 hover:border-gray-600/50 hover:shadow-xl'>
                <div className='absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.02] to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100' />
                <div className='relative px-4 py-1'>
                  <div className='mb-2 flex items-center justify-between'>
                    <div className='flex items-center gap-1.5'>
                      <p className='text-[10px] font-medium tracking-wider text-white'>
                        Price Targets
                      </p>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <Info className='h-3 w-3 cursor-help text-gray-500 transition-colors hover:text-gray-300' />
                        </TooltipTrigger>
                        <TooltipContent side='top' className='max-w-xs'>
                          <p className='text-xs'>
                            Price targets set by {priceTargets?.length || 0}{' '}
                            analyst
                            {(priceTargets?.length || 0) !== 1 ? 's' : ''}. The
                            bar shows the range from lowest to highest target,
                            with the average marked.
                          </p>
                        </TooltipContent>
                      </Tooltip>
                    </div>
                    <div className='rounded-full bg-gray-700/40 px-2 py-0.5'>
                      <p className='text-[10px] font-medium text-gray-300'>
                        {priceTargets?.length || 0} analyst
                        {(priceTargets?.length || 0) !== 1 ? 's' : ''}
                      </p>
                    </div>
                  </div>

                  {/* Enhanced Visual Price Range Bar */}
                  <div className='relative mb-2 h-3 overflow-hidden rounded-full bg-gray-800/60 shadow-inner'>
                    <div className='absolute inset-0 bg-gradient-to-r from-[#FF6B6B] via-[#FFA500] to-[#00FF88] opacity-70'></div>
                    {/* Average indicator with pulse animation */}
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div
                          className='absolute bottom-0 top-0 w-1.5 cursor-help bg-white shadow-2xl transition-all duration-300 hover:w-2'
                          style={{
                            left: `${((avgPriceTarget - (lowPriceTarget || 0)) / ((highPriceTarget || 0) - (lowPriceTarget || 0))) * 100}%`,
                          }}
                        >
                          <div className='absolute -inset-1 animate-pulse rounded-full bg-white/30' />
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className='text-xs font-semibold'>
                          Average Target: {formatPrice(avgPriceTarget)}
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  </div>

                  <div className='grid grid-cols-3 gap-2'>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className='cursor-help rounded-lg border border-[#FF6B6B]/20 bg-gradient-to-br from-[#FF6B6B]/10 to-transparent p-2 text-center transition-all duration-300 hover:border-[#FF6B6B]/40 hover:from-[#FF6B6B]/20'>
                          <p className='mb-0.5 text-[10px] font-medium tracking-wide text-gray-400'>
                            Low
                          </p>
                          <p className='text-sm font-bold text-[#FF6B6B]'>
                            {formatPrice(lowPriceTarget!)}
                          </p>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className='text-xs'>
                          Most conservative price target
                        </p>
                      </TooltipContent>
                    </Tooltip>

                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className='cursor-help rounded-lg border border-[#45CA89]/30 bg-gradient-to-br from-[#45CA89]/15 to-transparent p-2 text-center shadow-lg transition-all duration-300 hover:scale-105 hover:border-[#45CA89]/50 hover:from-[#45CA89]/25 hover:shadow-xl'>
                          <p className='mb-0.5 text-[10px] font-medium tracking-wide text-gray-400'>
                            Average
                          </p>
                          <p className='text-base font-black text-[#45CA89]'>
                            {formatPrice(avgPriceTarget)}
                          </p>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className='text-xs font-semibold'>
                          Average of all analyst targets
                        </p>
                        <p className='mt-1 text-[10px] text-gray-400'>
                          Based on {priceTargets?.length || 0} analyst
                          {(priceTargets?.length || 0) !== 1 ? 's' : ''}
                        </p>
                      </TooltipContent>
                    </Tooltip>

                    <Tooltip>
                      <TooltipTrigger asChild>
                        <div className='cursor-help rounded-lg border border-[#00FF88]/20 bg-gradient-to-br from-[#00FF88]/10 to-transparent p-2 text-center transition-all duration-300 hover:border-[#00FF88]/40 hover:from-[#00FF88]/20'>
                          <p className='mb-0.5 text-[10px] font-medium tracking-wide text-gray-400'>
                            High
                          </p>
                          <p className='text-sm font-bold text-[#00FF88]'>
                            {formatPrice(highPriceTarget!)}
                          </p>
                        </div>
                      </TooltipTrigger>
                      <TooltipContent>
                        <p className='text-xs'>Most optimistic price target</p>
                      </TooltipContent>
                    </Tooltip>
                  </div>
                </div>
              </div>
            )}

            {/* Recent Upgrades/Downgrades - Enhanced */}
            {upgradesDowngrades && upgradesDowngrades.length > 0 && (
              <div className='group relative overflow-hidden rounded-xl border border-gray-700/40 bg-gradient-to-br from-gray-800/50 via-gray-800/30 to-gray-900/20 p-3 shadow-lg transition-all duration-300 hover:border-gray-600/50 hover:shadow-xl'>
                <div className='absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.02] to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100' />
                <div className='relative px-4 py-1'>
                  <div className='mb-2 flex items-center gap-1.5'>
                    <p className='text-[10px] font-medium tracking-wider text-white'>
                      Recent Analyst Actions
                    </p>
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Info className='h-3 w-3 cursor-help text-gray-500 transition-colors hover:text-gray-300' />
                      </TooltipTrigger>
                      <TooltipContent side='top' className='max-w-xs'>
                        <p className='text-xs'>
                          Latest rating changes from major financial
                          institutions. Upgrades (green) indicate increased
                          optimism, downgrades (red) indicate concerns.
                        </p>
                      </TooltipContent>
                    </Tooltip>
                  </div>
                  <div className='space-y-1.5'>
                    {upgradesDowngrades.map((item, index) => {
                      const isUpgrade =
                        item.action?.toLowerCase().includes('upgrade') ||
                        item.action?.toLowerCase().includes('up')
                      const isDowngrade =
                        item.action?.toLowerCase().includes('downgrade') ||
                        item.action?.toLowerCase().includes('down')

                      return (
                        <Tooltip key={index}>
                          <TooltipTrigger asChild>
                            <div className='group/item flex cursor-help items-center gap-2.5 rounded-lg border border-gray-700/30 bg-gradient-to-r from-gray-900/60 to-gray-900/30 p-2 transition-all duration-200 hover:border-gray-600/50 hover:from-gray-800/60 hover:to-gray-900/40 hover:shadow-md'>
                              {/* Icon with animation */}
                              <div
                                className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg transition-all duration-200 group-hover/item:scale-110 ${
                                  isUpgrade
                                    ? 'bg-[#45CA89]/20 shadow-[0_0_10px_rgba(69,202,137,0.2)]'
                                    : isDowngrade
                                      ? 'bg-[#FF6B6B]/20 shadow-[0_0_10px_rgba(255,107,107,0.2)]'
                                      : 'bg-gray-700/30'
                                }`}
                              >
                                {isUpgrade ? (
                                  <ArrowUp className='h-4 w-4 text-[#45CA89]' />
                                ) : isDowngrade ? (
                                  <ArrowDown className='h-4 w-4 text-[#FF6B6B]' />
                                ) : (
                                  <Minus className='h-4 w-4 text-gray-400' />
                                )}
                              </div>

                              {/* Content */}
                              <div className='min-w-0 flex-1'>
                                <div className='flex items-center justify-between gap-2'>
                                  <span className='truncate text-[11px] font-semibold text-white'>
                                    {item.gradingCompany}
                                  </span>
                                  <div className='flex items-center gap-1.5'>
                                    {isUpgrade && (
                                      <TrendingUp className='h-3 w-3 text-[#45CA89]' />
                                    )}
                                    {isDowngrade && (
                                      <TrendingDown className='h-3 w-3 text-[#FF6B6B]' />
                                    )}
                                    <span className='whitespace-nowrap text-[10px] text-gray-400'>
                                      {moment(item.publishedDate).format(
                                        'MMM D, YYYY'
                                      )}
                                    </span>
                                  </div>
                                </div>
                                <div className='mt-1 flex items-center gap-2'>
                                  {item.previousGrade && (
                                    <>
                                      <span className='rounded bg-gray-800/60 px-1.5 py-0.5 text-[9px] font-medium text-gray-400'>
                                        {item.previousGrade}
                                      </span>
                                      <span className='text-[10px] text-gray-600'>
                                        →
                                      </span>
                                    </>
                                  )}
                                  <span
                                    className={`rounded px-1.5 py-0.5 text-[9px] font-semibold ${
                                      isUpgrade
                                        ? 'bg-[#45CA89]/20 text-[#45CA89]'
                                        : isDowngrade
                                          ? 'bg-[#FF6B6B]/20 text-[#FF6B6B]'
                                          : 'bg-gray-700/40 text-gray-300'
                                    }`}
                                  >
                                    {item.newGrade}
                                  </span>
                                </div>
                              </div>
                            </div>
                          </TooltipTrigger>
                          <TooltipContent side='left' className='max-w-xs'>
                            <div className='space-y-1'>
                              <p className='text-xs font-semibold'>
                                {item.gradingCompany}
                              </p>
                              <p className='text-[10px] text-gray-400'>
                                {item.action || 'Rating Change'}
                              </p>
                              {item.priceWhenPosted && (
                                <p className='text-[10px] text-gray-400'>
                                  Price when posted: $
                                  {item.priceWhenPosted.toFixed(2)}
                                </p>
                              )}
                            </div>
                          </TooltipContent>
                        </Tooltip>
                      )
                    })}
                  </div>
                </div>
              </div>
            )}

            {/* Fundamental Scores - Enhanced */}
            <div className='relative overflow-hidden rounded-xl border border-gray-700/40 bg-gradient-to-br from-gray-800/50 via-gray-800/30 to-gray-900/20 p-3 shadow-[0_8px_16px_rgba(0,0,0,0.4)]'>
              <div className='relative px-4 py-1'>
                <div className='mb-2 flex items-center gap-1.5'>
                  <p className='text-[10px] font-medium tracking-wider text-white'>
                    Fundamental Analysis
                  </p>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Info className='h-3 w-3 cursor-help text-gray-500 transition-colors hover:text-gray-300' />
                    </TooltipTrigger>
                    <TooltipContent side='top' className='max-w-xs'>
                      <p className='text-xs'>
                        Key financial metrics analyzed by experts. Each metric
                        is scored from 1-5 based on company fundamentals and
                        industry comparisons.
                      </p>
                    </TooltipContent>
                  </Tooltip>
                </div>
                <div className='grid grid-cols-2 gap-2'>
                  {[
                    {
                      label: 'DCF',
                      type: 'DCF' as const,
                      fullName: 'Discounted Cash Flow',
                      value: rating.ratingDetailsDCFRecommendation,
                      score: rating.ratingDetailsDCFScore,
                      description:
                        'Valuation based on projected future cash flows',
                    },
                    {
                      label: 'P/E',
                      type: 'PE' as const,
                      fullName: 'Price-to-Earnings',
                      value: rating.ratingDetailsPERecommendation,
                      score: rating.ratingDetailsPEScore,
                      description: 'Stock price relative to earnings per share',
                    },
                    {
                      label: 'ROE',
                      type: 'ROE' as const,
                      fullName: 'Return on Equity',
                      value: rating.ratingDetailsROERecommendation,
                      score: rating.ratingDetailsROEScore,
                      description:
                        'Profitability relative to shareholder equity',
                    },
                    {
                      label: 'ROA',
                      type: 'ROA' as const,
                      fullName: 'Return on Assets',
                      value: rating.ratingDetailsROARecommendation,
                      score: rating.ratingDetailsROAScore,
                      description: 'How efficiently company uses its assets',
                    },
                    {
                      label: 'D/E',
                      type: 'DE' as const,
                      fullName: 'Debt-to-Equity',
                      value: rating.ratingDetailsDERecommendation,
                      score: rating.ratingDetailsDEScore,
                      description: 'Financial leverage and debt management',
                    },
                    {
                      label: 'P/B',
                      type: 'PB' as const,
                      fullName: 'Price-to-Book',
                      value: rating.ratingDetailsPBRecommendation,
                      score: rating.ratingDetailsPBScore,
                      description: 'Market value relative to book value',
                    },
                  ].map((metric, idx) => (
                    <div
                      key={idx}
                      onClick={() =>
                        setSelectedMetric({
                          type: metric.type,
                          value: metric.value,
                          score: metric.score,
                        })
                      }
                      className='group/metric flex cursor-pointer flex-col justify-between rounded-lg border border-gray-700/30 bg-gradient-to-br from-gray-900/60 to-gray-900/30 p-2 transition-all duration-200 hover:scale-[1.02] hover:border-gray-600/50 hover:from-gray-800/60 hover:to-gray-900/40 hover:shadow-lg'
                    >
                      <div className='mb-1.5 flex items-start justify-between gap-2 px-2'>
                        <div className='flex-1'>
                          <span className='text-[11px] font-bold text-white'>
                            {metric.fullName}
                          </span>
                        </div>
                        <div
                          className={`rounded px-2 py-1 text-[10px] font-semibold ${
                            metric.score >= 4
                              ? 'bg-[#45CA89]/20 text-[#45CA89]'
                              : metric.score >= 3
                                ? 'bg-[#FFA500]/20 text-[#FFA500]'
                                : 'bg-[#FF6B6B]/20 text-[#FF6B6B]'
                          }`}
                        >
                          {metric.value} - {metric.score}
                        </div>
                      </div>
                      <div className='flex items-center gap-1'>
                        {[1, 2, 3, 4, 5].map((dot) => (
                          <div
                            key={dot}
                            className={`h-1.5 flex-1 rounded-full transition-all duration-200 ${
                              dot <= metric.score
                                ? metric.score >= 4
                                  ? 'bg-[#45CA89] shadow-[0_0_4px_rgba(69,202,137,0.5)]'
                                  : metric.score >= 3
                                    ? 'bg-[#FFA500] shadow-[0_0_4px_rgba(255,165,0,0.5)]'
                                    : 'bg-[#FF6B6B] shadow-[0_0_4px_rgba(255,107,107,0.5)]'
                                : 'bg-gray-700/50'
                            } ${dot <= metric.score ? 'group-hover/metric:shadow-lg' : ''}`}
                          />
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Metric Detail Modal */}
        {selectedMetric && (
          <MetricDetailModal
            isOpen={!!selectedMetric}
            onClose={() => setSelectedMetric(null)}
            metricType={selectedMetric.type}
            value={selectedMetric.value}
            score={selectedMetric.score}
          />
        )}
      </div>
    </TooltipProvider>
  )
}

export default TickerAnalystRatings
