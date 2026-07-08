import { ColumnDef } from '@tanstack/react-table'

import { RecentOpeningData } from '@/hooks/use-recent-opening'
import { DataTableColumnHeader } from './recent-opening-trades-table-column-header'

export const columns: ColumnDef<RecentOpeningData>[] = [
  {
    accessorKey: 'strike',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='STRIKE' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>${row.getValue('strike')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'expiration',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='EXPIRATION' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('expiration')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'bought',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='BOUGHT' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('bought')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'sold',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='SOLD' />
    ),
    cell: ({ row }) => (
      <div className='w-[80px] font-medium'>{row.getValue('sold')}</div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'type',
    header: ({ column }) => (
      <DataTableColumnHeader column={column} title='C / P' />
    ),
    cell: ({ row }) => {
      const type = row.getValue('type')
      if (type === 'call')
        return <div className='w-[80px] font-medium text-green-500'>Call</div>
      if (type === 'put')
        return <div className='w-[80px] font-medium text-red-500'>Put</div>
    },
    enableSorting: true,
    enableHiding: false,
  },
]
