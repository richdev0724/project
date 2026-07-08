import { Button } from '@/components/custom/button'
import { IconBrandX } from '@tabler/icons-react'

export default function XAuthButton() {
  const baseURL = import.meta.env.VITE_BASE_URL
  const handleXLoginClick = () => {
    window.open(`${baseURL}/auth/twitter`, '_self')
  }

  return (
    <Button
      type='button'
      size={'lg'}
      className='h-11 w-11 rounded-lg bg-[linear-gradient(135deg,_#32343A_0%,_#2F3137_100%)] shadow-lg transition-all hover:scale-110 hover:shadow-xl active:scale-95'
      onClick={handleXLoginClick}
    >
      <IconBrandX className='h-5 w-5 text-[#F5F7F7]' />
    </Button>
  )
}
