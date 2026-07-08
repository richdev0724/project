import LogoIcon from '@/icons/LogoIcon'
import { ResetPasswordForm } from './components/reset-password-form'
import AuthLeftPart from './components/auth-left-part'

export default function ResetPassword() {
  return (
    <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
      {/* Left side */}
      <AuthLeftPart />

      {/* reset password form */}
      <div className='mx-5 flex w-1/2 items-center justify-end'>
        <div className='min-h-[1020px] min-w-[849px] rounded-xl bg-[#1F2026E5] bg-opacity-90 pt-[171px] shadow-complex'>
          <div className='flex flex-col items-center space-y-4'>
            <LogoIcon className='h-[76px] w-[76px] fill-primary dark:fill-white' />
            <div className='w-full text-center'>
              <div className='inline-block'>
                <h2 className='h-[50px] text-[42px] font-semibold text-[#F5F7F7]'>
                  Create a new password
                </h2>
                <span className='float-right text-lg font-light text-[#F5F7F7]'>
                  let’s try this again.
                </span>
              </div>
            </div>
            <div className='min-w-[520px]'>
              <ResetPasswordForm />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
