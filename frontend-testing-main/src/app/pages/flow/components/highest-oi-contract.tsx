import { ColumnDef } from '@tanstack/react-table'
import { DataTableColumnHeader } from './highest-oi-column-header'
import PercentageCapsule from './charts/percentage-capsule'

export const highestOIcolumns: ColumnDef<any>[] = [
  {
    accessorKey: 'contract',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Contract'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-center text-[0.64rem]'>
        {row.getValue('contract')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'premium',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Premium'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-center text-[0.64rem]'>
        {row.getValue('premium')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'volume',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Value'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-center text-[0.64rem]'>
        {row.getValue('volume')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'open_in',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Open In.'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-center text-[0.64rem]'>
        {row.getValue('open_in')}
      </div>
    ),
    enableSorting: false,
  },
  {
    accessorKey: 'chain',
    header: ({ column }) => (
      <div className='flex min-w-[90px] pr-5'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Chain'
        />
      </div>
    ),
    cell: () => (
      <div className='flex items-center justify-start pr-5 text-[0.64rem]'>
        <PercentageCapsule />
      </div>
    ),
    enableSorting: false,
  },
]
