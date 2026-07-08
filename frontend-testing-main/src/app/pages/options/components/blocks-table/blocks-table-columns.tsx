import { ColumnDef } from '@tanstack/react-table'

import { TopBlockTrades } from '@/hooks/use-top-block-traders'
import { DataTableColumnHeader } from './blocks-table-column-header'

export const columns: ColumnDef<TopBlockTrades>[] = [
  {
    accessorKey: 'price',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='PRICE' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>${row.getValue('price')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'size',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='SIZE' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('size')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'net_premium',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='PREMIUM' />
    ),
    cell: ({ row }) => {
      const value: string = row.getValue('net_premium')
      let numericValue
      if (value.endsWith('M')) {
        numericValue = parseFloat(value) * 1e6
      } else if (value.endsWith('B')) {
        numericValue = parseFloat(value) * 1e9
      } else {
        numericValue = parseFloat(value)
      }
      let colorClass = ''
      if (numericValue > 750e6) {
        colorClass = 'text-amber-400' // Amber color for values over 500M
      } else if (numericValue >= 100e6) {
        colorClass = 'text-sky-400' // Sky color for values between 100M and 499M
      }
      return (
        <div className={`w-[80px] font-medium ${colorClass}`}>${value}</div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'sip_date_friendly',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='DATE' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>
        {row.getValue('sip_date_friendly')}
      </div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
]
