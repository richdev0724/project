import { SVGProps } from 'react'

const PlayIcon = (props: SVGProps<SVGSVGElement>) => {
  return (
    <svg
      width={18}
      height={18}
      viewBox='0 0 20 20'
      fill='none'
      xmlns='http://www.w3.org/2000/svg'
      {...props}
    >
      <path
        d='M10 20C4.477 20 0 15.523 0 10C0 4.477 4.477 0 10 0C15.523 0 20 4.477 20 10C20 15.523 15.523 20 10 20ZM8 14.5L14 10L8 5.5V14.5Z'
        fill='#00FF00'
      />
    </svg>
  )
}

export default PlayIcon
