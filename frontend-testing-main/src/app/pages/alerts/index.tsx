import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'

export default function Alerts() {
  return (
    <Layout>
      <Seo title='Alerts' />
      <Layout.Body>
        <div className='flex h-full items-center justify-center'>
          <div className='space-y-4 text-center'>
            <h2 className='text-xl font-semibold'>Alerts Page</h2>
            <p className='text-muted-foreground'>
              Your alerts system will be displayed here.
            </p>
          </div>
        </div>
      </Layout.Body>
    </Layout>
  )
}
