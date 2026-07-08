import {
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  Calendar,
  Award,
  AlertCircle,
  BarChart3,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  PieChart as RechartsPieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts'
import type { PortfolioData } from '@/data/mockHoldings'
import type { PoliticianTrade } from './PoliticianTradesTable'

interface PoliticianPortfolioProps {
  portfolioData: PortfolioData
  trades: PoliticianTrade[]
  onBack: () => void
}

const COLORS = [
  '#3b82f6',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#ec4899',
]

export function PoliticianPortfolio({
  portfolioData,
  trades,
  onBack,
}: PoliticianPortfolioProps) {
  const {
    politician,
    party,
    position,
    state,
    metrics,
    holdings,
    historicalValue,
    sectorAllocation,
  } = portfolioData

  const getPartyColor = (party: string) => {
    switch (party) {
      case 'Democrat':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
      case 'Republican':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300'
      case 'Independent':
        return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300'
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300'
    }
  }

  const formatCurrency = (value: number) => {
    if (value >= 1000000) {
      return `$${(value / 1000000).toFixed(2)}M`
    }
    return `$${value.toLocaleString()}`
  }

  const formatPercent = (value: number) => {
    return `${value >= 0 ? '+' : ''}${value.toFixed(2)}%`
  }

  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  }

  const recentTrades = trades
    .filter((t) => t.politician === politician)
    .sort(
      (a, b) =>
        new Date(b.disclosureDate).getTime() -
        new Date(a.disclosureDate).getTime()
    )
    .slice(0, 10)

  const purchaseCount = recentTrades.filter(
    (t) => t.tradeType === 'Purchase'
  ).length
  const saleCount = recentTrades.filter((t) => t.tradeType === 'Sale').length

  return (
    <div>
      {/* Back Button */}
      <Button variant='ghost' onClick={onBack} className='mb-6'>
        <ArrowLeft className='mr-2 h-4 w-4' />
        Back to All Trades
      </Button>

      {/* Header */}
      <div className='mb-8'>
        <div className='flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between'>
          <div>
            <div className='mb-2 flex items-center gap-3'>
              <h1 className='text-4xl'>{politician}</h1>
              <Badge variant='outline' className={getPartyColor(party)}>
                {party}
              </Badge>
            </div>
            <p className='text-muted-foreground'>
              {position}, {state}
            </p>
            <div className='mt-3 flex flex-wrap gap-2'>
              {metrics.committees.map((committee, idx) => (
                <Badge key={idx} variant='secondary' className='text-xs'>
                  {committee}
                </Badge>
              ))}
            </div>
          </div>
          <div className='text-left sm:text-right'>
            <div className='text-sm text-muted-foreground'>
              Total Portfolio Value
            </div>
            <div className='mt-1 text-3xl'>
              {formatCurrency(metrics.totalValue)}
            </div>
            <div
              className={`mt-2 flex items-center gap-1 ${metrics.totalGainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}
            >
              {metrics.totalGainLoss >= 0 ? (
                <TrendingUp className='h-5 w-5' />
              ) : (
                <TrendingDown className='h-5 w-5' />
              )}
              <span className='text-xl'>
                {formatCurrency(metrics.totalGainLoss)} (
                {formatPercent(metrics.percentGainLoss)})
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Key Metrics */}
      <div className='mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4'>
        <Card>
          <CardHeader className='pb-3'>
            <CardTitle className='flex items-center gap-2 text-sm text-muted-foreground'>
              <BarChart3 className='h-4 w-4' />
              Win Rate
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className='text-2xl'>{metrics.winRate.toFixed(1)}%</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              Based on {metrics.totalTrades} trades
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className='pb-3'>
            <CardTitle className='flex items-center gap-2 text-sm text-muted-foreground'>
              <Award className='h-4 w-4' />
              Best Trade
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className='font-mono text-2xl'>{metrics.bestTrade.ticker}</div>
            <div className='mt-1 text-sm text-green-600'>
              +{formatCurrency(metrics.bestTrade.gainLoss)} (
              {formatPercent(metrics.bestTrade.percentGainLoss)})
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className='pb-3'>
            <CardTitle className='flex items-center gap-2 text-sm text-muted-foreground'>
              <AlertCircle className='h-4 w-4' />
              Worst Trade
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className='font-mono text-2xl'>
              {metrics.worstTrade.ticker}
            </div>
            <div className='mt-1 text-sm text-red-600'>
              {formatCurrency(metrics.worstTrade.gainLoss)} (
              {formatPercent(metrics.worstTrade.percentGainLoss)})
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className='pb-3'>
            <CardTitle className='flex items-center gap-2 text-sm text-muted-foreground'>
              <Calendar className='h-4 w-4' />
              Avg. Disclosure Time
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className='text-2xl'>{metrics.avgDaysToDisclose} days</div>
            <div className='mt-1 text-xs text-muted-foreground'>
              Legal limit: 45 days
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Tabs for different sections */}
      <Tabs defaultValue='holdings' className='space-y-6'>
        <TabsList className='grid w-full grid-cols-4'>
          <TabsTrigger value='holdings'>Holdings</TabsTrigger>
          <TabsTrigger value='performance'>Performance</TabsTrigger>
          <TabsTrigger value='trades'>Trade History</TabsTrigger>
          <TabsTrigger value='analytics'>Analytics</TabsTrigger>
        </TabsList>

        {/* Holdings Tab */}
        <TabsContent value='holdings' className='space-y-6'>
          <Card>
            <CardHeader>
              <CardTitle>Current Holdings</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='rounded-lg border'>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Ticker</TableHead>
                      <TableHead>Asset</TableHead>
                      <TableHead className='text-right'>Shares</TableHead>
                      <TableHead className='text-right'>Avg Cost</TableHead>
                      <TableHead className='text-right'>
                        Current Price
                      </TableHead>
                      <TableHead className='text-right'>Market Value</TableHead>
                      <TableHead className='text-right'>Total Return</TableHead>
                      <TableHead className='text-right'>% Return</TableHead>
                      <TableHead className='text-right'>Days Held</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {holdings.map((holding, idx) => (
                      <TableRow key={idx}>
                        <TableCell>
                          <span className='font-mono'>{holding.ticker}</span>
                        </TableCell>
                        <TableCell>
                          <div>
                            <div className='text-foreground'>
                              {holding.assetName}
                            </div>
                            <div className='text-sm text-muted-foreground'>
                              {holding.sector}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell className='text-right'>
                          {holding.shares.toLocaleString()}
                        </TableCell>
                        <TableCell className='text-right'>
                          ${holding.avgCost.toFixed(2)}
                        </TableCell>
                        <TableCell className='text-right'>
                          ${holding.currentPrice.toFixed(2)}
                        </TableCell>
                        <TableCell className='text-right'>
                          {formatCurrency(holding.marketValue)}
                        </TableCell>
                        <TableCell
                          className={`text-right ${holding.totalGainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}
                        >
                          {formatCurrency(holding.totalGainLoss)}
                        </TableCell>
                        <TableCell
                          className={`text-right ${holding.percentGainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}
                        >
                          {formatPercent(holding.percentGainLoss)}
                        </TableCell>
                        <TableCell className='text-right text-muted-foreground'>
                          {holding.daysHeld}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Performance Tab */}
        <TabsContent value='performance' className='space-y-6'>
          <div className='grid grid-cols-1 gap-6 lg:grid-cols-2'>
            <Card>
              <CardHeader>
                <CardTitle>Portfolio Value Over Time</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width='100%' height={300}>
                  <AreaChart data={historicalValue}>
                    <CartesianGrid
                      strokeDasharray='3 3'
                      stroke='hsl(var(--border))'
                    />
                    <XAxis
                      dataKey='date'
                      stroke='hsl(var(--muted-foreground))'
                    />
                    <YAxis
                      stroke='hsl(var(--muted-foreground))'
                      tickFormatter={(value) => formatCurrency(value)}
                    />
                    <Tooltip
                      formatter={(value: number) => formatCurrency(value)}
                      contentStyle={{
                        backgroundColor: 'hsl(var(--card))',
                        border: '1px solid hsl(var(--border))',
                      }}
                    />
                    <Area
                      type='monotone'
                      dataKey='value'
                      stroke='#3b82f6'
                      fill='#3b82f6'
                      fillOpacity={0.2}
                      name='Portfolio Value'
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Portfolio vs S&P 500</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width='100%' height={300}>
                  <LineChart data={historicalValue}>
                    <CartesianGrid
                      strokeDasharray='3 3'
                      stroke='hsl(var(--border))'
                    />
                    <XAxis
                      dataKey='date'
                      stroke='hsl(var(--muted-foreground))'
                    />
                    <YAxis stroke='hsl(var(--muted-foreground))' />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'hsl(var(--card))',
                        border: '1px solid hsl(var(--border))',
                      }}
                    />
                    <Legend />
                    <Line
                      type='monotone'
                      dataKey='sp500'
                      stroke='#10b981'
                      name='S&P 500 (Indexed)'
                      strokeWidth={2}
                    />
                    <Line
                      type='monotone'
                      dataKey={(d) =>
                        (d.value / historicalValue[0].value) * 100
                      }
                      stroke='#3b82f6'
                      name='Portfolio (Indexed)'
                      strokeWidth={2}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Sector Allocation</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='grid grid-cols-1 gap-6 lg:grid-cols-2'>
                <ResponsiveContainer width='100%' height={300}>
                  <RechartsPieChart>
                    <Pie
                      data={sectorAllocation}
                      cx='50%'
                      cy='50%'
                      labelLine={false}
                      label={(entry) =>
                        `${entry.sector} ${entry.percentage.toFixed(1)}%`
                      }
                      outerRadius={100}
                      fill='#8884d8'
                      dataKey='value'
                    >
                      {sectorAllocation.map((_entry, index) => (
                        <Cell
                          key={`cell-${index}`}
                          fill={COLORS[index % COLORS.length]}
                        />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value: number) => formatCurrency(value)}
                    />
                  </RechartsPieChart>
                </ResponsiveContainer>

                <div className='space-y-3'>
                  {sectorAllocation.map((sector, idx) => (
                    <div
                      key={idx}
                      className='flex items-center justify-between rounded-lg border p-3'
                    >
                      <div className='flex items-center gap-3'>
                        <div
                          className='h-4 w-4 rounded'
                          style={{
                            backgroundColor: COLORS[idx % COLORS.length],
                          }}
                        />
                        <span>{sector.sector}</span>
                      </div>
                      <div className='text-right'>
                        <div>{formatCurrency(sector.value)}</div>
                        <div className='text-sm text-muted-foreground'>
                          {sector.percentage.toFixed(2)}%
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Trade History Tab */}
        <TabsContent value='trades' className='space-y-6'>
          <div className='mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3'>
            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Total Trades
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>{recentTrades.length}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Purchases
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl text-green-600'>{purchaseCount}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Sales
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl text-orange-600'>{saleCount}</div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Recent Trade Activity</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='rounded-lg border'>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Ticker</TableHead>
                      <TableHead>Asset</TableHead>
                      <TableHead>Amount</TableHead>
                      <TableHead>Owner</TableHead>
                      <TableHead>Filed</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {recentTrades.map((trade) => (
                      <TableRow key={trade.id}>
                        <TableCell>{formatDate(trade.tradeDate)}</TableCell>
                        <TableCell>
                          <Badge
                            variant='outline'
                            className={
                              trade.tradeType === 'Purchase'
                                ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                                : 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300'
                            }
                          >
                            {trade.tradeType}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <span className='font-mono'>{trade.ticker}</span>
                        </TableCell>
                        <TableCell>
                          <div>
                            <div className='text-foreground'>
                              {trade.assetName}
                            </div>
                            <div className='text-sm text-muted-foreground'>
                              {trade.assetType}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>{trade.amount}</TableCell>
                        <TableCell className='text-sm text-muted-foreground'>
                          {trade.owner}
                        </TableCell>
                        <TableCell className='text-muted-foreground'>
                          {formatDate(trade.disclosureDate)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Analytics Tab */}
        <TabsContent value='analytics' className='space-y-6'>
          <div className='grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3'>
            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Avg Trade Size
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>
                  {formatCurrency(metrics.avgTradeSize)}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Years in Office
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>
                  {(metrics.daysInOffice / 365).toFixed(1)}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Total Holdings
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>{holdings.length}</div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Avg Holding Period
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>
                  {Math.round(
                    holdings.reduce((sum, h) => sum + h.daysHeld, 0) /
                      holdings.length
                  )}{' '}
                  days
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Largest Position
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='font-mono text-xl'>
                  {
                    holdings.reduce(
                      (max, h) => (h.marketValue > max.marketValue ? h : max),
                      holdings[0]
                    ).ticker
                  }
                </div>
                <div className='mt-1 text-sm text-muted-foreground'>
                  {formatCurrency(
                    holdings.reduce(
                      (max, h) => (h.marketValue > max.marketValue ? h : max),
                      holdings[0]
                    ).marketValue
                  )}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className='pb-3'>
                <CardTitle className='text-sm text-muted-foreground'>
                  Portfolio Concentration
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className='text-2xl'>
                  {(
                    (holdings
                      .slice(0, 3)
                      .reduce((sum, h) => sum + h.marketValue, 0) /
                      metrics.totalValue) *
                    100
                  ).toFixed(1)}
                  %
                </div>
                <div className='mt-1 text-xs text-muted-foreground'>
                  Top 3 holdings
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Top Performers</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='space-y-3'>
                {holdings
                  .sort((a, b) => b.percentGainLoss - a.percentGainLoss)
                  .slice(0, 5)
                  .map((holding, idx) => (
                    <div
                      key={idx}
                      className='flex items-center justify-between rounded-lg border p-3'
                    >
                      <div>
                        <div className='font-mono'>{holding.ticker}</div>
                        <div className='text-sm text-muted-foreground'>
                          {holding.assetName}
                        </div>
                      </div>
                      <div className='text-right'>
                        <div className='text-green-600'>
                          {formatCurrency(holding.totalGainLoss)}
                        </div>
                        <div className='text-sm text-green-600'>
                          {formatPercent(holding.percentGainLoss)}
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Bottom Performers</CardTitle>
            </CardHeader>
            <CardContent>
              <div className='space-y-3'>
                {holdings
                  .sort((a, b) => a.percentGainLoss - b.percentGainLoss)
                  .slice(0, 5)
                  .map((holding, idx) => (
                    <div
                      key={idx}
                      className='flex items-center justify-between rounded-lg border p-3'
                    >
                      <div>
                        <div className='font-mono'>{holding.ticker}</div>
                        <div className='text-sm text-muted-foreground'>
                          {holding.assetName}
                        </div>
                      </div>
                      <div className='text-right'>
                        <div
                          className={
                            holding.totalGainLoss >= 0
                              ? 'text-green-600'
                              : 'text-red-600'
                          }
                        >
                          {formatCurrency(holding.totalGainLoss)}
                        </div>
                        <div
                          className={`text-sm ${holding.percentGainLoss >= 0 ? 'text-green-600' : 'text-red-600'}`}
                        >
                          {formatPercent(holding.percentGainLoss)}
                        </div>
                      </div>
                    </div>
                  ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
