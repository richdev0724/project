import { SVGProps } from 'react'

const CalendarIcon = (props: SVGProps<SVGSVGElement>) => {
  return (
    <svg
      width='14'
      height='14'
      viewBox='0 0 14 14'
      fill='none'
      xmlns='http://www.w3.org/2000/svg'
      {...props}
    >
      <path
        d='M11 1.5H10V0.5C10 0.224 9.776 0 9.5 0C9.224 0 9 0.224 9 0.5V1.5H5V0.5C5 0.224 4.776 0 4.5 0C4.224 0 4 0.224 4 0.5V1.5H3C1.346 1.5 0 2.846 0 4.5V11C0 12.654 1.346 14 3 14H11C12.654 14 14 12.654 14 11V4.5C14 2.846 12.654 1.5 11 1.5ZM3 2.5H11C12.103 2.5 13 3.397 13 4.5V5H1V4.5C1 3.397 1.897 2.5 3 2.5ZM11 13H3C1.897 13 1 12.103 1 11V6H13V11C13 12.103 12.103 13 11 13Z'
        fill='#646568'
      />
      <rect x='3' y='7.5' width='1.5' height='1.5' fill='#646568' />
      <rect x='6' y='7.5' width='1.5' height='1.5' fill='#646568' />
      <rect x='9' y='7.5' width='1.5' height='1.5' fill='#646568' />
      <rect x='3' y='10' width='1.5' height='1.5' fill='#646568' />
      <rect x='6' y='10' width='1.5' height='1.5' fill='#646568' />
      <rect x='9' y='10' width='1.5' height='1.5' fill='#646568' />
    </svg>
  )
}

export default CalendarIcon
