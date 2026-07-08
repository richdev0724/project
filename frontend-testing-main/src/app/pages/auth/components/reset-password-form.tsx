import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { PasswordInput } from '@/components/custom/password-input'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { useResetPasswordForm } from '@/hooks/use-reset-password'
import { HTMLAttributes, useState } from 'react'
import { useParams } from 'react-router-dom'

interface ResetPasswordFormProps extends HTMLAttributes<HTMLDivElement> {}

/**
 * Form to reset password.
 *
 * This component will render a form with two fields: `newPassword` and `confirmNewPassword`.
 * The form will be validated using the `resetPasswordSchema`.
 * When the form is submitted, the component will call the `mutation.mutateAsync` function
 * and navigate to the `Routes.SIGN_IN` route if the mutation is successful.
 * If the mutation fails, the component will show an error message.
 *
 * @prop {HTMLAttributes<HTMLDivElement>} className - The class name to be applied to the component.
 * @prop {HTMLAttributes<HTMLDivElement>} [props] - Any other props to be applied to the component.
 */
export function ResetPasswordForm({
  className,
  ...props
}: ResetPasswordFormProps) {
  const { token } = useParams()
  const [isPasswordMatch, setIsPasswordMatch] = useState<boolean>(false)
  const { form, onSubmit, isFormValid, isLoading } = useResetPasswordForm({
    token,
  })

  const handleIsPasswordMatch = () => {
    const newPassword = form.getValues('newPassword')
    const confirmNewPassword = form.getValues('confirmNewPassword')

    if (newPassword === confirmNewPassword) {
      setIsPasswordMatch(true)
    } else {
      setIsPasswordMatch(false)
    }
  }

  return (
    <div className={`${className} mt-14 grid gap-6`} {...props}>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className='grid gap-6'>
            <FormField
              control={form.control}
              name='newPassword'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Create a password
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      className='h-16 w-full rounded-sm border-[#F5F7F7]'
                      {...field}
                      isPassword={true}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='confirmNewPassword'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Confirm your password
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      className='h-16 w-full rounded-sm border-[#F5F7F7]'
                      {...field}
                      type='text'
                      onChange={(e) => {
                        field.onChange(e)
                        handleIsPasswordMatch()
                      }}
                    />
                  </FormControl>
                  {isPasswordMatch &&
                  form.formState.errors['confirmNewPassword'] === undefined ? (
                    <p className='font-light text-[#F5F7F7]'>
                      Passwords match!
                    </p>
                  ) : (
                    <FormMessage />
                  )}
                </FormItem>
              )}
            />
          </div>
          {!isFormValid ? (
            <div className='mt-24'>
              <div className='flex items-center justify-center rounded-sm p-4 text-[21px] font-medium text-[#F5F7F7]'>
                Please fill out all details
              </div>
            </div>
          ) : (
            <div className='mt-[45px]'>
              <AuthSubmitButton
                type='submit'
                className='w-full rounded-sm shadow-xl shadow-black/25 transition-colors duration-300'
                variant='gradient'
                size='fullLg'
                loading={isLoading}
              >
                Continue
              </AuthSubmitButton>
            </div>
          )}
        </form>
      </Form>
    </div>
  )
}
