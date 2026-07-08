import LogoIcon from '@/icons/LogoIcon'
import { OtpForm } from './components/otp-form'
import { useLocation, useNavigate } from 'react-router-dom'
// import { Routes } from '@/utilities/routes'
// import { useEffect } from 'react'
import { useForgetPassword } from '@/hooks/use-forget-password'
import Seo from '@/components/seo'
import { Button } from '@/components/custom/button'
import { IconArrowLeft } from '@tabler/icons-react'
import AuthLeftPart from './components/auth-left-part'

export default function Otp() {
  const location = useLocation()
  const navigate = useNavigate()
  const { handleResendOtpEmail } = useForgetPassword()

  return (
    <>
      <Seo title='Verify OTP' />
      <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
        {/* Left side */}
        <AuthLeftPart />

        {/* otp verification form */}
        <div className='mx-5 flex w-1/2 items-center justify-end'>
          <div className='min-h-[1020px] min-w-[849px] rounded-xl bg-[#1F2026E5] bg-opacity-90  shadow-complex'>
            <div className='mx-[33px] mt-[28px] flex flex-col items-start space-y-1'>
              <div className='flex items-center rounded-sm'>
                <Button
                  size='icon'
                  variant='ghost'
                  className='text-[#D9D9D9]'
                  onClick={() => navigate(-1)}
                >
                  <IconArrowLeft height={34} width={34} fill='#D9D9D9' />
                </Button>
              </div>
            </div>
            <div className='flex flex-col items-center space-y-4 pt-[100px]'>
              <LogoIcon className='h-[76px] w-[76px] fill-primary dark:fill-white' />
              <div className='w-full text-center'>
                <div className='inline-block'>
                  <h2 className='h-[50px] text-[42px] font-semibold text-[#F5F7F7]'>
                    Check your email
                  </h2>
                  <span className='float-right text-lg font-light text-[#F5F7F7]'>
                    We sent you a 6-digit code to:
                  </span>
                </div>
              </div>
              <span className='pt-[29px] text-2xl font-medium text-[#F5F7F7]'>
                {location.state?.email || ''}
              </span>
              <div className='min-w-[449px]'>
                <OtpForm className='pt-[29px]' />
              </div>
              <div className='flex items-center justify-center gap-1 pt-[79px]'>
                <span className='text-base font-medium'>
                  Didn't receive the email?
                </span>
                <span
                  className='cursor-pointer text-base font-medium italic text-[#989898] underline'
                  onClick={handleResendOtpEmail}
                >
                  Click here
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
