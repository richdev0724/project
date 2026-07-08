import { ColumnDef } from '@tanstack/react-table'
import { DataTableColumnHeader } from './highest-oi-column-header'
import PercentageCapsule from './charts/percentage-capsule'

export const columns: ColumnDef<any>[] = [
  {
    accessorKey: 'date',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Date'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('date')}
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
          title='Volume'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
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
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('open_in')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'plus_minus',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='+/-'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('plus_minus')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'open',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Open'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('open')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'close',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Close'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('close')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'avg',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Avg'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('avg')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'total_prems',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Total Prems'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('total_prems')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'multi_percent',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Multi %'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('multi_percent')}%
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'floor',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Floor'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('floor')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'sweep',
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Sweep'
        />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex items-center justify-start text-[0.64rem]'>
        {row.getValue('sweep')}
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'bid_ask',
    header: ({ column }) => (
      <div className='flex min-w-[90px] pr-5'>
        <DataTableColumnHeader
          className='text-[0.64rem] !font-medium text-[#8B8B8B]'
          column={column}
          title='Bid/Ask'
        />
      </div>
    ),
    cell: () => (
      <div className='flex items-center justify-start pr-5 text-[0.64rem]'>
        <PercentageCapsule />
      </div>
    ),
    enableSorting: false,
    enableHiding: false,
  },
]
