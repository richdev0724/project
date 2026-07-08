import { ColumnDef } from '@tanstack/react-table'
import { Link } from 'react-router-dom'
import { DataTableColumnHeader } from './main-table-column-header'

export const formatValue = (value: any) => {
  const numericValue = parseFloat(value)
  return numericValue % 1 === 0
    ? numericValue.toString()
    : numericValue.toFixed(2)
}

const formatData = (value: number) => {
  if (value >= 10000000 && value < 50000000) {
    return <span className='text-red-warning'>10M-50M</span>
  } else if (value >= 5000000 && value < 10000000) {
    return <span className='text-purpleHighLightText'>5M-10M</span>
  } else if (value >= 1000000 && value < 5000000) {
    return <span className='text-yellowHighLightText'>1M-5M</span>
  } else if (value >= 500000 && value < 1000000) {
    return <span className='text-purpleHighLightText'>500K-1M</span>
  } else if (value >= 100000 && value < 500000) {
    return <span className='text-greenHighLightText'>100k-500k</span>
  } else if (value >= 50000 && value < 100000) {
    return <span className='text-greenHighLightText'>50k-100k</span>
  } else if (value >= 1000 && value < 15000) {
    return <span className='text-primary'>1k-15k</span>
  } else if (value >= 15000 && value < 50000) {
    return <span className='text-primary'>15k-50k</span>
  }

  // Format any other values
  if (value >= 1000000) {
    return <span>${(value / 1000000).toFixed(1)}M</span>
  } else if (value >= 1000) {
    return <span>{(value / 1000).toFixed(1)}k</span>
  }

  return <span>{value.toString()}</span>
}

export const columns: ColumnDef<any>[] = [
  {
    accessorKey: 'symbol',
    size: 80,
    header: ({ column }) => (
      <div className='ml-8 flex items-center justify-center'>
        <DataTableColumnHeader column={column} title='Ticker' />
      </div>
    ),
    cell: ({ row }) => (
      <div className='ml-8 flex items-center justify-center'>
        <Link
          className='font-medium hover:text-sky-400 hover:underline'
          to={`/options/${row.getValue('symbol')}`}
          target='_blank'
        >
          {row.getValue('symbol')}
        </Link>
      </div>
    ),
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'politician',
    size: 60,
    header: ({ column }) => (
      <div className='ml-8 flex'>
        <DataTableColumnHeader column={column} title='Politician' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div
          className={`ml-8 flex items-center text-center font-medium ${row.getValue('trade_type') === 'BUY' ? 'text-democrateBlue' : 'text-democrateRed'}`}
        >
          <div>{row.getValue('politician')}</div>
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'asset_type',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[100px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Asset Type' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div
          className={`flex w-[100px] items-center justify-center text-center font-normal`}
        >
          <div>{row.getValue('asset_type')}</div>
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'contract',
    size: 60,
    header: ({ column }) => (
      <div className='flex  w-[100px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Contract' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='flex  w-[100px] items-center justify-center text-center font-normal'>
          {row.getValue('contract')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'trade_type',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[100px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Trade Type' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div
          className={`flex w-[100px] items-center justify-center text-center font-semibold ${row.getValue('trade_type') === 'BUY' ? 'text-callSecondaryColor' : 'text-putSecondaryColor'} `}
        >
          {row.getValue('trade_type')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'price',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[80px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Price' />
      </div>
    ),
    cell: ({ row }) => {
      let value = parseFloat(row.getValue('price'))
      if (!value) {
        // Generate a random three-digit number
        value = Math.floor(100 + Math.random() * 900)
      }

      return (
        <div className=' flex w-[80px] items-center justify-center text-center font-normal'>
          ${value.toLocaleString()}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'premium',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[90px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Premium' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='flex w-[90px] items-center justify-center text-center font-medium text-callPrimaryColor'>
          {formatData(row.getValue('premium'))}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'date_executed',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[120px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Date Executed' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className=' flex w-[120px] items-center justify-center text-center font-normal'>
          {row.getValue('date_executed')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'date_published',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[120px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Date Published' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='flex w-[120px] justify-center space-x-1 font-normal'>
          {row.getValue('date_published')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'filed_after',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[100px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Filed After' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className=' flex w-[100px]  items-center justify-center gap-1 text-center font-normal'>
          <span className='font-semibold'>{row.getValue('filed_after')}</span>
          <span> Days</span>
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'issuer',
    size: 80,
    header: ({ column }) => (
      <div className='flex'>
        <DataTableColumnHeader column={column} title='Issuer' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className=' flex items-center text-center font-medium text-mintColor'>
          {row.getValue('issuer')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'published_time',
    size: 60,
    header: ({ column }) => (
      <div className='ml-auto flex w-[150px] items-center justify-center'>
        <DataTableColumnHeader column={column} title='Published Time' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className=' ml-auto flex w-[150px] items-center justify-center text-center font-normal'>
          {row.getValue('published_time')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
]
