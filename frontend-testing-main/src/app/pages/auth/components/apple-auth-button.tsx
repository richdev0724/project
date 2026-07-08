import { Button } from '@/components/custom/button'
import { IconBrandAppleFilled } from '@tabler/icons-react'

export default function AppleAuthButton() {
  return (
    <Button
      size={'lg'}
      className='h-11 w-11 rounded-lg bg-[linear-gradient(135deg,_#32343A_0%,_#2F3137_100%)] shadow-lg transition-all hover:scale-110 hover:shadow-xl active:scale-95'
    >
      <IconBrandAppleFilled className='h-5 w-5 text-[#F5F7F7]' />
    </Button>
  )
}
