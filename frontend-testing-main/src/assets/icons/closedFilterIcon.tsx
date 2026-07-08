import { SVGProps } from 'react'
const ClosedFiltersIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg
    fill='none'
    height={24}
    stroke='currentColor'
    strokeLinecap='round'
    strokeLinejoin='round'
    strokeWidth={2}
    width={24}
    xmlns='http://www.w3.org/2000/svg'
    {...props}
  >
    <path d='M0 0h24v24H0z' stroke='none' />
    <path d='M4 4h16v2.172a2 2 0 0 1-.586 1.414L15 12v7l-6 2v-8.5L4.52 7.572A2 2 0 0 1 4 6.227V4z' />
  </svg>
)
export default ClosedFiltersIcon
