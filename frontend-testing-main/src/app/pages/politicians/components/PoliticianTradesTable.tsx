import { useState } from 'react'
import {
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  TrendingUp,
  TrendingDown,
  X,
} from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { motion, AnimatePresence } from 'framer-motion'

export interface PoliticianTrade {
  id: string
  politician: string
  party: 'Democrat' | 'Republican' | 'Independent'
  position: string
  tradeType: 'Purchase' | 'Sale' | 'Exchange'
  ticker: string
  assetName: string
  assetType: 'Stock' | 'Bond' | 'ETF' | 'Option' | 'Crypto'
  amount: string
  amountMin: number
  amountMax: number
  tradeDate: string
  disclosureDate: string
  owner: string
}

type SortField = 'politician' | 'tradeDate' | 'disclosureDate' | 'amount'
type SortDirection = 'asc' | 'desc'

interface PoliticianTradesTableProps {
  trades: PoliticianTrade[]
  onPoliticianClick?: (politicianName: string) => void
}

export function PoliticianTradesTable({
  trades,
  onPoliticianClick,
}: PoliticianTradesTableProps) {
  const [sortField, setSortField] = useState<SortField>('disclosureDate')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')
  const [selectedAsset, setSelectedAsset] = useState<PoliticianTrade | null>(
    null
  )

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDirection('desc')
    }
  }

  const sortedTrades = [...trades].sort((a, b) => {
    let comparison = 0

    switch (sortField) {
      case 'politician':
        comparison = a.politician.localeCompare(b.politician)
        break
      case 'tradeDate':
        comparison =
          new Date(a.tradeDate).getTime() - new Date(b.tradeDate).getTime()
        break
      case 'disclosureDate':
        comparison =
          new Date(a.disclosureDate).getTime() -
          new Date(b.disclosureDate).getTime()
        break
      case 'amount':
        comparison = a.amountMin - b.amountMin
        break
    }

    return sortDirection === 'asc' ? comparison : -comparison
  })

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field)
      return <ArrowUpDown className='ml-2 h-4 w-4 opacity-40' />
    return sortDirection === 'asc' ? (
      <ArrowUp className='ml-2 h-4 w-4' />
    ) : (
      <ArrowDown className='ml-2 h-4 w-4' />
    )
  }

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

  const getTradeTypeColor = (tradeType: string) => {
    switch (tradeType) {
      case 'Purchase':
        return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
      case 'Sale':
        return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300'
      case 'Exchange':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300'
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300'
    }
  }

  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    })
  }

  return (
    <div className='rounded-lg border bg-card'>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className='w-[220px] pl-6'>
              <button
                onClick={() => handleSort('politician')}
                className='flex items-center hover:text-foreground'
              >
                Politician
                <SortIcon field='politician' />
              </button>
            </TableHead>
            <TableHead className='w-[80px]'>Party</TableHead>
            <TableHead className='w-[110px]'>Trade</TableHead>
            <TableHead className='w-[90px]'>Ticker</TableHead>
            <TableHead className='w-[200px]'>Asset</TableHead>
            <TableHead className='w-[130px]'>
              <button
                onClick={() => handleSort('amount')}
                className='flex items-center hover:text-foreground'
              >
                Amount
                <SortIcon field='amount' />
              </button>
            </TableHead>
            <TableHead className='w-[120px]'>
              <button
                onClick={() => handleSort('tradeDate')}
                className='flex items-center hover:text-foreground'
              >
                Trade Date
                <SortIcon field='tradeDate' />
              </button>
            </TableHead>
            <TableHead className='w-[120px]'>
              <button
                onClick={() => handleSort('disclosureDate')}
                className='flex items-center hover:text-foreground'
              >
                Filed
                <SortIcon field='disclosureDate' />
              </button>
            </TableHead>
            <TableHead className='w-[100px]'>Owner</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sortedTrades.map((trade) => (
            <TableRow key={trade.id}>
              <TableCell className='pl-6'>
                <div>
                  <button
                    onClick={() => onPoliticianClick?.(trade.politician)}
                    className='text-left text-foreground hover:text-primary hover:underline'
                  >
                    {trade.politician}
                  </button>
                  <div className='text-sm text-muted-foreground'>
                    {trade.position}
                  </div>
                </div>
              </TableCell>
              <TableCell>
                <Badge variant='outline' className={getPartyColor(trade.party)}>
                  {trade.party === 'Democrat'
                    ? 'D'
                    : trade.party === 'Republican'
                      ? 'R'
                      : 'I'}
                </Badge>
              </TableCell>
              <TableCell>
                <div className='flex items-center gap-1.5'>
                  {trade.tradeType === 'Purchase' ? (
                    <TrendingUp className='h-4 w-4 text-green-600' />
                  ) : (
                    <TrendingDown className='h-4 w-4 text-orange-600' />
                  )}
                  <Badge
                    variant='outline'
                    className={getTradeTypeColor(trade.tradeType)}
                  >
                    {trade.tradeType}
                  </Badge>
                </div>
              </TableCell>
              <TableCell>
                <span className='font-mono'>{trade.ticker}</span>
              </TableCell>
              <TableCell>
                <button
                  onClick={() => setSelectedAsset(trade)}
                  className='block max-w-[200px] cursor-pointer truncate text-left text-foreground hover:text-primary hover:underline'
                  title='Click for details'
                >
                  {trade.assetName}
                </button>
              </TableCell>
              <TableCell>
                <div className='text-foreground'>{trade.amount}</div>
              </TableCell>
              <TableCell>
                <div className='text-muted-foreground'>
                  {formatDate(trade.tradeDate)}
                </div>
              </TableCell>
              <TableCell>
                <div className='text-muted-foreground'>
                  {formatDate(trade.disclosureDate)}
                </div>
              </TableCell>
              <TableCell>
                <div className='text-sm text-muted-foreground'>
                  {trade.owner}
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {/* Asset Details Modal */}
      <AnimatePresence>
        {selectedAsset && (
          <>
            {/* Backdrop */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setSelectedAsset(null)}
              className='fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm'
            >
              {/* Modal */}
              <motion.div
                initial={{ scale: 0.95, opacity: 0, y: 20 }}
                animate={{ scale: 1, opacity: 1, y: 0 }}
                exit={{ scale: 0.95, opacity: 0, y: 20 }}
                transition={{ duration: 0.2 }}
                onClick={(e) => e.stopPropagation()}
                className='relative w-full max-w-md rounded-xl border border-[#2A2D35] bg-gradient-to-br from-[#1a1f2e] to-[#151921] p-6 shadow-2xl'
              >
                {/* Close Button */}
                <button
                  onClick={() => setSelectedAsset(null)}
                  className='absolute right-4 top-4 text-muted-foreground transition-colors hover:text-foreground'
                >
                  <X className='h-5 w-5' />
                </button>

                {/* Header */}
                <div className='mb-6'>
                  <h3 className='mb-1 text-xl font-semibold text-white'>
                    Asset Details
                  </h3>
                  <p className='text-sm text-muted-foreground'>
                    Trade information
                  </p>
                </div>

                {/* Content */}
                <div className='space-y-4'>
                  {/* Asset Name */}
                  <div>
                    <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                      Asset Name
                    </label>
                    <p className='text-base text-white'>
                      {selectedAsset.assetName}
                    </p>
                  </div>

                  {/* Asset Type & Ticker */}
                  <div className='grid grid-cols-2 gap-4'>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Type
                      </label>
                      <Badge variant='outline' className='mt-1'>
                        {selectedAsset.assetType}
                      </Badge>
                    </div>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Ticker
                      </label>
                      <p className='font-mono text-base text-white'>
                        {selectedAsset.ticker}
                      </p>
                    </div>
                  </div>

                  {/* Trade Info */}
                  <div className='grid grid-cols-2 gap-4'>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Trade Type
                      </label>
                      <Badge
                        variant='outline'
                        className={`mt-1 ${getTradeTypeColor(selectedAsset.tradeType)}`}
                      >
                        {selectedAsset.tradeType}
                      </Badge>
                    </div>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Amount
                      </label>
                      <p className='text-base text-white'>
                        {selectedAsset.amount}
                      </p>
                    </div>
                  </div>

                  {/* Dates */}
                  <div className='grid grid-cols-2 gap-4'>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Trade Date
                      </label>
                      <p className='text-sm text-white'>
                        {formatDate(selectedAsset.tradeDate)}
                      </p>
                    </div>
                    <div>
                      <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                        Filed Date
                      </label>
                      <p className='text-sm text-white'>
                        {formatDate(selectedAsset.disclosureDate)}
                      </p>
                    </div>
                  </div>

                  {/* Owner */}
                  <div>
                    <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                      Owner
                    </label>
                    <p className='text-sm text-white'>{selectedAsset.owner}</p>
                  </div>

                  {/* Politician */}
                  <div className='border-t border-[#2A2D35] pt-4'>
                    <label className='mb-1 block text-xs uppercase tracking-wide text-muted-foreground'>
                      Politician
                    </label>
                    <p className='text-base text-white'>
                      {selectedAsset.politician}
                    </p>
                    <p className='mt-0.5 text-sm text-muted-foreground'>
                      {selectedAsset.position}
                    </p>
                  </div>
                </div>
              </motion.div>
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  )
}
