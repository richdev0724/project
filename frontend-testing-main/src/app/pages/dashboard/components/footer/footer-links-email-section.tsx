import { Button } from '@/components/custom/button'
import NewsLetterInput from './news-letter-input'

const FooterLinksEmailSection = () => {
  return (
    <div className='font-sm mb-14 flex items-center justify-between text-sm'>
      <div className='flex gap-10'>
        <span className='cursor-pointer'>Contact Us</span> |{' '}
        <span className='cursor-pointer'>FAQ</span> |{' '}
        <span className='cursor-pointer'>Glossary</span> |
        <span className='cursor-pointer'>Information</span>
      </div>
      <div className='flex flex-col gap-2 text-sm'>
        <span>5-minute market insights, delivered weekly.</span>
        <div className='flex h-12 w-[372px] items-center justify-center rounded-sm border border-[#F5F7F7]'>
          <NewsLetterInput className='border-none text-[17px] font-light' />
          <Button className='mx-1 h-[35px] rounded-sm border border-[#FFFFFF] bg-[#D9D9D912] px-7 text-xs font-light text-[#FFFFFF]'>
            Submit
          </Button>
        </div>
      </div>
    </div>
  )
}

export default FooterLinksEmailSection
