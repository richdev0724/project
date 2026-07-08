import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { InputShared } from '@/components/custom/input-shared'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form'
import { useEmailRegisterForm } from '@/hooks/use-register-email'
import { IconMail } from '@tabler/icons-react'

export function CreateAccountForm({ ...props }) {
  /**
   * useLoginForm hook is used to handle login form and its validation.
   */
  const { form, isLoading, onSubmit, isFormValid } = useEmailRegisterForm()

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
            <AuthSubmitButton
              className='w-full rounded-lg shadow-lg transition-opacity duration-500 hover:shadow-xl'
              style={{
                opacity: isFormValid && !isLoading ? 1 : 0.5,
              }}
              variant={'gradient'}
              size={'fullLg'}
              loading={isLoading}
              disabled={!isFormValid || isLoading}
            >
              Create Account
            </AuthSubmitButton>
          </div>
        </form>
      </Form>
    </div>
  )
}
