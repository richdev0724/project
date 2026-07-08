import React, { useState } from 'react'
import { Layout, Responsive, WidthProvider } from 'react-grid-layout'
import {
  BarChart,
  LineChart,
  PieChart,
  AreaChart,
  Bar,
  CartesianGrid,
  XAxis,
  YAxis,
  Legend,
  Rectangle,
  ResponsiveContainer,
  Line,
  Pie,
  Area,
} from 'recharts'
import {
  BarChart2,
  LineChart as LineIcon,
  PieChart as PieIcon,
  Activity,
  Square,
  Trash2,
  Move,
  Settings,
} from 'lucide-react'
import 'react-grid-layout/css/styles.css'
import 'react-resizable/css/styles.css'

// Create a responsive grid layout
const ResponsiveGridLayout = WidthProvider(Responsive)

// Define widget types
type WidgetType = 'bar' | 'line' | 'pie' | 'area' | 'text' | 'button'

// Define the dashboard item interface
interface DashboardItem extends Layout {
  type: WidgetType
}

// Define data point interface for charts
interface DataPoint {
  name: string
  value: number
}

const Signals = React.memo(() => {
  // State for storing dashboard items
  const [items, setItems] = useState<DashboardItem[]>([])
  // Counter for generating unique keys
  const [counter, setCounter] = useState<number>(0)

  const barData = [
    {
      name: 'Page A',
      uv: 4000,
      pv: 2400,
      amt: 2400,
    },
    {
      name: 'Page B',
      uv: 3000,
      pv: 1398,
      amt: 2210,
    },
    {
      name: 'Page C',
      uv: 2000,
      pv: 9800,
      amt: 2290,
    },
    {
      name: 'Page D',
      uv: 2780,
      pv: 3908,
      amt: 2000,
    },
    {
      name: 'Page E',
      uv: 1890,
      pv: 4800,
      amt: 2181,
    },
    {
      name: 'Page F',
      uv: 2390,
      pv: 3800,
      amt: 2500,
    },
    {
      name: 'Page G',
      uv: 3490,
      pv: 4300,
      amt: 2100,
    },
  ]
  // Sample data for charts
  const sampleData: DataPoint[] = [
    { name: 'Jan', value: 400 },
    { name: 'Feb', value: 300 },
    { name: 'Mar', value: 600 },
    { name: 'Apr', value: 800 },
    { name: 'May', value: 500 },
    { name: 'Jun', value: 900 },
  ]

  // Function to add a new item to the dashboard
  const addItem = (type: WidgetType): void => {
    const newItem: DashboardItem = {
      i: `item-${counter}`,
      x: (items.length * 4) % 12,
      y: Math.floor(items.length / 3) * 4,
      w: 4,
      h: 3,
      type: type,
    }

    setItems([...items, newItem])
    setCounter(counter + 1)
  }

  // Function to remove an item from the dashboard
  const removeItem = (id: string): void => {
    setItems(items.filter((item) => item.i !== id))
  }

  const renderLayoutInfo = () => {
    return (
      <div className='mb-4 rounded-lg p-3 shadow-md'>
        <div className='mt-2'>
          <div className='mb-1 text-sm text-white'>
            Displayed as <code>[x, y, w, h]</code>:
          </div>
          <div className='flex flex-wrap gap-2 text-white'>
            {items.map((l) => (
              <div
                key={l.i}
                className='rounded border border-gray-800 p-2 text-sm'
              >
                <strong>{l.i}</strong>: [{l.x}, {l.y}, {l.w}, {l.h}]
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  // Function to render different widget types
  const renderWidget = (item: DashboardItem): React.ReactNode => {
    switch (item.type) {
      case 'bar':
        return (
          <div className='h-[calc(100%_-_20px)] w-full'>
            <ResponsiveContainer width='100%' height='100%'>
              <BarChart data={barData}>
                <CartesianGrid strokeDasharray='3 3' />
                <XAxis dataKey='name' />
                <YAxis />
                <Legend />
                <Bar
                  dataKey='pv'
                  fill='#8884d8'
                  activeBar={<Rectangle fill='pink' stroke='blue' />}
                />
                <Bar
                  dataKey='uv'
                  fill='#82ca9d'
                  activeBar={<Rectangle fill='gold' stroke='purple' />}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )
      case 'line':
        return (
          <div className='h-[calc(100%_-_20px)] w-full'>
            <ResponsiveContainer width='100%' height='100%'>
              <LineChart
                width={500}
                height={300}
                data={barData}
                margin={{
                  top: 5,
                  right: 30,
                  left: 20,
                  bottom: 5,
                }}
              >
                <CartesianGrid strokeDasharray='3 3' />
                <XAxis dataKey='name' />
                <YAxis />
                <Legend />
                <Line
                  type='monotone'
                  dataKey='pv'
                  stroke='#8884d8'
                  activeDot={{ r: 8 }}
                />
                <Line type='monotone' dataKey='uv' stroke='#82ca9d' />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )
      case 'pie':
        return (
          <div className='h-[calc(100%_-_20px)] w-full'>
            <ResponsiveContainer width='100%' height='100%'>
              <PieChart width={400} height={400}>
                <Pie
                  dataKey='value'
                  startAngle={360}
                  endAngle={0}
                  data={sampleData}
                  cx='50%'
                  cy='50%'
                  outerRadius={80}
                  fill='#8884d8'
                  label
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        )
      case 'area':
        return (
          <div className='h-[calc(100%_-_20px)] w-full'>
            <ResponsiveContainer width='100%' height='100%'>
              <AreaChart
                width={500}
                height={400}
                data={barData}
                margin={{
                  top: 10,
                  right: 30,
                  left: 0,
                  bottom: 0,
                }}
              >
                <CartesianGrid strokeDasharray='3 3' />
                <XAxis dataKey='name' />
                <YAxis />
                <Area
                  type='monotone'
                  dataKey='uv'
                  stroke='#8884d8'
                  fill='#8884d8'
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )
      case 'text':
        return (
          <div>
            <h3 className='mb-2 text-lg font-medium'>Text Widget</h3>
            <p className='text-black'>
              This is a sample text widget. Double-click to edit.
            </p>
          </div>
        )
      case 'button':
        return (
          <div className='flex h-full items-center justify-center'>
            <button
              className='rounded bg-blue-500 px-4 py-2 text-white transition-colors hover:bg-blue-600'
              onClick={() => alert('Button clicked!')}
            >
              Action Button
            </button>
          </div>
        )
      default:
        return <div>Unknown widget type</div>
    }
  }

  // Create a custom delete button component to handle the deletion
  const DeleteButton = ({ id }: { id: string }) => {
    // Handle click with special handling to prevent drag overlay issues
    const handleDeleteClick = (e: React.MouseEvent) => {
      e.stopPropagation()
      e.preventDefault()

      // Add a slight delay to ensure event doesn't conflict with dragging
      setTimeout(() => {
        removeItem(id)
      }, 10)
    }

    return (
      <div
        className='delete-button rounded p-1 text-red-500 hover:bg-red-50'
        style={{ cursor: 'pointer', position: 'relative', zIndex: 999 }}
        onClick={handleDeleteClick}
        onMouseDown={(e) => {
          // Prevent the mousedown from triggering drag
          e.stopPropagation()
        }}
      >
        <Trash2 size={16} />
      </div>
    )
  }

  return (
    <div className='h-screen overflow-y-auto  p-4'>
      <div className='mb-4 text-white'>
        <div className='mb-4 flex space-x-4'>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('bar')}
          >
            <BarChart2 size={18} className='mr-2' />
            <span>Bar Chart</span>
          </div>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('line')}
          >
            <LineIcon size={18} className='mr-2' />
            <span>Line Chart</span>
          </div>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('pie')}
          >
            <PieIcon size={18} className='mr-2' />
            <span>Pie Chart</span>
          </div>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('area')}
          >
            <Activity size={18} className='mr-2' />
            <span>Area Chart</span>
          </div>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('text')}
          >
            <Square size={18} className='mr-2' />
            <span>Text</span>
          </div>
          <div
            className='flex cursor-pointer items-center justify-center rounded border p-2'
            onClick={() => addItem('button')}
          >
            <Settings size={18} className='mr-2' />
            <span>Button</span>
          </div>
        </div>
      </div>

      {items.length > 0 && renderLayoutInfo()}

      <ResponsiveGridLayout
        className='layout'
        layouts={{ lg: items }}
        breakpoints={{ lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 }}
        cols={{ lg: 12, md: 10, sm: 6, xs: 4, xxs: 2 }}
        rowHeight={100}
        isDraggable={true}
        isResizable={true}
        onLayoutChange={(layout: Layout[]) => {
          // Update the layout when items are moved or resized
          setItems(
            items.map((item) => {
              const updatedPos = layout.find((l) => l.i === item.i)
              return updatedPos ? { ...item, ...updatedPos } : item
            })
          )
        }}
      >
        {items.map((item) => (
          <div key={item.i} className='border border-gray-800 p-4 shadow-md'>
            <div className='mb-2 flex justify-between'>
              <div className='flex items-center'>
                <Move size={16} className='mr-2 cursor-move text-gray-400' />
                <span className='text-sm text-gray-500'>Drag to move</span>
              </div>
              <DeleteButton id={item.i} />
            </div>
            <div className='h-full overflow-hidden '>{renderWidget(item)}</div>
          </div>
        ))}
      </ResponsiveGridLayout>
    </div>
  )
})
export default Signals
