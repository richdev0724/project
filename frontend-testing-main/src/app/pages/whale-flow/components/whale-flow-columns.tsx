import { ColumnDef } from '@tanstack/react-table'
import { DataTableColumnHeader } from '@/components/custom/data-table-column-header'
import { Link } from 'react-router-dom'

// Format number with K, M, B suffixes and return parts separately for coloring
const formatPremium = (
  value: string | number
): { number: string; suffix: string; suffixColor: string } => {
  const num = typeof value === 'string' ? parseFloat(value) : value

  if (isNaN(num)) return { number: '$0', suffix: '', suffixColor: '' }

  if (num >= 1000000000) {
    return {
      number: `$${(num / 1000000000).toFixed(1)}`,
      suffix: 'B',
      suffixColor: 'text-yellow-500', // Gold for billions
    }
  } else if (num >= 1000000) {
    return {
      number: `$${(num / 1000000).toFixed(1)}`,
      suffix: 'M',
      suffixColor: 'text-purple-500', // Purple for millions
    }
  } else if (num >= 1000) {
    return {
      number: `$${(num / 1000).toFixed(1)}`,
      suffix: 'K',
      suffixColor: 'text-green-500', // Green for thousands
    }
  } else {
    return {
      number: `$${num.toFixed(0)}`,
      suffix: '',
      suffixColor: '', // No suffix for small amounts
    }
  }
}

export const columns: ColumnDef<any>[] = [
  {
    accessorKey: 'symbol',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[60px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Ticker' />
      </div>
    ),
    cell: ({ row }) => (
      <div className='flex w-[60px] items-center justify-center'>
        <Link
          className='text-[10px] font-medium text-sky-400 hover:text-sky-500 hover:underline'
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
    accessorKey: 'price',
    size: 60,
    header: ({ column }) => (
      <div className='flex w-[60px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Price' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='w-[60px] items-center justify-center text-center text-[10px] font-normal'>
          ${row.getValue('price')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'size',
    size: 50,
    header: ({ column }) => (
      <div className='flex w-[50px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Size' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='w-[50px] items-center justify-center text-center text-[10px] font-normal'>
          {row.getValue('size')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'total_premium',
    size: 80,
    header: ({ column }) => (
      <div className='flex w-[80px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Premium' />
      </div>
    ),
    cell: ({ row }) => {
      const premium = formatPremium(row.getValue('total_premium'))
      return (
        <div className='w-[80px] items-center justify-center text-center text-[10px] font-normal'>
          <span>{premium.number}</span>
          <span className={premium.suffixColor}>{premium.suffix}</span>
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'market_session',
    size: 70,
    header: ({ column }) => (
      <div className='flex w-[70px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Session' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='w-[70px] items-center justify-center text-center text-[10px] font-normal'>
          {row.getValue('market_session')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
  {
    accessorKey: 'trade_date',
    size: 120,
    header: ({ column }) => (
      <div className='flex w-[120px] items-center justify-center text-[10px]'>
        <DataTableColumnHeader column={column} title='Timestamp' />
      </div>
    ),
    cell: ({ row }) => {
      return (
        <div className='w-[120px] items-center justify-center text-center text-[10px] font-normal'>
          {row.getValue('trade_date')}
        </div>
      )
    },
    enableSorting: true,
    enableHiding: false,
  },
]
