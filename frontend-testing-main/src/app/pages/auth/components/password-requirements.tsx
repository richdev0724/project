import { IconCheck, IconX } from '@tabler/icons-react'

const PasswordRequirements = ({ password = '', visible = true }) => {
  const requirements = [
    {
      label: 'Use at least 8 characters',
      test: (pass: string | any[]) => pass.length >= 8,
    },
    {
      label: 'Use upper and lower case characters',
      test: (pass: string) => /[a-z]/.test(pass) && /[A-Z]/.test(pass),
    },
    {
      label: 'Use at least one number',
      test: (pass: string) => /[0-9]/.test(pass),
    },
    {
      label: 'Use at least one special character',
      test: (pass: string) => /[!@#$%^&*(),.?":{}|<>]/.test(pass),
    },
  ]

  if (!visible) return null

  return (
    <div className='relative'>
      {/* Triangle pointer */}
      <div className='absolute -top-3 left-4 h-4 w-4'>
        <div className='absolute h-6 w-6 rotate-45 transform bg-gray-800'></div>
      </div>

      {/* Main popup container */}
      <div className='rounded-lg bg-gray-800 p-6 shadow-xl'>
        {/* Header section */}
        <div className='mb-4'>
          <h3 className='text-white'>We take passwords very seriously...</h3>
          <p className='text-sm text-gray-400'>
            like, really, really seriously.
          </p>
        </div>

        {/* Requirements list */}
        <ul className='space-y-3'>
          {requirements.map((req, index) => {
            const isMet = req.test(password)
            return (
              <li key={index} className='flex items-center space-x-2'>
                {isMet ? (
                  <IconCheck className='h-4 w-4 text-green-500' />
                ) : (
                  <IconX className='h-4 w-4 text-gray-400' />
                )}
                <span className='text-sm text-gray-300'>{req.label}</span>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}

export default PasswordRequirements
