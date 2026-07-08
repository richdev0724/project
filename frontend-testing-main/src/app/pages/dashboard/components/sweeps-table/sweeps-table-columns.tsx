import { ColumnDef } from '@tanstack/react-table'

import { DataTableColumnHeader } from './sweeps-table-column-header'

import { Link } from 'react-router-dom'

export const columns: ColumnDef<any>[] = [
  {
    accessorKey: 'symbol',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='SYMBOL' />
    ),
    cell: ({ row }) => (
      <Link
        className='w-[80px] font-medium  hover:text-sky-400 hover:underline'
        to={`/options/${row.getValue('symbol')}`}
        target='_blank'
      >
        {row.getValue('symbol')}
      </Link>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'contract',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='CONTRACT' />
    ),
    cell: ({ row }) => (
      <div className='w-[120px] font-medium'>${row.getValue('contract')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
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
    accessorKey: 'volume',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='VOLUME' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('volume')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'premium',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='PREMIUM' />
    ),
    cell: ({ row }) => {
      const value: string = row.getValue('premium')
      let numericValue
      if (value.endsWith('M')) {
        numericValue = parseFloat(value) * 1e6
      } else if (value.endsWith('B')) {
        numericValue = parseFloat(value) * 1e9
      } else {
        numericValue = parseFloat(value)
      }

      let colorClass = ''
      if (numericValue > 50e6) {
        colorClass = 'text-amber-400' // Amber color for values over 500M
      } else if (numericValue > 25e6) {
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
    accessorKey: 'date',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='DATE' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('date')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
]
