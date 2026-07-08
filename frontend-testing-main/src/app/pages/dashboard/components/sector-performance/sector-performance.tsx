import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
// import { SECTORS } from '@/constants'
import { IconCircleFilled } from '@tabler/icons-react'
import { useSectorPerformance } from '@/hooks/use-sector-performance'
import Loader from '@/components/loader'

const SectorPerformance = () => {
  const { data: sectorData, isLoading } = useSectorPerformance()

  // Fallback data in case of loading or error
  const fallbackData = [
    {
      ticker: 'XLC',
      sector: 'Communication Services',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLY',
      sector: 'Consumer Discretionary',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLP',
      sector: 'Consumer Staples',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLE',
      sector: 'Energy',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLF',
      sector: 'Financials',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLV',
      sector: 'Healthcare',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLI',
      sector: 'Industrials',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLK',
      sector: 'Information Technology',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLB',
      sector: 'Materials',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLRE',
      sector: 'Real Estate',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
    {
      ticker: 'XLU',
      sector: 'Utilities',
      priceChanges: { '1d': 0, '30d': 0, '6m': 0 },
      percentageChanges: { '1d': 0, '30d': 0, '6m': 0 },
    },
  ]

  const displayData = sectorData || fallbackData

  if (isLoading) {
    return (
      <div className='flex h-[200px] items-center justify-center'>
        <Loader />
      </div>
    )
  }

  return (
    <>
      <Table>
        <TableHeader className='bg-transparent'>
          <TableRow className='border-none'>
            <TableHead className='h-5 w-[250px] pb-5 text-xs font-normal'>
              Sectors
            </TableHead>
            <TableHead className='h-5 w-[200px] pb-5 text-center text-xs font-light'>
              Daily Performance
            </TableHead>
            <TableHead className='h-5 w-[200px] pb-5 text-center text-xs font-light'>
              30-Day Performance
            </TableHead>
            <TableHead className='h-5 w-[200px] pb-5 text-center text-xs font-light'>
              6-Month Performance
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {displayData.map((sector: any, index: any) => (
            <TableRow
              className='h-8 items-center justify-center border-none'
              key={index}
            >
              <TableCell className='flex items-center gap-2.5 py-2.5 text-xs font-medium'>
                <span className='min-w-[45px] text-end'>{sector.ticker}</span>
                <IconCircleFilled size={5} />
                <span className='min-w-[160px]'>{sector.sector}</span>
              </TableCell>
              <TableCell className='w-[200px] text-xs font-medium'>
                <div className='flex items-center justify-center gap-2'>
                  <div className='flex h-5 min-w-[45px] items-center justify-end text-end text-xs'>
                    {sector.priceChanges['1d'] < 0
                      ? sector.priceChanges['1d']
                      : '+' + sector.priceChanges['1d']}
                  </div>
                  <div
                    className={`flex h-5 w-[60px] items-center justify-center rounded-[2px] text-xs ${
                      sector.percentageChanges['1d'] < 0
                        ? 'bg-sectorPerformanceDecreaseBgColor text-[#F12617]'
                        : 'bg-sectorPerformanceIncreaseBgColor text-[#28BF94]'
                    }`}
                  >
                    {sector.percentageChanges['1d']}%
                  </div>
                </div>
              </TableCell>
              <TableCell className='w-[200px] text-xs font-medium'>
                <div className='flex items-center justify-center gap-2'>
                  <div className='flex h-5 min-w-[45px] items-center justify-end text-end text-xs'>
                    {sector.priceChanges['30d'] < 0
                      ? sector.priceChanges['30d']
                      : '+' + sector.priceChanges['30d']}
                  </div>
                  <div
                    className={`flex h-5 w-[60px] items-center justify-center rounded-[2px] text-xs ${
                      sector.percentageChanges['30d'] < 0
                        ? 'bg-sectorPerformanceDecreaseBgColor text-[#F12617]'
                        : 'bg-sectorPerformanceIncreaseBgColor text-[#28BF94]'
                    }`}
                  >
                    {sector.percentageChanges['30d']}%
                  </div>
                </div>
              </TableCell>
              <TableCell className='w-[200px] text-xs font-medium'>
                <div className='flex items-center justify-center gap-2'>
                  <div className='flex h-5 min-w-[45px] items-center justify-end text-end text-xs'>
                    {sector.priceChanges['6m'] < 0
                      ? sector.priceChanges['6m']
                      : '+' + sector.priceChanges['6m']}
                  </div>
                  <div
                    className={`flex h-5 w-[60px] items-center justify-center rounded-[2px] text-xs ${
                      sector.percentageChanges['6m'] < 0
                        ? 'bg-sectorPerformanceDecreaseBgColor text-[#F12617]'
                        : 'bg-sectorPerformanceIncreaseBgColor text-[#28BF94]'
                    }`}
                  >
                    {sector.percentageChanges['6m']}%
                  </div>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  )
}

export default SectorPerformance
