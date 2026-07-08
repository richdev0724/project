import React, { useState } from 'react'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { CompanyProfileCacheManager } from '@/components/custom/company-profile-cache-manager'
import { MonthlyUpdateManager } from '@/components/custom/monthly-update-manager'
import { BulkUpdateManager } from '@/components/custom/bulk-update-manager'
import { InstantProfileDemo } from '@/components/custom/instant-profile-demo'
import { Settings, Database, Calendar, Zap, RefreshCw } from 'lucide-react'

export const DataManagementSettings: React.FC = () => {
  const [activeTab, setActiveTab] = useState('instant')

  return (
    <div className='container mx-auto max-w-6xl p-6'>
      <div className='mb-6'>
        <h1 className='flex items-center gap-2 text-3xl font-bold'>
          <Settings className='h-8 w-8' />
          Data Management Settings
        </h1>
        <p className='mt-2 text-muted-foreground'>
          Manage company profile caching and automated monthly updates
        </p>
      </div>

      <Tabs
        value={activeTab}
        onValueChange={setActiveTab}
        className='space-y-6'
      >
        <TabsList className='grid w-full grid-cols-4'>
          <TabsTrigger value='instant' className='flex items-center gap-2'>
            <Zap className='h-4 w-4' />
            Instant Loading
          </TabsTrigger>
          <TabsTrigger value='cache' className='flex items-center gap-2'>
            <Database className='h-4 w-4' />
            Cache Management
          </TabsTrigger>
          <TabsTrigger value='bulk' className='flex items-center gap-2'>
            <RefreshCw className='h-4 w-4' />
            Bulk Updates
          </TabsTrigger>
          <TabsTrigger value='updates' className='flex items-center gap-2'>
            <Calendar className='h-4 w-4' />
            Monthly Updates
          </TabsTrigger>
        </TabsList>

        <TabsContent value='instant' className='space-y-6'>
          <InstantProfileDemo />
        </TabsContent>

        <TabsContent value='cache' className='space-y-6'>
          <Card>
            <CardHeader>
              <CardTitle>Company Profile Cache</CardTitle>
              <CardDescription>
                Manage cached company profile data. Data is stored locally and
                persists across browser sessions.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <CompanyProfileCacheManager />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value='bulk' className='space-y-6'>
          <Card>
            <CardHeader>
              <CardTitle>Bulk Company Profile Updates</CardTitle>
              <CardDescription>
                Manually trigger bulk updates of company profiles for all US
                tickers.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <BulkUpdateManager />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value='updates' className='space-y-6'>
          <Card>
            <CardHeader>
              <CardTitle>Automated Monthly Updates</CardTitle>
              <CardDescription>
                Configure and monitor automatic company profile updates that run
                after market close on the last Friday of each month.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <MonthlyUpdateManager />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* System Status Overview */}
      <Card className='mt-6'>
        <CardHeader>
          <CardTitle>System Status</CardTitle>
          <CardDescription>
            Overview of the data management system
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className='grid grid-cols-1 gap-4 md:grid-cols-3'>
            <div className='rounded-lg bg-green-50 p-4 text-center dark:bg-green-950/20'>
              <div className='text-2xl font-bold text-green-600 dark:text-green-400'>
                ✅ Active
              </div>
              <div className='text-sm text-green-600 dark:text-green-400'>
                Cache System
              </div>
            </div>

            <div className='rounded-lg bg-blue-50 p-4 text-center dark:bg-blue-950/20'>
              <div className='text-2xl font-bold text-blue-600 dark:text-blue-400'>
                🔄 Scheduled
              </div>
              <div className='text-sm text-blue-600 dark:text-blue-400'>
                Monthly Updates
              </div>
            </div>

            <div className='rounded-lg bg-purple-50 p-4 text-center dark:bg-purple-950/20'>
              <div className='text-2xl font-bold text-purple-600 dark:text-purple-400'>
                🚀 Optimized
              </div>
              <div className='text-sm text-purple-600 dark:text-purple-400'>
                Performance
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
