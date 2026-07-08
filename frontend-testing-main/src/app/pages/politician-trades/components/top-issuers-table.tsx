import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'

const TopIssuersTable: React.FC = () => {
  const symbol = 'USTB'
  return (
    <div className='w-[21%] border-l-[5px] border-t-2 border-[#1F2025] p-0 '>
      <div className='flex items-center justify-between px-2 py-1 text-xs'>
        <h2>Top Issuers</h2>
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
            <TableRow className='h-[52px] border-b-0 border-t-2 border-politicianTradesTable'>
              <TableCell className='w-[56px] !p-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/USTB.svg`}
                  alt='USTB'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                US Treasury Bills
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>74</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $478M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/MSFT.svg`}
                  alt='MSFT'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Microsoft Corp
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>112</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $420M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/GOOGL.svg`}
                  alt='GOOGL'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Alphabet Inc
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>35</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $357M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/AMZN.svg`}
                  alt='AMZN'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Amazon Inc
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>22</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $301M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/AAPL.svg`}
                  alt='AAPL'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Apple Inc
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>61</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $260M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/NVDA.svg`}
                  alt='NVDA'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                NVIDIA Corp
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>48</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $243M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/META.svg`}
                  alt='META'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Meta Platforms
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>15</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $118M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/JMOM.svg`}
                  alt='JMOM'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>JPMorgan</TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>40</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $92M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/BNGE.svg`}
                  alt='BNGE'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Coca Cola Inc
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>102</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $67M
              </TableCell>
            </TableRow>
            <TableRow className='h-[52px] border-b-0 border-t-2'>
              <TableCell className='w-[58px] !py-0 font-medium'>
                <img
                  src={`${import.meta.env.VITE_APP_URL.split('/v1')[0]}/tickers/logos/ADBE.svg`}
                  alt='ADBE'
                  className='rounded-xs h-full w-full object-contain'
                  title={symbol}
                />
              </TableCell>
              <TableCell className='py-0 pl-2 font-medium '>
                Adobe Inc
              </TableCell>
              <TableCell className='py-0 pr-4'>
                <div className='flex flex-col items-center'>
                  <span className='text-center font-bold'>96</span>
                  <span className='text-center'>Trades</span>
                </div>
              </TableCell>
              <TableCell className='py-0 pl-4 font-bold text-yellowHighLightText'>
                $40M
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
export default TopIssuersTable
