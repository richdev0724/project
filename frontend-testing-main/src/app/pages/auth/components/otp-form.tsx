import { authService } from '@/api'
import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { PinInput, PinInputField } from '@/components/custom/pin-input'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { OtpVerifyRequest } from '@/models/user.model'
import { OtpRequest, otpSchema } from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { HTMLAttributes, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation, useNavigate } from 'react-router-dom'
import { z } from 'zod'

interface OtpFormProps extends HTMLAttributes<HTMLDivElement> {}

/**
 * Form to enter OTP code.
 *
 * This component will render a form with an input to enter the OTP code.
 * The form will be validated using the OTP schema.
 * When the form is submitted, the component will call the `verifyOtp` mutation
 * and navigate to the OTP route if the mutation is successful.
 * If the mutation fails, the component will show an error message.
 *
 * @prop {HTMLAttributes<HTMLDivElement>} className - The class name to be applied to the component.
 * @prop {HTMLAttributes<HTMLDivElement>} [props] - Any other props to be applied to the component.
 */
export function OtpForm({ className, ...props }: OtpFormProps) {
  const navigate = useNavigate()
  const location = useLocation()
  const [isLoading, setIsLoading] = useState(false)
  const [disabledBtn, setDisabledBtn] = useState(true)

  const form = useForm<z.infer<typeof otpSchema>>({
    resolver: zodResolver(otpSchema),
    defaultValues: { otp: '' },
  })

  const mutation = useMutation({
    mutationFn: async (data: OtpVerifyRequest) => authService.verifyOtp(data),
    onSuccess: (response) => {
      setIsLoading(false)
      navigate(`/reset-password/${response.data.resetPasswordToken}`)
    },
    onError: () => {
      setIsLoading(false)
    },
  })

  function onSubmit(data: OtpRequest) {
    setIsLoading(true)
    mutation.mutateAsync({
      otp: data.otp,
      email: location.state.email,
    })
  }

  return (
    <div className={cn('grid gap-6', className)} {...props}>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className='grid gap-10'>
            <FormField
              control={form.control}
              name='otp'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center text-base'>
                    Enter code
                  </FormLabel>
                  <FormControl>
                    <PinInput
                      {...field}
                      className='flex h-20 justify-between'
                      onComplete={() => setDisabledBtn(false)}
                      type='numeric'
                      placeholder=''
                      onIncomplete={() => setDisabledBtn(true)}
                    >
                      {Array.from({ length: 6 }, (_, i) => {
                        return (
                          <PinInputField
                            key={i}
                            component={Input}
                            className={`${form.getFieldState('otp').invalid ? 'border-red-500' : 'border-[#F5F7F7]'} h-20 w-14 rounded-sm px-2 text-[46px] font-bold text-[#FFFFFF]`}
                          />
                        )
                      })}
                    </PinInput>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            {form.formState.isValid ? (
              <AuthSubmitButton
                className='mt-3 w-full rounded-sm shadow-xl shadow-black/25 transition-colors duration-300'
                variant={'gradient'}
                size={'fullLg'}
                loading={isLoading}
                disabled={disabledBtn}
              >
                Continue
              </AuthSubmitButton>
            ) : (
              <div className='mt-3 inline-flex h-[65px] items-center justify-center whitespace-nowrap rounded-md border border-[#F5F7F7] text-xl font-medium'>
                <span>Please enter code</span>
              </div>
            )}
          </div>
        </form>
      </Form>
    </div>
  )
}
