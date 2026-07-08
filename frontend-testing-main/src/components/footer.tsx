import { FOOTER_HEIGHT } from '@/constants'
import { Fragment } from 'react'

const Footer = () => {
  return (
    <Fragment>
      <footer
        style={{ height: `${FOOTER_HEIGHT}px` }}
        className={
          'footer text-defaultsize bg-bodybg z-10 mt-auto w-full py-2 text-center font-inter font-normal leading-normal shadow-[0_0_0.4rem_rgba(0,0,0,0.1)]'
        }
      >
        <div className='container flex flex-row items-center justify-center space-x-4 text-center'>
          <span className='text-defaultsize text-[#8b8b8b] text-[0.813]'>
            © 2024 MarketMono LLC | NFA
          </span>
        </div>
      </footer>
    </Fragment>
  )
}

export default Footer
