import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import { Link } from 'react-router-dom'

export default function TopLosersTable({ dataMarketLosers }: any) {
  return (
    <>
      <Table className='table min-w-full whitespace-nowrap p-2'>
        <TableBody>
          {dataMarketLosers?.slice(0, 16).map((item: any) => (
            <TableRow key={item.ticker} className=' border-t-2'>
              <TableCell className='flex flex-row items-center pl-10'>
                <Link
                  to={`/options/${item.ticker}`}
                  className='mr-2 font-medium hover:text-sky-400 hover:underline'
                >
                  {item.ticker}
                </Link>
              </TableCell>
              <TableCell>${item.day.c}</TableCell>
              <TableCell className=' text-red-500'>
                {item.todaysChangePerc.toFixed(2)}%
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  )
}
