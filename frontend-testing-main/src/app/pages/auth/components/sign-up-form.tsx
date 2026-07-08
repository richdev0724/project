import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { InputShared } from '@/components/custom/input-shared'
import { PasswordInput } from '@/components/custom/password-input'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { IconMail } from '@tabler/icons-react'
import { Dispatch, HTMLAttributes, SetStateAction } from 'react'

interface SignUpFormProps extends HTMLAttributes<HTMLDivElement> {
  form: any
  isFormValid: boolean
  setShowPolicyPage: Dispatch<SetStateAction<boolean>>
}

export function SignUpForm({
  form,
  isFormValid,
  setShowPolicyPage,
  ...props
}: SignUpFormProps) {
  return (
    <div className='mt-[10px] grid gap-6' {...props}>
      <Form {...form}>
        <form>
          <div className='grid gap-3'>
            <FormField
              control={form.control}
              name='first_name'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    First Name
                  </FormLabel>
                  <FormControl>
                    <InputShared
                      {...field}
                      className='h-16 rounded-sm border-[#F5F7F7] text-center font-light'
                      icon={<IconMail size={14} />}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='last_name'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Last Name
                  </FormLabel>
                  <FormControl>
                    <InputShared
                      {...field}
                      className='h-16 w-full rounded-sm border-[#F5F7F7] text-center font-light'
                      icon={<IconMail size={14} />}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='email'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Confirm your email address
                  </FormLabel>
                  <FormControl>
                    <InputShared
                      {...field}
                      className='h-16 w-full rounded-sm border-[#F5F7F7] text-center font-light'
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
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Create a password
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      className='h-16 w-full rounded-sm border-[#F5F7F7] text-center font-light'
                      {...field}
                      isPassword={true}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name='confirmPassword'
              render={({ field }) => (
                <FormItem className='space-y-1'>
                  <FormLabel className='flex items-center justify-center text-base font-medium'>
                    Confirm your Password
                  </FormLabel>
                  <FormControl>
                    <PasswordInput
                      className='h-16 w-full rounded-sm border-[#F5F7F7] text-center'
                      {...field}
                      type='text'
                      onChange={(e) => {
                        field.onChange(e)
                      }}
                    />
                  </FormControl>
                  {form.getValues('password') ===
                    form.getValues('confirmPassword') &&
                    form.formState.errors['confirmPassword'] === undefined && (
                      <p className='font-light text-[#F5F7F7]'>
                        Passwords match!
                      </p>
                    )}
                </FormItem>
              )}
            />
          </div>

          {!isFormValid ? (
            <div className='mt-[46px] flex items-center justify-center rounded-sm p-4 text-[21px] font-medium text-[#F5F7F7]'>
              Please fill out all details
            </div>
          ) : (
            <AuthSubmitButton
              type='button'
              className='mt-3 w-full rounded-sm shadow-xl shadow-black/25 transition-colors duration-300'
              variant='gradient'
              size='fullLg'
              onClick={() => setShowPolicyPage(true)}
            >
              Continue
            </AuthSubmitButton>
          )}
        </form>
      </Form>
    </div>
  )
}
