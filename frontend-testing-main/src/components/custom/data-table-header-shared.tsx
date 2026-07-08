import { FC, ReactNode } from 'react'

type Props = {
  title: string
  areFiltersVisible: boolean
  toggleFiltersVisibility?: () => void
  startLoadingData?: () => void
  stopLoadingData?: () => void
  hasWebsocket?: boolean
  iconElement?: ReactNode
  paginationElement?: ReactNode
}

/**
 * DataTableHeaderShared is a functional component that renders a header
 * for a data table. It displays a title and an optional icon element.
 *
 * @param {Object} props - The properties object.
 * @param {string} props.title - The title to be displayed in the header.
 * @param {ReactNode} [props.iconElement] - Optional icon element to be displayed next to the title.
 */

const DataTableHeaderShared: FC<Props> = ({ title, iconElement }: Props) => {
  return (
    <>
      <div className='flex items-center p-5'>
        <h1 className='text-xs font-semibold tracking-tight text-headerPrimary'>
          {title}
        </h1>
        <div className='flex items-center'> {iconElement}</div>
      </div>
    </>
  )
}

export default DataTableHeaderShared
