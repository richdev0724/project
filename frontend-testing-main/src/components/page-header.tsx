import { IconInfoCircle } from '@tabler/icons-react'
import { Link } from 'react-router-dom'

interface PageHeaderProps {
  currentpage: string
  helpText?: string
}

const PageHeader = (props: PageHeaderProps) => {
  const today = new Date()

  // Define month names to convert month number to name
  const monthNames = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ]

  // Format the date as "Month day, Year"
  const formattedDate = `${monthNames[today.getMonth()]} ${today.getDate()}, ${today.getFullYear()}`

  return (
    <div className='block md:flex'>
      <div className='flex flex-row'>
        <div>
          <h1 className='text-center text-2xl font-semibold text-gray-200 sm:text-left'>
            Welcome To {props.currentpage}
          </h1>
          <p className='my-1 hidden text-lg font-semibold text-gray-500 sm:block'>
            {formattedDate}
          </p>
        </div>
        <div className='group relative ml-2'>
          <Link to={'#!'} className='flex items-center'>
            <IconInfoCircle size={20} className='text-sky-500' />
          </Link>
          <div
            className='absolute left-0 hidden w-max whitespace-nowrap rounded-md bg-sky-500 px-3 py-3 text-sm font-semibold text-white shadow-lg group-hover:block'
            role='tooltip'
          >
            {props.helpText}
          </div>
        </div>
      </div>
    </div>
  )
}

export default PageHeader
