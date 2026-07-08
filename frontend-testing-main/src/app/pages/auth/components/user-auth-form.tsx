import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { InputShared } from '@/components/custom/input-shared'
import { PasswordInput } from '@/components/custom/password-input'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from '@/components/ui/form'
import {
  FORGOT_PASSWORD,
  LOGIN_BUTTON_TEXT,
  RESET_PASSWORD_LINK_TEXT,
} from '@/constants'
import { useLoginForm } from '@/hooks/use-login'
import { cn } from '@/lib/utils'
import { Routes } from '@/utilities/routes'
import { IconMail } from '@tabler/icons-react'
import { HTMLAttributes } from 'react'
import { Link } from 'react-router-dom'

interface UserAuthFormProps extends HTMLAttributes<HTMLDivElement> {}

export function UserAuthForm({ className, ...props }: UserAuthFormProps) {
  /**
   * useLoginForm hook is used to handle login form and its validation.
   */
  const { form, isLoading, onSubmit, isFormValid } = useLoginForm()

  return (
    <div className={cn('grid gap-6', className)} {...props}>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className='grid gap-8'>
            <FormField
              control={form.control}
              name='email'
              render={({ field }) => (
                <FormItem className='space-y-2'>
                  <FormControl>
                    <InputShared
                      placeholder='Email'
                      {...field}
                      icon={<IconMail size={14} />}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='password'
              render={({ field }) => (
                <FormItem className='space-y-2'>
                  <FormControl>
                    <PasswordInput placeholder='Password' {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <AuthSubmitButton
              className='mt-4 h-14 w-full transition-colors duration-300'
              variant={'gradient'}
              loading={isLoading}
              disabled={!isFormValid || isLoading}
            >
              {LOGIN_BUTTON_TEXT}
            </AuthSubmitButton>
            <div className='text-center'>
              <p className='text-[0.75rem] text-muted-foreground'>
                {FORGOT_PASSWORD}{' '}
                <Link
                  className='text-[#0ea5e9] hover:underline'
                  to={Routes.FORGOT_PASSWORD}
                >
                  {RESET_PASSWORD_LINK_TEXT}
                </Link>
              </p>
            </div>
          </div>
        </form>
      </Form>
    </div>
  )
}
