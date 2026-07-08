import { Search } from 'lucide-react'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface TradeFiltersProps {
  searchQuery: string
  onSearchChange: (value: string) => void
  partyFilter: string
  onPartyFilterChange: (value: string) => void
  tradeTypeFilter: string
  onTradeTypeFilterChange: (value: string) => void
  assetTypeFilter: string
  onAssetTypeFilterChange: (value: string) => void
}

export function TradeFilters({
  searchQuery,
  onSearchChange,
  partyFilter,
  onPartyFilterChange,
  tradeTypeFilter,
  onTradeTypeFilterChange,
  assetTypeFilter,
  onAssetTypeFilterChange,
}: TradeFiltersProps) {
  return (
    <div className='flex flex-col gap-4 sm:flex-row'>
      <div className='relative flex-1'>
        <Search className='absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground' />
        <Input
          placeholder='Search politician or ticker...'
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          className='pl-10'
        />
      </div>

      <Select value={partyFilter} onValueChange={onPartyFilterChange}>
        <SelectTrigger className='w-full sm:w-[160px]'>
          <SelectValue placeholder='Party' />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value='all'>All Parties</SelectItem>
          <SelectItem value='Democrat'>Democrat</SelectItem>
          <SelectItem value='Republican'>Republican</SelectItem>
          <SelectItem value='Independent'>Independent</SelectItem>
        </SelectContent>
      </Select>

      <Select value={tradeTypeFilter} onValueChange={onTradeTypeFilterChange}>
        <SelectTrigger className='w-full sm:w-[160px]'>
          <SelectValue placeholder='Trade Type' />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value='all'>All Trades</SelectItem>
          <SelectItem value='Purchase'>Purchase</SelectItem>
          <SelectItem value='Sale'>Sale</SelectItem>
          <SelectItem value='Exchange'>Exchange</SelectItem>
        </SelectContent>
      </Select>

      <Select value={assetTypeFilter} onValueChange={onAssetTypeFilterChange}>
        <SelectTrigger className='w-full sm:w-[160px]'>
          <SelectValue placeholder='Asset Type' />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value='all'>All Assets</SelectItem>
          <SelectItem value='Stock'>Stock</SelectItem>
          <SelectItem value='ETF'>ETF</SelectItem>
          <SelectItem value='Option'>Option</SelectItem>
          <SelectItem value='Bond'>Bond</SelectItem>
          <SelectItem value='Crypto'>Crypto</SelectItem>
        </SelectContent>
      </Select>
    </div>
  )
}
