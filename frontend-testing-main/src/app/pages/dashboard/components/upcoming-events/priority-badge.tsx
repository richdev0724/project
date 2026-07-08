import { Badge } from '@/components/ui/badge'

interface Props {
  priority: string
}

const PriorityBadge = ({ priority }: Props) => {
  let color = '#D9D9D9'
  let textColor = '#090909'
  switch (priority) {
    case 'HIGH':
      color = '#CD4B38'
      textColor = '#FFFFFF'
      break
    case 'MEDIUM':
      color = '#FF9500'
      textColor = '#FFFFFF'
      break
    default:
      color = '#D9D9D9'
      textColor = '#090909'
      break
  }

  return (
    <Badge
      className='h-[15px] w-[68px] items-center justify-center rounded-[1px] text-[10px] font-bold'
      style={{ backgroundColor: color, color: textColor }}
    >
      {priority}
    </Badge>
  )
}

export default PriorityBadge
