import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { InputShared } from '@/components/custom/input-shared'
import { PasswordInput } from '@/components/custom/password-input'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form'
import { MESSAGE_CONSTANTS } from '@/constants'
import { useLoginForm } from '@/hooks/use-login'
import { Routes } from '@/utilities/routes'
import { IconMail } from '@tabler/icons-react'
import { HTMLAttributes } from 'react'
import { useNavigate } from 'react-router-dom'

interface SignUpFormProps extends HTMLAttributes<HTMLDivElement> {
  onForgotPassword?: () => void
}

export function SignInForm({ onForgotPassword, ...props }: SignUpFormProps) {
  const { form, isLoading, onSubmit, isFormValid, passwordRetriesCount } =
    useLoginForm()
  const navigate = useNavigate()
  const handleResetButtonClick = () => {
    if (onForgotPassword) {
      onForgotPassword()
    } else {
      navigate(Routes.FORGOT_PASSWORD)
    }
  }
  return (
    <div className='mx-auto grid w-full max-w-sm gap-4' {...props}>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className='grid gap-4'>
            <FormField
              control={form.control}
              name='email'
              render={({ field }) => (
                <FormItem className='space-y-2'>
                  <FormLabel className='text-sm font-medium !text-[#F5F7F7] text-[#F5F7F7]'>
                    Email Address
                  </FormLabel>
                  <FormControl>
                    <InputShared
                      {...field}
                      className='h-11 w-full rounded-lg border-[#F5F7F7]/20 bg-[#2D2E33]/50 transition-colors focus:border-primary'
                      icon={<IconMail size={16} />}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='password'
              render={({ field }) => (
                <FormItem className='space-y-2'>
                  <FormLabel className='text-sm font-medium !text-[#F5F7F7] text-[#F5F7F7]'>
                    Password
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      className='h-11 w-full rounded-lg border-[#F5F7F7]/20 bg-[#2D2E33]/50 transition-colors focus:border-primary'
                      {...field}
                    />
                  </FormControl>
                  {passwordRetriesCount > 0 && passwordRetriesCount <= 5 && (
                    <div className='flex items-center justify-between pt-1'>
                      <p className='text-xs font-light italic text-[#F5F7F7]/70'>
                        {MESSAGE_CONSTANTS[passwordRetriesCount - 1].message}
                      </p>
                      <p className='text-xs font-medium text-validationError'>
                        {
                          MESSAGE_CONSTANTS[passwordRetriesCount - 1]
                            .retryCountMessage
                        }
                      </p>
                    </div>
                  )}
                </FormItem>
              )}
            />
          </div>
          <div className={`${passwordRetriesCount > 0 ? 'mt-4' : 'mt-6'}`}>
            {passwordRetriesCount > 4 ? (
              <AuthSubmitButton
                type='button'
                className='w-full rounded-lg shadow-lg transition-opacity duration-500 hover:shadow-xl'
                style={{
                  opacity: isFormValid && !isLoading ? 1 : 0.5,
                }}
                variant='gradient'
                size='fullLg'
                loading={isLoading}
                disabled={!isFormValid || isLoading}
                onClick={handleResetButtonClick}
              >
                Reset Password
              </AuthSubmitButton>
            ) : (
              <AuthSubmitButton
                type='submit'
                className='w-full rounded-lg shadow-lg transition-opacity duration-500 hover:shadow-xl'
                style={{
                  opacity: isFormValid && !isLoading ? 1 : 0.5,
                }}
                variant='gradient'
                size='fullLg'
                loading={isLoading}
                disabled={!isFormValid || isLoading}
              >
                Login
              </AuthSubmitButton>
            )}
          </div>
        </form>
      </Form>
    </div>
  )
}
