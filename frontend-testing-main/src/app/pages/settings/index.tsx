import { useState } from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Separator } from '@/components/ui/separator'
import { Badge } from '@/components/ui/badge'
import {
  User,
  Shield,
  Bell,
  Palette,
  CreditCard,
  Mail,
  CheckCircle,
  AlertCircle,
  Eye,
  EyeOff,
  QrCode,
  MessageSquare,
} from 'lucide-react'

export default function Settings() {
  const [emailVerified] = useState(true)
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [darkMode, setDarkMode] = useState(true)
  const [emailAlertsEnabled, setEmailAlertsEnabled] = useState(true)
  const [textAlertsEnabled, setTextAlertsEnabled] = useState(false)
  const [phoneNumber, setPhoneNumber] = useState('')

  return (
    <div className='mx-auto max-w-4xl space-y-6 px-6 py-12'>
      {/* Header */}
      <div className='mb-8'>
        <h1 className='mb-3 text-2xl font-bold'>Settings & Profile</h1>
        <p className='text-sm text-muted-foreground'>
          Manage your account settings, security preferences, and subscription
          details.
        </p>
      </div>

      {/* Profile Information */}
      <Card>
        <CardHeader className='pb-4'>
          <CardTitle className='flex items-center gap-2 text-lg'>
            <User className='h-5 w-5' />
            Profile Information
          </CardTitle>
          <CardDescription className='text-sm'>
            Update your personal information and account details
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-4 pt-4'>
          <div className='grid grid-cols-1 gap-4 md:grid-cols-2'>
            <div className='space-y-2'>
              <Label htmlFor='legalName' className='text-sm font-medium'>
                Legal Name
              </Label>
              <Input
                id='legalName'
                defaultValue='Alex Morgan'
                className='h-10'
              />
            </div>
            <div className='space-y-2'>
              <Label htmlFor='email' className='text-sm font-medium'>
                Email Address
              </Label>
              <div className='flex gap-2'>
                <Input
                  id='email'
                  defaultValue='alex.morgan@example.com'
                  className='h-10 flex-1'
                />
                <div className='flex items-center gap-2'>
                  {emailVerified ? (
                    <CheckCircle className='h-5 w-5 text-green-500' />
                  ) : (
                    <AlertCircle className='h-5 w-5 text-orange-500' />
                  )}
                  {!emailVerified && (
                    <Button variant='outline' size='sm' className='h-10 px-3'>
                      Verify
                    </Button>
                  )}
                </div>
              </div>
              <p className='text-sm text-muted-foreground'>
                {emailVerified
                  ? 'Email verified'
                  : 'Please verify your email address'}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Account Security */}
      <Card>
        <CardHeader className='pb-4'>
          <CardTitle className='flex items-center gap-2 text-lg'>
            <Shield className='h-5 w-5' />
            Account Security
          </CardTitle>
          <CardDescription className='text-sm'>
            Manage your password and two-factor authentication settings
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-6 pt-4'>
          {/* Password Change */}
          <div className='space-y-4'>
            <h4 className='text-base font-semibold'>Change Password</h4>
            <div className='grid grid-cols-1 gap-4 md:grid-cols-3'>
              <div className='space-y-2'>
                <Label
                  htmlFor='currentPassword'
                  className='text-sm font-medium'
                >
                  Current Password
                </Label>
                <div className='relative'>
                  <Input
                    id='currentPassword'
                    type={showPassword ? 'text' : 'password'}
                    placeholder='Enter current password'
                    className='h-10'
                  />
                  <Button
                    type='button'
                    variant='ghost'
                    size='sm'
                    className='absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent'
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? (
                      <EyeOff className='h-4 w-4' />
                    ) : (
                      <Eye className='h-4 w-4' />
                    )}
                  </Button>
                </div>
              </div>
              <div className='space-y-2'>
                <Label htmlFor='newPassword' className='text-sm font-medium'>
                  New Password
                </Label>
                <Input
                  id='newPassword'
                  type={showPassword ? 'text' : 'password'}
                  placeholder='Enter new password'
                  className='h-10'
                />
              </div>
              <div className='space-y-2'>
                <Label
                  htmlFor='confirmPassword'
                  className='text-sm font-medium'
                >
                  Confirm Password
                </Label>
                <Input
                  id='confirmPassword'
                  type={showPassword ? 'text' : 'password'}
                  placeholder='Confirm new password'
                  className='h-10'
                />
              </div>
            </div>
            <Button className='h-10 w-fit px-4'>Update Password</Button>
          </div>

          <Separator />

          {/* Two-Factor Authentication */}
          <div className='space-y-4'>
            <div className='flex items-center justify-between'>
              <div>
                <h4 className='text-base font-semibold'>
                  Two-Factor Authentication
                </h4>
                <p className='text-sm text-muted-foreground'>
                  Add an extra layer of security to your account
                </p>
              </div>
              <Switch
                checked={twoFactorEnabled}
                onCheckedChange={setTwoFactorEnabled}
              />
            </div>
            {twoFactorEnabled && (
              <div className='space-y-3 rounded-lg bg-muted/50 p-4'>
                <div className='flex items-center gap-2'>
                  <QrCode className='h-4 w-4' />
                  <span className='text-sm font-medium'>Setup Required</span>
                </div>
                <p className='text-sm text-muted-foreground'>
                  Scan the QR code with your authenticator app to complete
                  setup.
                </p>
                <Button variant='outline' size='sm' className='h-9 px-3'>
                  Show QR Code
                </Button>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Notification Preferences */}
      <Card>
        <CardHeader className='pb-4'>
          <CardTitle className='flex items-center gap-2 text-lg'>
            <Bell className='h-5 w-5' />
            Notification Preferences
          </CardTitle>
          <CardDescription className='text-sm'>
            Notifications are set on the Signals page.
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-6 pt-4'>
          {/* Alert Delivery Method */}
          <div className='space-y-4'>
            <h4 className='text-base font-semibold'>Alert Delivery Method</h4>

            {/* Email Alerts */}
            <div className='space-y-3'>
              <div className='flex items-center justify-between'>
                <div className='flex items-center gap-3'>
                  <Mail className='h-5 w-5' />
                  <div>
                    <p className='text-sm font-medium'>Email Alerts</p>
                    <p className='text-sm text-muted-foreground'>
                      Receive alerts via email
                    </p>
                  </div>
                </div>
                <Switch
                  checked={emailAlertsEnabled}
                  onCheckedChange={setEmailAlertsEnabled}
                />
              </div>

              {emailAlertsEnabled && (
                <div className='ml-8 rounded-lg bg-muted/50 p-3'>
                  <p className='text-sm text-muted-foreground'>
                    Alerts will be sent to:{' '}
                    <span className='font-medium text-foreground'>
                      alex.morgan@example.com
                    </span>
                  </p>
                </div>
              )}
            </div>

            <Separator />

            {/* Text Alerts */}
            <div className='space-y-3'>
              <div className='flex items-center justify-between'>
                <div className='flex items-center gap-3'>
                  <MessageSquare className='h-5 w-5' />
                  <div>
                    <p className='text-sm font-medium'>Text Alerts</p>
                    <p className='text-sm text-muted-foreground'>
                      Receive alerts via SMS
                    </p>
                  </div>
                </div>
                <Switch
                  checked={textAlertsEnabled}
                  onCheckedChange={setTextAlertsEnabled}
                />
              </div>

              {textAlertsEnabled && (
                <div className='ml-8 space-y-3'>
                  <div className='space-y-2'>
                    <Label
                      htmlFor='phoneNumber'
                      className='text-sm font-medium'
                    >
                      Phone Number
                    </Label>
                    <Input
                      id='phoneNumber'
                      type='tel'
                      placeholder='+1 (555) 123-4567'
                      value={phoneNumber}
                      onChange={(e) => setPhoneNumber(e.target.value)}
                      className='h-10'
                    />
                    <p className='text-sm text-muted-foreground'>
                      Enter your phone number to receive SMS alerts
                    </p>
                  </div>
                  {phoneNumber && (
                    <div className='rounded-lg bg-muted/50 p-3'>
                      <p className='text-sm text-muted-foreground'>
                        SMS alerts will be sent to:{' '}
                        <span className='font-medium text-foreground'>
                          {phoneNumber}
                        </span>
                      </p>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Data & Display Preferences */}
      <Card>
        <CardHeader className='pb-4'>
          <CardTitle className='flex items-center gap-2 text-lg'>
            <Palette className='h-5 w-5' />
            Data & Display Preferences
          </CardTitle>
          <CardDescription className='text-sm'>
            Customize the appearance and display of your dashboard
          </CardDescription>
        </CardHeader>
        <CardContent className='pt-4'>
          <div className='flex items-center justify-between'>
            <div>
              <h4 className='text-base font-semibold'>Theme</h4>
              <p className='text-sm text-muted-foreground'>
                Switch between light and dark mode
              </p>
            </div>
            <div className='flex items-center gap-3'>
              <span className='text-sm'>Light</span>
              <Switch checked={darkMode} onCheckedChange={setDarkMode} />
              <span className='text-sm'>Dark</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Subscription & Billing */}
      <Card>
        <CardHeader className='pb-4'>
          <CardTitle className='flex items-center gap-2 text-lg'>
            <CreditCard className='h-5 w-5' />
            Subscription & Billing
          </CardTitle>
          <CardDescription className='text-sm'>
            Manage your subscription plan and payment methods
          </CardDescription>
        </CardHeader>
        <CardContent className='space-y-6 pt-4'>
          {/* Plan Details */}
          <div className='space-y-4'>
            <h4 className='text-base font-semibold'>Plan Details</h4>
            <div className='space-y-4 rounded-lg border p-4'>
              <div className='flex items-center justify-between'>
                <div>
                  <div className='mb-2 flex items-center gap-2'>
                    <h5 className='text-base font-semibold'>
                      MarketMono Monthly Subscription
                    </h5>
                    <Badge
                      variant='default'
                      className='border-green-500/20 bg-green-500/10 px-2 py-1 text-xs text-green-500'
                    >
                      Active
                    </Badge>
                  </div>
                  <p className='text-sm text-muted-foreground'>
                    Advanced trading tools, real-time data, and premium
                    analytics
                  </p>
                </div>
                <div className='text-right'>
                  <p className='text-lg font-semibold'>$40/month</p>
                </div>
              </div>

              <div className='flex gap-3 pt-2'>
                <Button
                  variant='outline'
                  size='sm'
                  className='h-9 border-orange-200 px-3 text-orange-600 hover:bg-orange-50 hover:text-orange-700 dark:border-orange-800 dark:text-orange-400 dark:hover:bg-orange-950'
                >
                  Pause Subscription
                </Button>
                <Button
                  variant='outline'
                  size='sm'
                  className='h-9 border-red-200 px-3 text-red-600 hover:bg-red-50 hover:text-red-700 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950'
                >
                  Cancel Subscription
                </Button>
              </div>

              <div className='space-y-1 border-t pt-3 text-sm text-muted-foreground'>
                <p>
                  <strong>Pause:</strong> Temporarily stops access while keeping
                  your payment method on file
                </p>
                <p>
                  <strong>Cancel:</strong> Ends subscription on renewal date and
                  removes payment method
                </p>
              </div>
            </div>
          </div>

          <Separator />

          {/* Payment Method */}
          <div className='space-y-4'>
            <h4 className='text-base font-semibold'>Payment Method</h4>
            <div className='flex items-center justify-between rounded-lg border p-4'>
              <div className='flex items-center gap-3'>
                <div className='flex h-5 w-8 items-center justify-center rounded bg-blue-500'>
                  <span className='text-xs font-bold text-white'>****</span>
                </div>
                <div>
                  <p className='text-sm font-medium'>**** **** **** 4242</p>
                  <p className='text-sm text-muted-foreground'>Expires 12/25</p>
                </div>
              </div>
              <Button variant='outline' size='sm' className='h-9 px-3'>
                Update
              </Button>
            </div>
          </div>

          <Separator />

          {/* Billing Cycle */}
          <div className='space-y-4'>
            <h4 className='text-base font-semibold'>Billing Cycle</h4>
            <div className='grid grid-cols-1 gap-4 md:grid-cols-2'>
              <div className='rounded-lg border p-4'>
                <p className='mb-1 text-sm text-muted-foreground'>
                  Next Renewal Date
                </p>
                <p className='text-base font-medium'>March 15, 2024</p>
              </div>
              <div className='rounded-lg border p-4'>
                <p className='mb-1 text-sm text-muted-foreground'>
                  Billing Frequency
                </p>
                <p className='text-base font-medium'>Monthly</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Save Changes */}
      <div className='flex justify-end gap-3 pt-6'>
        <Button variant='outline' size='sm' className='h-10 px-4'>
          Cancel
        </Button>
        <Button size='sm' className='h-10 px-4'>
          Save Changes
        </Button>
      </div>
    </div>
  )
}
