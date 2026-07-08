import { SVGProps } from 'react'

const PlayIconTickerOverview = (props: SVGProps<SVGSVGElement>) => {
  return (
    <div className='relative -top-[12px]'>
      <svg
        width='24'
        height='24'
        viewBox='0 0 24 24'
        fill='none'
        xmlns='http://www.w3.org/2000/svg'
        {...props}
        style={{ position: 'absolute' }}
      >
        <circle cx='12' cy='12' r='12' fill='#28BF94' />
      </svg>
      <svg
        width='12'
        height='13'
        viewBox='0 0 12 13'
        fill='none'
        xmlns='http://www.w3.org/2000/svg'
        style={{ position: 'absolute', top: '5px', left: '8px' }}
      >
        <path
          d='M12 6.49519L0.749999 12.9904L0.75 -4.91753e-07L12 6.49519Z'
          fill='#16181D'
        />
      </svg>
    </div>
  )
}

export default PlayIconTickerOverview
