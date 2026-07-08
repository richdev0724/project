import { UncheckedCheckbox } from '@/assets/icons/unchecked-checkbox'
import { AuthSubmitButton } from '@/components/custom/auth-submit-button'
import { Button } from '@/components/custom/button'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { SignupRequest } from '@/validations/auth.validation'
import { HTMLAttributes, useCallback, useEffect } from 'react'

interface UserAgreementFormProps
  extends Omit<HTMLAttributes<HTMLDivElement>, 'onSubmit'> {
  form: any
  onSubmit: (data: SignupRequest) => void
  isLoading: boolean
  isFormValid: boolean
}

export function UserAgreementForm({
  form,
  onSubmit,
  isLoading,
  isFormValid,
  ...props
}: UserAgreementFormProps) {
  const agreementFields = new Set([
    {
      label: 'NASDAQ Agreement',
      value: 'nasdaqAgreement',
    },
    {
      label: 'OPRA Agreement',
      value: 'opraAgreement',
    },
    {
      label: 'Privacy Policy',
      value: 'privacyPolicy',
    },
    {
      label: 'User Agreement',
      value: 'userAgreement',
    },
  ])

  const handleAgreementClick = useCallback(
    (fieldName: string) => {
      form.setValue(fieldName, !form.getValues(fieldName), {
        shouldValidate: true,
        shouldDirty: true,
        shouldTouch: true,
      })
    },
    [form]
  )

  useEffect(() => {
    const subscription = form.watch((name: string) => {
      if (Array.from(agreementFields).some((field) => field.value === name)) {
        form.trigger()
      }
    })

    return () => subscription.unsubscribe()
  }, [form])

  const getButtonClass = (isActive: boolean) =>
    isActive &&
    'from-gradientButtonSecondary to-gradientButtonSecondaryTransparent bg-gradient-to-r text-white'

  const getButtonText = (isActive: boolean) =>
    isActive ? 'Completed' : 'Click Here'

  return (
    <div className='grid gap-6' {...props}>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className='grid gap-6'>
            {Array.from(agreementFields).map((fieldItem, index) => (
              <FormField
                key={index}
                control={form.control}
                name={fieldItem.value}
                render={({ field }) => (
                  <FormItem className='space-y-1'>
                    <FormLabel className='flex items-center text-xl font-medium'>
                      {fieldItem.label}
                    </FormLabel>
                    <div className='flex items-center gap-11'>
                      <FormControl>
                        <Button
                          type='button'
                          variant='gradient'
                          size='fullLg'
                          {...field}
                          className={`h-[49px] w-[367px] text-base shadow-xl shadow-black/25 ${getButtonClass(
                            field.value
                          )}`}
                          onClick={() => handleAgreementClick(fieldItem.value)}
                        >
                          {getButtonText(field.value)}
                        </Button>
                      </FormControl>
                      <UncheckedCheckbox isActive={field.value} />
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ))}
          </div>
          <div className='mt-16'>
            {!isFormValid ? (
              <div className='flex items-center justify-center rounded-sm p-24 text-[21px] font-medium text-[#F5F7F7]'>
                Please complete all forms
              </div>
            ) : (
              <>
                <div className='flex items-center justify-center rounded-sm pt-6 text-[21px] font-medium text-[#F5F7F7]'>
                  It's go time!
                </div>
                <AuthSubmitButton
                  className='mt-[52px] w-full rounded-sm shadow-xl shadow-black/25 transition-colors duration-300'
                  variant='gradient'
                  size='fullLg'
                  loading={isLoading}
                  disabled={!isFormValid || isLoading}
                >
                  Finish
                </AuthSubmitButton>
              </>
            )}
          </div>
        </form>
      </Form>
    </div>
  )
}
