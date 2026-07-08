import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { IconCaretUpFilled } from '@tabler/icons-react'

const TopPerformingPoliticianTable: React.FC = () => {
  const symbol = 'USTB'
  return (
    <div className='w-[21%] border-l-[5px] border-t-2 border-[#1F2025] p-0 '>
      <div className='flex items-center justify-between px-2 py-1 text-xs'>
        <h2>Top Performing Politicians</h2>
        <Select>
          <SelectTrigger className='w-[110px] justify-end border-none px-4 text-xs'>
            <SelectValue placeholder='YTD' className='text-xs' />
          </SelectTrigger>
          <SelectContent className='bg-background'>
            <SelectItem value='premium'>Premium</SelectItem>
            <SelectItem value='size'>Size</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <div>
        <Table className='text-[13px]'>
          <TableBody>
            <TableRow className='border-t-1 h-[52px] border-b-0'>
              <TableCell className='w-[56px] !p-0 font-medium'>
                <img
                  src='/assets/politicians-images/image_4.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateBlue'>
                Nancy Pelosi
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>74</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold'>
                <span className='flex items-center text-callSecondaryColor'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 5.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateRed'>
                Mark Alford
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>112</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[56px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 6.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateRed'>
                Jefferson Shreve
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>35</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 7.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateBlue'>
                John Delaney
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>22</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 8.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateRed'>
                David Taylor
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>61</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 9.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateBlue'>
                Gil Cisneros
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>48</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 10.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateBlue'>
                Debbie Dingell
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>15</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 11.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateRed'>
                Max Miller
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>40</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 12.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateBlue'>
                Mikie Sherrill
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>102</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src='/assets/politicians-images/image 13.svg'
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-4 font-medium text-democrateRed'>
                Mitch McConnell
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>96</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-3 text-right text-sm font-bold text-callSecondaryColor'>
                <span className='flex items-center'>
                  <IconCaretUpFilled size={14} />
                  <span>142%</span>
                </span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
export default TopPerformingPoliticianTable
