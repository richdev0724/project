import { Button } from '@/components/custom/button'
import { useRegisterForm } from '@/hooks/use-register'
import LogoIcon from '@/icons/LogoIcon'
import { Routes } from '@/utilities/routes'
import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { SignUpForm } from './components/sign-up-form'
import { UserAgreementForm } from './components/user-agreement-form'
import { IconArrowLeft } from '@tabler/icons-react'
import AuthLeftPart from './components/auth-left-part'

export default function VerifyEmail() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [isFirstFormValid, setIsFirstFormValid] = useState(false)
  const { form, isLoading, onSubmit, isFormValid } = useRegisterForm({
    token: token as string,
  })
  const [showPolicyPage, setShowPolicyPage] = useState(false)
  const REQUIRED_FIELDS = [
    'first_name',
    'last_name',
    'email',
    'password',
    'confirmPassword',
  ] as const

  const handleHidePolicyPage = () => {
    setShowPolicyPage(false)
  }

  useEffect(() => {
    const areFieldsValid = REQUIRED_FIELDS.every((fieldName) => {
      const fieldState = form.getFieldState(fieldName)
      return fieldState.isDirty && !fieldState.error
    })

    setIsFirstFormValid(areFieldsValid)
  }, [form.formState])

  return (
    <div className='flex min-h-screen bg-gradientRadial bg-cover bg-center'>
      {/* Left side */}
      <AuthLeftPart />

      {/* Create an account */}
      <div className='mx-5 flex w-1/2 items-center justify-end'>
        {!showPolicyPage ? (
          <div className='min-h-[1020px] min-w-[849px] rounded-xl bg-[#1F2026E5] bg-opacity-90 shadow-complex'>
            <div className='mx-[28px] mt-[30px] flex flex-col items-end space-y-1'>
              <div className='flex items-center rounded-sm bg-[#2D2E33] px-1 py-1'>
                <Button
                  variant='ghost'
                  className='rounded-sm px-5 py-[21px] text-base font-medium text-[#F5F7F7] transition-colors'
                  onClick={() => navigate(Routes.SIGN_IN)}
                >
                  Sign In
                </Button>
                <Button
                  variant='ghost'
                  className='rounded-sm bg-[#4A4B50] px-5 py-[21px] text-base font-medium text-[#F5F7F7] transition-colors'
                >
                  Register
                </Button>
              </div>
            </div>
            <div className='flex flex-col items-center space-y-5'>
              <LogoIcon className='h-[76px] w-[76px] fill-primary dark:fill-white' />
              <div className='w-full text-center'>
                <div className='inline-block'>
                  <h2 className='h-[50px] text-[36px] font-bold text-[#F5F7F7]'>
                    Just a few more details...
                  </h2>
                </div>
              </div>
              <div className='min-w-[510px]'>
                <SignUpForm
                  form={form}
                  isFormValid={isFirstFormValid}
                  setShowPolicyPage={setShowPolicyPage}
                />
              </div>
            </div>
          </div>
        ) : (
          <div className='min-h-[1020px] min-w-[849px] rounded-xl bg-[#1F2026E5] bg-opacity-90 shadow-complex'>
            <div className='mx-[28px] mt-[30px] flex items-center justify-between space-y-1'>
              <Button
                size='icon'
                variant='ghost'
                className='text-[#D9D9D9]'
                onClick={handleHidePolicyPage}
              >
                <IconArrowLeft height={34} width={34} fill='#D9D9D9' />
              </Button>
              <div className='flex items-center rounded-sm bg-[#2D2E33] px-1 py-1'>
                <Button
                  variant='ghost'
                  className='rounded-sm px-5 py-[21px] text-base font-medium text-[#F5F7F7] transition-colors'
                  onClick={() => navigate(Routes.SIGN_IN)}
                >
                  Sign In
                </Button>
                <Button
                  variant='ghost'
                  className='rounded-sm bg-[#4A4B50] px-5 py-[21px] text-base font-medium text-[#F5F7F7] transition-colors'
                >
                  Register
                </Button>
              </div>
            </div>
            <div className='flex flex-col items-center space-y-4'>
              <LogoIcon className='h-[76px] w-[76px] fill-primary dark:fill-white' />
              <div className='mb-10 w-full text-center'>
                <div className='flex justify-center gap-2'>
                  <h2 className='h-[50px] text-[36px] font-bold text-[#F5F7F7]'>
                    Now the fun stuff
                  </h2>
                  <span className='mb-2 self-end text-lg font-light text-[#F5F7F7]'>
                    said no one ever
                  </span>
                </div>
              </div>
              <div className='min-w-[470px] pt-[57px]'>
                <UserAgreementForm
                  onSubmit={onSubmit}
                  isLoading={isLoading}
                  isFormValid={isFormValid}
                  form={form}
                  className='w-full'
                />
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
