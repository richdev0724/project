import { IconLoader3 } from '@tabler/icons-react'

export default function Loader() {
  return (
    <div className='flex h-full w-full items-center justify-center'>
      <IconLoader3 className='animate-spin' size={32} />
      <span className='sr-only'>loading</span>
    </div>
  )
}
