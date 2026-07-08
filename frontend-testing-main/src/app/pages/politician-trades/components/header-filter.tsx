import { MakeSearchDropdown } from '@/components/custom/select-with-search-icon'

const HeaderFilter: React.FC = () => {
  return (
    <div className='flex items-center gap-4'>
      <MakeSearchDropdown placeholder='Search by Name' hasError={false} />
      <MakeSearchDropdown placeholder='Search by Ticker' hasError={false} />
      <MakeSearchDropdown placeholder='Search by State' hasError={false} />
      <MakeSearchDropdown placeholder='Search by Committee' hasError={false} />
      <MakeSearchDropdown placeholder='Search by Market Cap' hasError={false} />
    </div>
  )
}

export default HeaderFilter
