import moment from 'moment'

export const getFridaysOfThreeMonths = () => {
  const start = moment() // Start from the first day of the previous month
  const end = moment().add(4, 'month').endOf('month') // End on the last day of the next month
  const fridays = []

  if (start.isoWeekday() !== 5) {
    start.add(5 - start.isoWeekday() + (start.isoWeekday() > 5 ? 7 : 0), 'days')
  }

  while (start.isSameOrBefore(end)) {
    const weekOfMonth = Math.ceil(start.date() / 7)
    const isOpex = weekOfMonth === 3

    fridays.push({
      label: start.format('MM/DD/YYYY'),
      value: start.format('YYYY-MM-DD'),
      isOpex,
    })

    start.add(1, 'week')
  }
  return fridays
}
